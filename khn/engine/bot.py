"""KHN : orchestration du marché, du risque, des positions, de l'apprentissage et de l'équipe."""
from __future__ import annotations

import asyncio
import copy
import os
import re
import time
from collections import deque
from datetime import datetime, timedelta, timezone

from . import learning
from .backtest import adapt, eval_spec, robust
from .broker import PaperBroker, TestnetBroker
from .config import Config
from .exchange import DATA_API, TESTNET_API, BinanceSpot, ExchangeError
from .llm import Brain, BrainError
from .market import BinanceFeed, SimFeed
from .store import Store
from .strategy import C, Market, builtins, exit_signal, features, levels, new_strategy, signal_at, spec_from_raw
from .team import ROLES, Team
from .youtube import transcript

TAHITI = timezone(timedelta(hours=-10))
RS_FR = {"tp": "objectif atteint", "stop": "stop touché", "signal": "signal de sortie", "temps": "durée max",
         "manuel": "clôture manuelle", "urgence": "arrêt d'urgence"}
MANUAL = {"id": "manual", "name": "Ordre manuel", "family": "trend", "stop": {"type": "atr", "mult": 1.5}, "tp": {"rr": 2.0}, "filters": {}}


def fr2(x: float) -> str:
    return f"{x:,.2f}".replace(",", " ").replace(".", ",")


def fR(r: float) -> str:
    return ("+" if r > 0 else "−" if r < 0 else "") + f"{abs(r):.2f}".replace(".", ",") + " R"


def make_feed(cfg: Config):
    if cfg.mode == "sim":
        return SimFeed(cfg.symbols, cfg.history, cfg.sim_candle_sec)
    base = TESTNET_API if cfg.mode == "testnet" else DATA_API
    return BinanceFeed(BinanceSpot(base, cfg.testnet_key, cfg.testnet_secret), cfg.symbols, cfg.history)


