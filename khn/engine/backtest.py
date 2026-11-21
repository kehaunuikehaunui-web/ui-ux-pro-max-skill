"""Backtest en multiples de R (risque initial), indépendant de la taille du compte."""
from __future__ import annotations

import copy

from .strategy import Market, exit_signal, levels, signal_at

COST_PCT = 0.0024  # frais aller-retour + glissement, en fraction du prix d'entrée


def stats(rs: list[float]) -> dict:
    n = len(rs)
    gp = sum(r for r in rs if r > 0)
    gl = -sum(r for r in rs if r < 0)
    eq = pk = dd = 0.0
    for r in rs:
        eq += r
        pk = max(pk, eq)
        dd = max(dd, pk - eq)
    return {"n": n, "wr": (sum(1 for r in rs if r > 0) / n) if n else 0.0, "expR": (gp - gl) / n if n else 0.0,
            "pf": (gp / gl) if gl else (9.99 if gp else 0.0), "totR": gp - gl, "ddR": dd}


def backtest(sp: dict, markets: list[Market], frm: int, to: int, allow_short: bool) -> dict:
    rs: list[float] = []
    for m in markets:
        C = m.candles
        end = min(to, len(C) - 1)
        pos = None
        for i in range(max(frm, 110), end + 1):
            c = C[i]
            if pos:
                ex = None
                if pos["side"] == "long":
                    if c.l <= pos["stop"]:
                        ex = min(pos["stop"], c.o)
                    elif c.h >= pos["tp"]:
                        ex = pos["tp"]
                else:
                    if c.h >= pos["stop"]:
                        ex = max(pos["stop"], c.o)
                    elif c.l <= pos["tp"]:
                        ex = pos["tp"]
                if ex is None and exit_signal(sp, m, i, pos["side"]):
                    ex = c.c
                if ex is None and i - pos["i"] > 150:
                    ex = c.c
                if ex is not None:
                    raw = ex - pos["entry"] if pos["side"] == "long" else pos["entry"] - ex
                    rs.append(raw / pos["dist"] - COST_PCT * pos["entry"] / pos["dist"])
                    pos = None
                continue
            side = signal_at(sp, m, i, allow_short)
            if side:
                dist, stop, tp = levels(sp, m, i, side, c.c)
                pos = {"side": side, "entry": c.c, "stop": stop, "tp": tp, "dist": dist, "i": i}
    return stats(rs)


def eval_spec(sp: dict, markets: list[Market], allow_short: bool) -> dict:
    """70 % de l'historique pour apprendre, 30 % jamais vus pour valider."""
    n = min(len(m.candles) for m in markets) if markets else 0
    split = int(n * 0.7)
    return {"is": backtest(sp, markets, 60, split, allow_short), "oos": backtest(sp, markets, split + 1, n - 1, allow_short)}


def robust(e: dict, min_trades: int = 8) -> bool:
    return e["oos"]["n"] >= min_trades and e["oos"]["expR"] > 0 and e["is"]["expR"] > -0.05


def adapt(sp: dict, markets: list[Market], allow_short: bool) -> tuple[dict, dict] | None:
    """Cherche un couple stop/objectif qui tient hors échantillon. None si rien de robuste."""
    best = None
    for sm in (1.0, 1.5, 2.0, 2.5):
        for rr in (1.5, 2.0, 3.0):
            c = copy.deepcopy(sp)
            c["stop"], c["tp"] = {"type": "atr", "mult": sm}, {"rr": rr}
            e = eval_spec(c, markets, allow_short)
            if e["oos"]["n"] >= 8 and e["is"]["expR"] > 0 and e["oos"]["expR"] > 0.03 and (best is None or e["oos"]["expR"] > best[1]["oos"]["expR"]):
                best = (c, e)
    return best
