"""Verrou du mode réel : toutes les conditions doivent être remplies, sinon KHN reste en essai à blanc."""
from __future__ import annotations

import glob
import os
import time

from .store import Store

LIVE_PHRASE = "JE RISQUE MON ARGENT"
DEMO_DAYS, DEMO_TRADES, MIN_EXP_R, MIN_PF = 60, 50, 0.05, 1.1


def _fr(x: float, d: int = 2) -> str:
    return f"{x:,.{d}f}".replace(",", " ").replace(".", ",")


def demo_stats(store: Store) -> dict:
    acct = store.get("acct", {}) or {}
    trades = [t for t in (store.get("journal", []) or []) if not t.get("manual")]
    eq = store.get("eq", []) or []
    risk = store.get("risk", {}) or {}
    now = time.time()
    first = min((t.get("closed", now * 1000) / 1000 for t in trades), default=now)
    created = acct.get("created") or first
    rs = [t["r"] for t in trades]
    gp, gl = sum(r for r in rs if r > 0), -sum(r for r in rs if r < 0)
    pk, dd = 0.0, 0.0
    for v in eq:
        pk = max(pk, v)
        if pk > 0:
            dd = max(dd, (pk - v) / pk)
    return {"days": max(0.0, (now - created) / 86400), "n": len(rs), "expR": (gp - gl) / len(rs) if rs else 0.0,
            "pf": gp / gl if gl else (9.99 if gp else 0.0), "dd": dd, "max_dd": float(risk.get("max_dd", 10))}


def best_demo_store(data_dir: str) -> Store | None:
    """La base de démo (papier ou testnet) qui contient le plus de trades."""
    best, best_n = None, -1
    for path in glob.glob(os.path.join(data_dir, "khn-*.sqlite")):
        if os.path.basename(path) not in ("khn-paper.sqlite", "khn-testnet.sqlite"):
            continue
        st = Store(path)
        n = len(st.get("journal", []) or [])
        if n > best_n:
            best, best_n = st, n
        else:
            st.close()
    return best


def evaluate(demo: dict, capital_xpf: float, min_capital_xpf: float, live: dict | None, armed: bool) -> list[dict]:
    """live = None hors mode réel ; sinon {"keys": bool, "withdraw": bool | None, "dryrun": bool}."""
    c = [
        {"key": "days", "label": f"{DEMO_DAYS} jours de démo", "ok": demo["days"] >= DEMO_DAYS, "detail": f"{demo['days']:.0f} / {DEMO_DAYS} jours"},
        {"key": "trades", "label": f"{DEMO_TRADES} trades en démo", "ok": demo["n"] >= DEMO_TRADES, "detail": f"{demo['n']} / {DEMO_TRADES} trades"},
        {"key": "edge", "label": "Résultat moyen positif en démo", "ok": demo["n"] >= DEMO_TRADES and demo["expR"] >= MIN_EXP_R and demo["pf"] >= MIN_PF,
         "detail": f"{'+' if demo['expR'] >= 0 else '−'}{_fr(abs(demo['expR']))} R par trade (minimum +0,05) · facteur de profit {_fr(demo['pf'])} (minimum 1,10)"},
        {"key": "dd", "label": "Baisse max de la démo sous la limite", "ok": demo["n"] > 0 and demo["dd"] * 100 <= demo["max_dd"],
         "detail": f"{_fr(demo['dd'] * 100, 1)} % (limite {_fr(demo['max_dd'], 0)} %)"},
        {"key": "capital", "label": "Capital réel suffisant", "ok": capital_xpf >= min_capital_xpf,
         "detail": f"{_fr(capital_xpf, 0)} XPF pour {_fr(min_capital_xpf, 0)} XPF minimum (au-dessous, frais de dépôt et ordre minimum mangent le capital)"},
    ]
    if live is None:
        pend = "à vérifier en mode réel (KHN_MODE=live)"
        c += [{"key": "keys", "label": "Clés Kraken valides", "ok": None, "detail": pend},
              {"key": "withdraw", "label": "Clé Kraken sans droit de retrait", "ok": None, "detail": pend},
              {"key": "dryrun", "label": "Ordre d'essai validé par Kraken", "ok": None, "detail": pend}]
    else:
        w = live.get("withdraw")
        c += [{"key": "keys", "label": "Clés Kraken valides", "ok": bool(live.get("keys")), "detail": live.get("keys_detail", "")},
              {"key": "withdraw", "label": "Clé Kraken sans droit de retrait", "ok": w is False,
               "detail": "vérifié" if w is False else "la clé peut retirer des fonds : recrée-la sans « Withdraw Funds »" if w else "vérification impossible"},
              {"key": "dryrun", "label": "Ordre d'essai validé par Kraken", "ok": bool(live.get("dryrun")), "detail": live.get("dryrun_detail", "")}]
    c.append({"key": "armed", "label": "Activation par toi", "ok": armed, "detail": "activé" if armed else f"tape « {LIVE_PHRASE} » dans l'onglet Réel"})
    return c


def ready(checks: list[dict], ignore: tuple[str, ...] = ()) -> bool:
    return all(x["ok"] is True for x in checks if x["key"] not in ignore)