class Bot:
    def __init__(self, cfg: Config, feed=None, broker=None, brain: Brain | None = None, store: Store | None = None):
        self.cfg = cfg
        self.store = store or Store(os.path.join(cfg.data_dir, f"khn-{cfg.mode}.sqlite"))
        self.feed = feed or make_feed(cfg)
        if broker is not None:
            self.broker = broker
        elif cfg.mode == "testnet":
            self.broker = TestnetBroker(self.feed.client)
        else:
            self.broker = PaperBroker()
        self.brain = brain or Brain(cfg.model)
        self.listeners: set[asyncio.Queue] = set()
        self.logs: deque = deque(maxlen=200)
        self._log_id = 0
        self.team = Team(self._handle, self.emit, self.log, speed=cfg.team_speed)
        self.risk = {"per_trade": 1.0, "max_daily": 3.0, "max_dd": 10.0, "max_pos": 2, "shorts": self.broker.can_short, "lev": 1}
        self.acct = {"start": 0.0, "balance": 0.0, "peak": 0.0, "day_start": 0.0, "day": ""}
        self.positions: list[dict] = []
        self.journal: list[dict] = []
        self.lessons: list[dict] = []
        self.strategies: list[dict] = []
        self.eq_hist: list[float] = []
        self.flags = {"paused": False, "killed": False, "dd_halt": False, "day_halt": ""}
        self.risk_mult = 1.0
        self.snum = self.seq = 0
        self.status = "démarrage"
        self.ready = False
        self.dirty = False
        self.candles_seen = 0
        self._bg: set[asyncio.Task] = set()
        self._last_err = 0.0

    # --- utilitaires ----------------------------------------------------------
    @property
    def markets(self) -> dict[str, Market]:
        return self.feed.markets

    def mlist(self) -> list[Market]:
        return list(self.markets.values())

    def by_key(self, k: str) -> Market | None:
        k = (k or "").upper().replace("USDT", "")
        return next((m for m in self.mlist() if m.key == k), None)

    def strat(self, sid: str) -> dict | None:
        return next((s for s in self.strategies if s["id"] == sid), None)

    def emit(self, ev: dict) -> None:
        for q in list(self.listeners):
            try:
                q.put_nowait(ev)
            except asyncio.QueueFull:
                pass

    def log(self, who: str, typ: str, text: str) -> None:
        self._log_id += 1
        e = {"id": self._log_id, "ts": int(time.time() * 1000), "who": who, "type": typ, "text": text}
        self.logs.append(e)
        self.emit({"type": "log", "entry": e})

    def spawn(self, coro) -> None:
        t = asyncio.create_task(coro)
        self._bg.add(t)
        t.add_done_callback(self._bg.discard)

    def rate(self) -> float:
        return self.feed.xpf_per_usdt

    def xpf(self, usdt: float) -> str:
        return f"{round(usdt * self.rate()):,}".replace(",", " ") + " XPF"

    def sxpf(self, usdt: float) -> str:
        v = round(usdt * self.rate())
        return ("+" if v > 0 else "−" if v < 0 else "") + f"{abs(v):,}".replace(",", " ") + " XPF"

    def unreal(self, p: dict) -> float:
        px = self.markets[p["sym"]].price
        return (px - p["entry"] if p["side"] == "long" else p["entry"] - px) * p["qty"]

    def equity(self) -> float:
        return self.acct["balance"] + sum(self.unreal(p) for p in self.positions)

    def dd(self) -> float:
        pk = self.acct["peak"]
        return max(0.0, (pk - self.equity()) / pk) if pk > 0 else 0.0

    @staticmethod
    def today() -> str:
        return datetime.now(TAHITI).date().isoformat()

    def min_notional(self, sym: str) -> float:
        f = self.feed.filters.get(sym)
        return f.min_notional if f else 5.0

    # --- mémoire --------------------------------------------------------------
    def load(self) -> None:
        g = self.store.get
        self.risk.update(g("risk", {}))
        if not self.broker.can_short:
            self.risk["shorts"], self.risk["lev"] = False, 1
        self.acct.update(g("acct", {}))
        self.strategies = g("strategies", []) or []
        self.journal = g("journal", []) or []
        self.lessons = g("lessons", []) or []
        self.eq_hist = g("eq", []) or []
        self.snum, self.seq = g("snum", 0), g("seq", 0)
        self.flags["killed"] = bool(g("killed", False))
        pos = g("positions", []) or []
        if self.cfg.mode == "sim" and pos:
            self.acct["balance"] += sum(p.get("fee_in", 0) for p in pos)
            self.log("KHN", "info", f"{len(pos)} position(s) simulée(s) de la session précédente annulée(s) : le marché synthétique a changé.")
        elif pos:
            self.positions = [p for p in pos if p.get("sym") in self.markets]
        for name in g("team", []) or []:
            if name != "KHN" and name in ROLES:
                self.team.recruit(name, "retour de la session précédente")

    def save(self) -> None:
        self.store.put_many({"risk": self.risk, "acct": self.acct, "strategies": self.strategies, "journal": self.journal[:300],
                             "lessons": self.lessons[:60], "eq": self.eq_hist[-1500:], "snum": self.snum, "seq": self.seq,
                             "positions": self.positions, "killed": self.flags["killed"], "team": [a.name for a in self.team.agents]})
        self.dirty = False

    # --- démarrage ------------------------------------------------------------
    async def start(self) -> None:
        delay = 5
        while True:
            try:
                self.status = "connexion au marché"
                await self.feed.bootstrap()
                if self.cfg.mode == "testnet":
                    await self.feed.client.sync_time()
                    await self.feed.client.account()  # vérifie les clés
                break
            except ExchangeError as e:
                self.status = "marché injoignable"
                self.log("KHN", "error", f"Connexion impossible : {e}. Nouvel essai dans {delay} s.")
                await asyncio.sleep(delay)
                delay = min(300, delay * 2)
        self.load()
        if not self.acct["start"]:
            start = self.cfg.capital_xpf / self.rate()
            self.acct.update(start=start, balance=start, peak=start, day_start=start, day=self.today())
            self.log("KHN", "info", f"Nouveau compte démo : {self.xpf(start)} ({fr2(start)} USDT).")
        if not self.strategies:
            for sp in builtins():
                self.snum += 1
                self.strategies.append(new_strategy(sp, self.snum))
        for s in self.strategies:
            if s.get("long") or s.get("short"):
                s["bt"] = eval_spec(s, self.mlist(), self.risk["shorts"])
        act = [s for s in self.strategies if s["status"] == "active"]
        n = min(len(m.candles) for m in self.mlist())
        self.log("QUANT", "info", f"Stratégies testées sur {n} bougies de 5 min par marché : "
                 + ", ".join(f"« {s['name']} » {fR(s['bt']['oos']['expR'])}/trade hors échantillon" for s in act if s.get("bt")) + ".")
        mode_txt = {"sim": "marché synthétique", "paper": "prix réels Binance, ordres simulés", "testnet": "testnet Binance, ordres réels en argent fictif"}[self.cfg.mode]
        self.log("KHN", "info", f"Surveillance lancée ({mode_txt}) sur {', '.join(m.key for m in self.mlist())}. "
                 f"Capital {self.xpf(self.equity())}, risque {fr2(self.risk['per_trade'])} % par trade."
                 + ("" if self.brain.available else " Pas de clé Claude : analyse des vidéos par mots-clés seulement."))
        if not self.eq_hist:
            self.eq_hist.append(self.equity())
        self.status = "en service"
        self.ready = True
        self.save()

    async def run(self) -> None:
        await self.start()
        team_task = asyncio.create_task(self.team.run())
        last_save = time.monotonic()
        poll = self.cfg.poll_sec if self.cfg.mode != "sim" else min(0.5, self.cfg.sim_candle_sec / 2)
        try:
            while True:
                try:
                    new = await self.feed.poll()
                    if self.status != "en service":
                        self.status = "en service"
                        self.log("KHN", "info", "Connexion au marché rétablie.")
                except ExchangeError as e:
                    if time.monotonic() - self._last_err > 300:
                        self._last_err = time.monotonic()
                        self.log("KHN", "error", f"Marché injoignable : {e}. Positions surveillées dès le retour de la connexion.")
                    self.status = "marché injoignable"
                    await asyncio.sleep(poll * 3)
                    continue
                await self.tick(new)
                if self.dirty and time.monotonic() - last_save > 20:
                    self.save()
                    last_save = time.monotonic()
                await asyncio.sleep(poll)
        finally:
            team_task.cancel()
            self.save()

    # --- boucle de marché -----------------------------------------------------
    async def tick(self, new: dict) -> None:
        for sym, candles in new.items():
            for c in candles:
                await self.on_candle(sym, c)
        await self.check_prices()
        self.risk_monitor()

    async def on_candle(self, sym: str, c) -> None:
        m = self.markets[sym]
        i = len(m.candles) - 1
        for p in [p for p in self.positions if p["sym"] == sym]:
            p["bars"] = p.get("bars", 0) + 1
            hit = None
            if c.t >= p["t0"]:
                if p["side"] == "long":
                    hit = ("stop", min(p["stop"], c.o)) if c.l <= p["stop"] else ("tp", p["tp"]) if c.h >= p["tp"] else None
                else:
                    hit = ("stop", max(p["stop"], c.o)) if c.h >= p["stop"] else ("tp", p["tp"]) if c.l <= p["tp"] else None
            sp = self.strat(p["sid"])
            if hit:
                await self.close_position(p, hit[1], hit[0])
            elif sp and exit_signal(sp, m, i, p["side"]):
                await self.close_position(p, m.price, "signal")
            elif p["bars"] > 150:
                await self.close_position(p, m.price, "temps")
        if not self.flags["killed"]:
            ttl = max(60.0, self.cfg.poll_sec * 3) if self.cfg.mode != "sim" else self.cfg.sim_candle_sec * 1.2
            self.team.push("scan", {"k": m.key, "sym": sym, "t": c.t}, ttl=ttl)
        if sym == self.cfg.symbols[0]:
            self.candles_seen += 1
            self.eq_hist.append(self.equity())
            del self.eq_hist[:-1500]
            if self.team.get("RISK") and self.candles_seen % 12 == 0 and not self.team.pending("audit"):
                self.team.push("audit", {})
            if self.candles_seen % 240 == 0:
                for s in self.strategies:
                    if s["status"] == "suspendue":
                        self.team.push("bt", {"sid": s["id"], "mode": "retest"})
            day = self.today()
            if day != self.acct["day"]:
                self.acct.update(day=day, day_start=self.equity())
                self.log("KHN", "info", "Nouvelle journée (heure de Tahiti) : compteur du jour remis à zéro.")
            self.dirty = True

    async def check_prices(self) -> None:
        for p in list(self.positions):
            px = self.markets[p["sym"]].price
            fav = (px - p["entry"]) if p["side"] == "long" else (p["entry"] - px)
            p["mfeR"] = max(p.get("mfeR", 0.0), fav / p["dist"])
            if p["side"] == "long":
                if px <= p["stop"]:
                    await self.close_position(p, px, "stop")
                elif px >= p["tp"]:
                    await self.close_position(p, px, "tp")
            else:
                if px >= p["stop"]:
                    await self.close_position(p, px, "stop")
                elif px <= p["tp"]:
                    await self.close_position(p, px, "tp")

    def risk_monitor(self) -> None:
        if not self.ready:
            return
        e = self.equity()
        if e > self.acct["peak"]:
            self.acct["peak"] = e
            if self.risk_mult < 1:
                self.risk_mult = 1.0
                self.log("RISK", "risk", "Nouveau sommet du capital : retour au risque normal.")
        d = self.dd()
        if not self.flags["dd_halt"] and d >= self.risk["max_dd"] / 100:
            self.flags["dd_halt"] = True
            self.log("RISK", "risk", f"Baisse de {d * 100:.1f} % : limite atteinte, plus aucune nouvelle position. Tape « reprendre » pour relancer.".replace(".", ",", 1))
            self.team.recruit("RISK", "la baisse maximale est atteinte")
        ds = self.acct["day_start"] or e
        if self.flags["day_halt"] != self.acct["day"] and (e - ds) / ds <= -self.risk["max_daily"] / 100:
            self.flags["day_halt"] = self.acct["day"]
            self.log("RISK", "risk", f"Perte du jour {(e - ds) / ds * 100:.1f} % : plus de nouvelle position avant demain (heure de Tahiti).".replace(".", ",", 1))
        if d >= self.risk["max_dd"] / 200:
            if not self.team.get("RISK"):
                self.team.recruit("RISK", "la baisse dépasse la moitié de la limite")
            elif self.risk_mult == 1.0:
                self.risk_mult = 0.5
                self.log("RISK", "risk", "Baisse supérieure à la moitié de la limite : mode prudent, risque par trade divisé par 2.")

    # --- positions ------------------------------------------------------------
    def gate(self, sym: str) -> str | None:
        f = self.flags
        if f["killed"]:
            return "arrêt d'urgence actif"
        if f["paused"]:
            return "trading en pause"
        if f["dd_halt"]:
            return "baisse maximale atteinte"
        if f["day_halt"] == self.acct["day"]:
            return "perte max du jour atteinte"
        if len(self.positions) >= self.risk["max_pos"]:
            return "nombre max de positions atteint"
        if any(p["sym"] == sym for p in self.positions):
            return "position déjà ouverte sur ce marché"
        if self.equity() < self.min_notional(sym):
            return "capital inférieur à l'ordre minimum de l'exchange"
        return None

    def size(self, sym: str, price: float, dist: float) -> tuple[float | None, str]:
        eq = self.equity()
        risk_amt = eq * self.risk["per_trade"] / 100 * self.risk_mult
        stop_pct = dist / price
        notional = risk_amt / stop_pct
        lev = self.risk["lev"] if (self.risk["shorts"] and self.broker.can_short) else 1
        max_n = eq * lev * 0.98
        note = ""
        if notional > max_n:
            notional, note = max_n, " (taille plafonnée par le capital)"
        mn = self.min_notional(sym) * 1.02
        if notional < mn:
            if mn * stop_pct <= risk_amt * 1.5 and mn <= max_n:
                notional, note = mn, " (taille portée au minimum de l'exchange)"
            else:
                return None, f"la taille calculée ({fr2(notional)} USDT) est sous l'ordre minimum de {fr2(self.min_notional(sym))} USDT et le risque dépasserait la limite"
        return notional, note

    async def open_position(self, sp: dict, sym: str, side: str, who: str, manual: bool = False) -> dict | None:
        why = self.gate(sym)
        if not why and side == "short" and not (self.risk["shorts"] and self.broker.can_short):
            why = "vente à découvert impossible dans ce mode"
        if why:
            if manual:
                self.log("RISK", "risk", f"Ordre refusé : {why}.")
            return None
        m = self.markets[sym]
        i = len(m.candles) - 1
        dist, _, _ = levels(sp, m, i, side, m.price)
        notional, note = self.size(sym, m.price, dist)
        if notional is None:
            self.log("RISK", "risk", f"Trade {m.key} refusé : {note}.")
            return None
        try:
            fill = await self.broker.open(self.feed.filters[sym], side, notional, m.price)
        except ExchangeError as e:
            self.log(who, "error", f"Ordre {m.key} rejeté par l'exchange : {e}.")
            return None
        dist, stop, tp = levels(sp, m, i, side, fill.price)
        self.seq += 1
        self.acct["balance"] -= fill.fee
        p = {"id": self.seq, "sym": sym, "k": m.key, "side": side, "qty": fill.qty, "entry": fill.price, "stop": stop, "tp": tp, "dist": dist,
             "sid": sp["id"], "sname": sp["name"], "t0": m.cur.t if m.cur else m.candles[-1].t, "opened": int(time.time() * 1000), "fee_in": fill.fee,
             "risk": fill.qty * dist, "mfeR": 0.0, "bars": 0, "feat": features(m, i, side, fill.price, dist), "manual": manual}
        self.positions.append(p)
        self.dirty = True
        self.log(who, "order", f"{'Achat' if side == 'long' else 'Vente à découvert'} {m.key} à {fr2(fill.price)} · stop {fr2(stop)} · objectif {fr2(tp)} · "
                 f"risque {self.xpf(p['risk'])}{note}{' · ' + self.broker.name if self.cfg.mode == 'testnet' else ''}")
        self.emit({"type": "fx", "kind": "order", "agent": who, "text": f"{'LONG' if side == 'long' else 'SHORT'} {m.key}!"})
        return p

    async def close_position(self, p: dict, price: float, reason: str) -> None:
        if p not in self.positions:
            return
        try:
            fill = await self.broker.close(self.feed.filters[p["sym"]], p["side"], p["qty"], price)
        except ExchangeError as e:
            self.log("EXEC", "error", f"Clôture {p['k']} impossible : {e}. Nouvel essai au prochain contrôle.")
            return
        self.positions.remove(p)
        gross = (fill.price - p["entry"] if p["side"] == "long" else p["entry"] - fill.price) * min(p["qty"], fill.qty or p["qty"])
        self.acct["balance"] += gross - fill.fee
        pnl = gross - fill.fee - p["fee_in"]
        r = pnl / (p["risk"] or 1)
        tr = {"id": p["id"], "k": p["k"], "sym": p["sym"], "side": p["side"], "entry": p["entry"], "exit": fill.price, "pnl": pnl, "r": r,
              "reason": reason, "sid": p["sid"], "sname": p["sname"], "bars": p.get("bars", 0), "mfeR": p.get("mfeR", 0), "feat": p.get("feat"),
              "t0": p["t0"], "t1": self.markets[p["sym"]].cur.t if self.markets[p["sym"]].cur else p["t0"], "closed": int(time.time() * 1000),
              "diag": None, "manual": p.get("manual", False)}
        self.journal.insert(0, tr)
        del self.journal[300:]
        sp = self.strat(p["sid"])
        if sp and not tr["manual"]:
            L = sp["live"]
            L["n"] += 1
            L["w"] += 1 if r > 0 else 0
            L["sumR"] += r
            L["since"] += 1
            L["consec"] = L["consec"] + 1 if r < 0 else 0
        self.dirty = True
        self.log("KHN", "win" if r > 0 else "loss", f"Clôture {p['k']} {'achat' if p['side'] == 'long' else 'vente'} : {self.sxpf(pnl)} ({fR(r)}), {RS_FR.get(reason, reason)}.")
        self.emit({"type": "fx", "kind": "win" if r > 0 else "loss", "agent": "KHN", "text": self.sxpf(pnl).replace(" XPF", "")})
        if not tr["manual"] and reason != "urgence":
            self.team.push("post", {"tid": tr["id"]})
        if self.equity() > self.acct["peak"]:
            self.acct["peak"] = self.equity()

    # --- tâches de l'équipe ---------------------------------------------------
    async def _handle(self, t, a) -> None:
        d = t.data
        if t.type == "scan":
            m = self.markets[d["sym"]]
            i = len(m.candles) - 1
            if m.candles[i].t != d["t"]:
                return  # une bougie plus récente a déjà sa propre tâche
            for s in self.strategies:
                if s["status"] != "active":
                    continue
                side = signal_at(s, m, i, self.risk["shorts"] and self.broker.can_short)
                if side:
                    self.log(a.name, "signal", f"Signal {'achat' if side == 'long' else 'vente'} sur {m.key} : « {s['name']} ».")
                    self.team.push("order", {"k": m.key, "sym": d["sym"], "side": side, "sid": s["id"], "t": d["t"]}, ttl=120)
                    break
        elif t.type == "order":
            s = self.strat(d["sid"])
            m = self.markets[d["sym"]]
            if m.candles[-1].t != d["t"] and len(m.candles) >= 2 and m.candles[-2].t != d["t"]:
                self.log(a.name, "error", f"Ordre {d['k']} annulé : signal trop ancien.")
            elif s:
                await self.open_position(s, d["sym"], d["side"], a.name)
        elif t.type == "post":
            tr = next((x for x in self.journal if x["id"] == d["tid"]), None)
            if not tr:
                return
            sp = self.strat(tr["sid"])
            tr["diag"] = learning.diagnose(tr, sp)
            if not tr["diag"]["ok"]:
                if tr["diag"]["causes"]:
                    self.log(a.name, "error", f"Erreur repérée sur le trade #{tr['id']} ({tr['k']}) : {tr['diag']['text']}.")
                else:
                    self.log(a.name, "info", f"Trade #{tr['id']} ({tr['k']}) : perte normale, pas d'erreur dans l'exécution.")
            if sp and sp["status"] == "active" and (sp["live"]["since"] >= 6 or sp["live"]["consec"] >= 3) and not self.team.pending("corr", lambda x: x["sid"] == sp["id"]):
                sp["live"]["since"] = 0
                self.team.push("corr", {"sid": sp["id"]})
            self.dirty = True
        elif t.type == "corr":
            sp = self.strat(d["sid"])
            if sp:
                self.correct(sp, a.name)
        elif t.type == "bt":
            self.backtest_task(d, a.name)
        elif t.type == "video":
            await self.video_task(d, a.name)
        elif t.type == "audit":
            if self.positions:
                tot = sum(p["risk"] for p in self.positions) / max(1e-9, self.equity()) * 100
                self.log(a.name, "risk", f"Contrôle : {len(self.positions)} position(s), risque total {fr2(tot)} % du capital, baisse {fr2(self.dd() * 100)} %.")

    def correct(self, sp: dict, who: str) -> None:
        res = learning.auto_correct(sp, self.journal, self.mlist(), self.risk["shorts"])
        if res["adopted"]:
            self.lessons.insert(0, {"ts": int(time.time() * 1000), "sid": sp["id"], "sname": sp["name"], "text": res["message"], "cause": res["cause"],
                                    "before": res["before"], "after": res["after"]})
            self.log(who, "fix", f"Auto-correction de « {sp['name']} » : {res['message']}. Test hors échantillon : {fR(res['before'])} → {fR(res['after'])} par trade.")
            self.emit({"type": "fx", "kind": "fix", "agent": who, "text": "CORRECTION OK"})
        else:
            self.log(who, "info", f"« {sp['name']} » : aucune correction ne tient sur des données jamais vues. Paramètres conservés pour éviter la sur-optimisation.")
        if res["suspend"]:
            L = sp["live"]
            self.log(who, "risk", f"« {sp['name']} » suspendue : {fR(L['sumR'] / L['n'])} par trade en démo et négative en test. Nouveau test plus tard.")
        self.dirty = True

    def backtest_task(self, d: dict, who: str) -> None:
        sp = self.strat(d["sid"])
        if not sp:
            return
        e = eval_spec(sp, self.mlist(), self.risk["shorts"])
        sp["bt"] = e
        mode = d.get("mode")
        if mode == "validate":
            if robust(e):
                sp["status"] = "active"
                self.log(who, "learn", f"« {sp['name']} » validée : {e['oos']['n']} trades hors échantillon, {fR(e['oos']['expR'])} par trade. Passage en démo.")
            else:
                b = adapt(sp, self.mlist(), self.risk["shorts"])
                if b:
                    c, e2 = b
                    sp.update(stop=c["stop"], tp=c["tp"], bt=e2, status="active", version=sp.get("version", 1) + 1)
                    sp["notes"].append(f"Paramètres adaptés par KHN : stop {c['stop']['mult']} × ATR, objectif {c['tp']['rr']} R".replace(".", ","))
                    self.log(who, "learn", f"« {sp['name']} » ne passait pas telle quelle. Version adaptée : {fR(e2['oos']['expR'])} par trade hors échantillon. Passage en démo.")
                else:
                    sp["status"] = "rejetee"
                    sp["notes"].append("Rejetée : pas d'avantage mesurable sur des données jamais vues.")
                    self.log(who, "learn", f"« {sp['name']} » rejetée : {e['oos']['n']} trades hors échantillon, {fR(e['oos']['expR'])} par trade. Aucune variante robuste.")
        elif mode == "retest":
            if robust(e):
                sp["status"] = "active"
                sp["live"]["consec"] = 0
                self.log(who, "learn", f"« {sp['name']} » de nouveau positive en test ({fR(e['oos']['expR'])}) : réactivée.")
        else:
            self.log(who, "info", f"Backtest « {sp['name']} » : historique {e['is']['n']} trades ({fR(e['is']['expR'])}), hors échantillon {e['oos']['n']} trades "
                     f"({fR(e['oos']['expR'])}, facteur de profit {e['oos']['pf']:.2f}).".replace(f"{e['oos']['pf']:.2f}", f"{e['oos']['pf']:.2f}".replace(".", ",")))
        self.dirty = True

    # --- apprentissage vidéo -------------------------------------------------
    def learn(self, url: str, notes: str) -> dict | None:
        if not re.match(r"^https?://\S+$", url or ""):
            self.log("KHN", "error", "Lien invalide : il doit commencer par https://")
            return None
        self.snum += 1
        sp = new_strategy({"name": "Analyse en cours…", "family": "trend", "source": "Vidéo", "url": url, "long": [], "short": [],
                           "stop": {"type": "atr", "mult": 1.5}, "tp": {"rr": 2.0}}, self.snum)
        sp["status"] = "analyse"
        self.strategies.append(sp)
        self.team.recruit("LEARN", "une vidéo est à étudier")
        self.team.push("video", {"sid": sp["id"], "url": url, "notes": notes or ""})
        self.log("TOI", "you", f"Nouvelle stratégie à étudier : {url}")
        self.dirty = True
        return sp

    async def video_task(self, d: dict, who: str) -> None:
        sp = self.strat(d["sid"])
        if not sp:
            return
        text, how = d.get("notes", "").strip(), "notes"
        if not text:
            text, err = await transcript(d["url"])
            if not text:
                self._reject_video(sp, who, f"Transcription introuvable : {err}. Colle la transcription ou les règles dans le formulaire.")
                return
            how = "transcription YouTube"
            self.log(who, "learn", f"Transcription récupérée ({len(text)} caractères).")
        raw, via = None, "mots-clés"
        if self.brain.available:
            try:
                raw = await self.brain.extract_strategy(d["url"], text)
                via = "Claude"
            except BrainError as e:
                self.log(who, "error", f"Analyse par Claude impossible ({e}) : analyse par mots-clés à la place.")
        if raw and raw.get("insufficient"):
            miss = ", ".join(raw.get("missing") or [])[:200]
            self._reject_video(sp, who, "Le texte ne contient pas de règles d'entrée et de sortie chiffrables." + (f" Manque : {miss}." if miss else ""), raw.get("summary", ""))
            return
        spec = spec_from_raw(raw) if raw else spec_from_raw(local_parse(text) or {})
        if not spec:
            self._reject_video(sp, who, "Aucune règle chiffrable trouvée dans le texte.")
            return
        sp.update(copy.deepcopy(spec))
        sp.update(status="test", source=f"Vidéo · {how} · analysée par {via}")
        sp["notes"] = [f"Hypothèse : {x}" for x in spec.get("assumptions", [])]
        self.log(who, "learn", f"Règles extraites : « {sp['name']} ». {sp.get('summary', '')} Test sur l'historique en cours.")
        self.emit({"type": "fx", "kind": "fix", "agent": who, "text": "REGLES OK"})
        self.team.push("bt", {"sid": sp["id"], "mode": "validate"})
        self.dirty = True

    def _reject_video(self, sp: dict, who: str, why: str, summary: str = "") -> None:
        sp.update(status="rejetee", name="Vidéo sans règles exploitables", summary=summary or why)
        sp["notes"] = [why]
        self.log(who, "learn", why)
        self.dirty = True

    # --- commandes -------------------------------------------------------------
    HELP = [("statut", "résumé du compte et de l'équipe"), ("rapport", "performance par stratégie"), ("pause / reprendre", "suspendre ou relancer les nouvelles positions"),
            ("stop", "arrêt d'urgence : ferme tout et bloque"), ("fermer btc|eth|sol|tout", "clôturer des positions"), ("long btc / short eth", "ordre manuel, soumis au contrôle du risque"),
            ("risque 0.5", "risque par trade en %"), ("strat", "liste des stratégies"), ("strat on|off 2", "activer ou couper la stratégie n° 2"), ("backtest 2", "tester la stratégie n° 2"),
            ("corriger 2", "lancer l'auto-correction de la n° 2"), ("apprendre <lien> <règles>", "étudier une stratégie"), ("recruter scout|exec|quant|learn|risk", "appeler un robot"),
            ("renvoyer quant", "renvoyer un robot"), ("capital 1000", "réinitialiser la démo avec ce capital en XPF"), ("autre phrase", "question posée à KHN (IA)")]

    def by_num(self, n) -> dict | None:
        try:
            return next((s for s in self.strategies if s["num"] == int(n)), None)
        except (TypeError, ValueError):
            return None

    async def command(self, raw: str) -> None:
        txt = (raw or "").strip()[:2000]
        if not txt:
            return
        self.log("TOI", "you", txt)
        parts = txt.split()
        c = parts[0].lower().translate(str.maketrans("éèêàç", "eeeac"))
        a1 = parts[1] if len(parts) > 1 else ""
        a2 = parts[2] if len(parts) > 2 else ""
        L = lambda typ, msg, who="KHN": self.log(who, typ, msg)  # noqa: E731
        if c in ("aide", "help"):
            for k, v in self.HELP:
                L("info", f"{k} — {v}")
        elif c in ("statut", "status"):
            e = self.equity()
            st = "ARRÊT D'URGENCE actif." if self.flags["killed"] else "Trading en pause." if self.flags["paused"] else "Bloqué : baisse maximale." if self.flags["dd_halt"] else "Trading actif."
            L("info", f"Capital {self.xpf(e)} ({fr2(e)} USDT), {self.sxpf(e - self.acct['start'])} depuis le départ. {len(self.positions)} position(s). "
                      f"Équipe : {', '.join(a.name for a in self.team.agents)}. {len(self.team.queue)} tâche(s) en file. Mode {self.cfg.mode}. {st}")
        elif c == "rapport":
            for s in self.strategies:
                Lv = s["live"]
                L("info", f"n° {s['num']} « {s['name']} » [{s['status']}] démo : {Lv['n']} trades, {round(Lv['w'] / Lv['n'] * 100) if Lv['n'] else 0} % gagnants, "
                          f"{fR(Lv['sumR'] / Lv['n'] if Lv['n'] else 0)}/trade · test : {fR(s['bt']['oos']['expR']) if s.get('bt') else '—'}.", "QUANT")
        elif c == "pause":
            self.flags["paused"] = True
            L("risk", "Pause : je continue de surveiller, sans nouvelle position.")
        elif c in ("reprendre", "resume"):
            self.flags.update(paused=False, dd_halt=False, killed=False)
            self.acct["peak"] = self.equity()
            self.risk_mult = 1.0
            L("info", "Trading relancé. Le compteur de baisse repart du capital actuel.")
        elif c in ("stop", "urgence"):
            await self.kill()
        elif c in ("fermer", "close"):
            m = self.by_key(a1) if a1 and a1.lower() not in ("tout", "all") else None
            lst = [p for p in self.positions if not m or p["sym"] == m.symbol]
            if not lst:
                L("info", "Aucune position à fermer.")
            for p in lst:
                await self.close_position(p, self.markets[p["sym"]].price, "manuel")
        elif c in ("long", "achat", "acheter", "short", "vente", "vendre"):
            m = self.by_key(a1)
            if not m:
                L("error", "Précise le marché : " + ", ".join(x.key.lower() for x in self.mlist()) + ".")
            else:
                await self.open_position(MANUAL, m.symbol, "long" if c in ("long", "achat", "acheter") else "short", "KHN", manual=True)
        elif c == "risque":
            try:
                v = float(a1.replace(",", "."))
            except ValueError:
                v = -1
            if not 0.25 <= v <= 2:
                L("error", "Risque entre 0,25 et 2 %.")
            else:
                self.risk["per_trade"] = v
                L("risk", f"Risque par trade fixé à {fr2(v)} %.", "RISK")
        elif c == "strat":
            if not a1:
                for s in self.strategies:
                    L("info", f"n° {s['num']} « {s['name']} » — {s['status']}")
            else:
                s = self.by_num(a2)
                if not s:
                    L("error", "Numéro de stratégie inconnu. Tape « strat ».")
                elif a1 == "on":
                    s["status"] = "active"
                    L("info", f"« {s['name']} » activée.")
                elif a1 == "off":
                    s["status"] = "off"
                    L("info", f"« {s['name']} » coupée.")
        elif c == "backtest":
            s = self.by_num(a1)
            if s:
                self.team.push("bt", {"sid": s["id"], "mode": "manual"})
                L("info", f"Backtest de « {s['name']} » ajouté à la file.")
            else:
                L("error", "Ex. : backtest 2")
        elif c == "corriger":
            s = self.by_num(a1)
            if s:
                self.team.push("corr", {"sid": s["id"]})
                L("info", f"Auto-correction de « {s['name']} » ajoutée à la file.")
            else:
                L("error", "Ex. : corriger 1")
        elif c in ("apprendre", "learn"):
            if not a1:
                L("error", "Ex. : apprendre https://youtu.be/… (les règles ou la transcription peuvent suivre le lien)")
            else:
                self.learn(a1, " ".join(parts[2:]))
        elif c == "recruter":
            n = a1.upper()
            if n not in ROLES or n == "KHN":
                L("error", "Rôles : scout, exec, quant, learn, risk.")
            elif not self.team.recruit(n, "à ta demande"):
                L("info", f"{n} est déjà là (ou l'équipe est complète).")
        elif c == "renvoyer":
            if not self.team.dismiss(a1.upper()):
                L("error", "Ce robot n'est pas là, est au travail, ou c'est KHN (qui reste toujours).")
        elif c == "capital":
            try:
                v = float(a1.replace(" ", ""))
            except ValueError:
                v = 0
            if v < 1000:
                L("error", "Capital minimum : 1 000 XPF.")
            else:
                await self.reset(v)
        else:
            if not self.brain.available:
                L("error", "Commande inconnue. Tape « aide ». (Questions libres : il faut une clé API Claude.)")
            else:
                self.spawn(self._ask(txt))
        self.dirty = True

    async def _ask(self, txt: str) -> None:
        try:
            self.log("KHN", "ai", await self.brain.answer(txt, self.context()))
        except BrainError as e:
            self.log("KHN", "error", f"Je n'ai pas pu répondre : {e}.")

    def context(self) -> dict:
        return {"capital_usdt": round(self.equity(), 4), "capital_xpf": round(self.equity() * self.rate()), "depart_xpf": round(self.acct["start"] * self.rate()),
                "mode": self.cfg.mode, "risque": self.risk, "baisse_pct": round(self.dd() * 100, 1),
                "positions": [{"m": p["k"], "sens": p["side"], "entree": p["entry"], "stop": p["stop"], "tp": p["tp"], "strat": p["sname"]} for p in self.positions],
                "trades": [{"m": t["k"], "sens": t["side"], "r": round(t["r"], 2), "sortie": t["reason"], "diag": (t.get("diag") or {}).get("text")} for t in self.journal[:10]],
                "strategies": [{"n": s["num"], "nom": s["name"], "statut": s["status"], "demo_trades": s["live"]["n"],
                                "test_R": round(s["bt"]["oos"]["expR"], 2) if s.get("bt") else None, "filtres": s.get("filters")} for s in self.strategies],
                "lecons": [x["text"] for x in self.lessons[:8]], "equipe": [a.name for a in self.team.agents]}

    async def kill(self) -> None:
        self.flags["killed"] = True
        for p in list(self.positions):
            await self.close_position(p, self.markets[p["sym"]].price, "urgence")
        self.team.queue = [t for t in self.team.queue if t.type != "order" or t.agent]
        self.log("RISK", "risk", "Arrêt d'urgence : toutes les positions sont fermées, aucun nouvel ordre. Tape « reprendre » pour relancer.")
        self.save()

    async def reset(self, capital_xpf: float) -> None:
        for p in list(self.positions):
            await self.close_position(p, self.markets[p["sym"]].price, "manuel")
        start = capital_xpf / self.rate()
        self.acct = {"start": start, "balance": start, "peak": start, "day_start": start, "day": self.today()}
        self.journal, self.eq_hist = [], [start]
        self.flags.update(dd_halt=False, day_halt="")
        self.risk_mult = 1.0
        for s in self.strategies:
            s["live"] = {"n": 0, "w": 0, "sumR": 0.0, "since": 0, "consec": 0}
        self.log("KHN", "info", f"Démo réinitialisée avec {self.xpf(start)}. Stratégies et leçons conservées.")
        self.save()

    def set_risk(self, data: dict) -> None:
        lim = {"per_trade": (0.25, 2), "max_daily": (1, 10), "max_dd": (5, 30), "max_pos": (1, 3), "lev": (1, 3)}
        for k, (lo, hi) in lim.items():
            if k in data:
                try:
                    v = float(data[k])
                except (TypeError, ValueError):
                    continue
                self.risk[k] = int(v) if k in ("max_pos", "lev") else max(lo, min(hi, v))
                if k in ("max_pos", "lev"):
                    self.risk[k] = max(lo, min(hi, self.risk[k]))
        if "shorts" in data:
            self.risk["shorts"] = bool(data["shorts"]) and self.broker.can_short
        if not self.broker.can_short:
            self.risk["lev"] = 1
        self.dirty = True

    # --- état envoyé à l'interface --------------------------------------------
    def snapshot(self) -> dict:
        mk = {}
        for m in self.mlist():
            cs = m.candles[-120:] + ([m.cur] if m.cur else [])
            ref = m.candles[-288].c if len(m.candles) >= 288 else (m.candles[0].c if m.candles else m.price)
            e21 = _tail_ema(m, 21, len(cs) - (1 if m.cur else 0))
            e50 = _tail_ema(m, 50, len(cs) - (1 if m.cur else 0))
            mk[m.key] = {"sym": m.symbol, "price": m.price, "chg": (m.price - ref) / ref * 100 if ref else 0,
                         "candles": [[c.t, c.o, c.h, c.l, c.c] for c in cs], "ema21": e21, "ema50": e50, "regime": _regime(m)}
        return {
            "type": "state", "mode": self.cfg.mode, "broker": self.broker.name, "status": self.status, "ready": self.ready,
            "connected": getattr(self.feed, "connected", True), "ai": self.brain.available, "rate": self.rate(), "ts": int(time.time() * 1000),
            "acct": {**self.acct, "equity": self.equity(), "dd": self.dd()}, "risk": self.risk, "risk_mult": self.risk_mult, "flags": self.flags,
            "can_short": self.broker.can_short, "min_notional": min((self.min_notional(s) for s in self.markets), default=5.0),
            "markets": mk,
            "positions": [{**{k: p[k] for k in ("id", "k", "side", "qty", "entry", "stop", "tp", "sname", "risk", "t0")}, "unreal": self.unreal(p)} for p in self.positions],
            "journal": [{k: t.get(k) for k in ("id", "k", "side", "entry", "exit", "pnl", "r", "reason", "sname", "t0", "t1", "diag", "manual")} for t in self.journal[:40]],
            "lessons": self.lessons[:12], "strategies": self.strategies,
            "team": self.team.snapshot(), "queue": self.team.queue_counts(), "eq": self.eq_hist[-400:],
            "stats": {"n": len(self.journal), "w": sum(1 for t in self.journal if t["pnl"] > 0), "pnl": sum(t["pnl"] for t in self.journal)},
        }


def _tail_ema(m: Market, n: int, count: int) -> list:
    from .strategy import series
    s = series(m, {"k": "ema", "n": n})[-count:] if count > 0 else []
    return [None if x != x else round(x, 6) for x in s]


def _regime(m: Market) -> str:
    from .strategy import ok, series, val
    i = len(m.candles) - 1
    if i < 60:
        return "—"
    er, e50 = val(m, {"k": "er", "n": 20}, i), series(m, {"k": "ema", "n": 50})[i]
    if not ok(er):
        return "—"
    if er > 0.35:
        return ("tendance haussière" if m.price > e50 else "tendance baissière") + f" · force {er:.2f}".replace(".", ",")
    return ("marché sans direction" if er < 0.18 else "mouvement modéré") + f" · force {er:.2f}".replace(".", ",")


# --- analyse locale (sans IA) -------------------------------------------------

def local_parse(text: str) -> dict | None:
    t = (text or "").lower()

    def num(s, d):
        try:
            return float(str(s).replace(",", "."))
        except (TypeError, ValueError):
            return d

    def find(p, d=None):
        mm = re.search(p, t)
        return mm.group(1) if mm else d

    sp = None
    if "rsi" in t:
        n = int(num(find(r"rsi\s*\(?\s*(\d{1,3})"), 14))
        lv = [int(x) for x in re.findall(r"(?<![\d.,:])(\d{2})(?![\d.,])", t) if 5 <= int(x) <= 95 and int(x) != n]
        lo = float(min(lv)) if lv and min(lv) < 50 else 30.0
        hi = float(max(lv)) if lv and max(lv) > 50 else 70.0
        sp = {"name": f"RSI {n} ({lo:g}/{hi:g})", "family": "meanrev", "long": [C("rsi", n, "cross_above", "num", lo)], "short": [C("rsi", n, "cross_below", "num", hi)]}
    elif "bollinger" in t:
        sp = {"name": "Rebond Bollinger", "family": "meanrev", "long": [C("close", 0, "cross_above", "bb_lo", 20, 2.0)], "short": [C("close", 0, "cross_below", "bb_up", 20, 2.0)]}
    elif re.search(r"(ema|moyenne mobile|mm\s?\d|sma)", t):
        ns = [int(x) for x in re.findall(r"(?:ema|mm|sma|moyennes? mobiles?)\s*(?:de\s*)?(\d{1,3})", t) if 2 <= int(x) <= 200]
        f, s = (min(ns), max(ns)) if len(set(ns)) >= 2 else (9, 21)
        sp = {"name": f"Croisement {f}/{s}", "family": "trend", "long": [C("ema", f, "cross_above", "ema", s)], "short": [C("ema", f, "cross_below", "ema", s)]}
    elif re.search(r"(cassure|breakout|donchian|plus haut)", t):
        n = int(num(find(r"(\d{1,3})\s*(?:bougies|p[ée]riodes|candles|jours)"), 20))
        sp = {"name": f"Cassure {n}", "family": "breakout", "long": [C("close", 0, "cross_above", "don_hi", n)], "short": [C("close", 0, "cross_below", "don_lo", n)]}
    elif "macd" in t:
        sp = {"name": "Croisement MACD", "family": "trend", "long": [{"a": {"k": "macd"}, "op": "cross_above", "b": {"k": "macd_sig"}}],
              "short": [{"a": {"k": "macd"}, "op": "cross_below", "b": {"k": "macd_sig"}}]}
    if not sp:
        return None
    am, pm = find(r"(\d+(?:[.,]\d+)?)\s*(?:x|×)?\s*(?:l')?atr"), find(r"stop[^\d]{0,20}(\d+(?:[.,]\d+)?)\s*%")
    rr = find(r"(?:rr|ratio|risque\s*/?\s*rendement|r\s*/\s*r)[^\d]{0,8}1\s*[:/]\s*(\d+(?:[.,]\d+)?)") or find(r"1\s*:\s*(\d+(?:[.,]\d+)?)")
    sp["stop"] = {"type": "pct", "value": num(pm, 1)} if pm else {"type": "atr", "mult": num(am, 1.5)}
    sp["tp"] = {"rr": num(rr, 2)}
    sp["summary"] = "Règles extraites par mots-clés (sans IA) : vérifie-les."
    sp["assumptions"] = ["Analyse par mots-clés : paramètres non cités remplacés par des valeurs standard."]
    return sp
