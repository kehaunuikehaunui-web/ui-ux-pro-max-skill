"""Marché (bougies + cache d'indicateurs) et langage de stratégie commun au prototype et au moteur."""
from __future__ import annotations

import copy
import math
import time
from dataclasses import dataclass, field

from . import indicators as ind


@dataclass(slots=True)
class Candle:
    t: int  # heure d'ouverture (ms)
    o: float
    h: float
    l: float
    c: float


@dataclass
class Market:
    symbol: str  # ex. BTCUSDT
    candles: list[Candle] = field(default_factory=list)
    price: float = 0.0
    cur: Candle | None = None  # bougie en cours de formation
    _cache: dict = field(default_factory=dict)
    _cache_n: int = -1

    @property
    def key(self) -> str:
        return self.symbol.replace("USDT", "")

    def append(self, c: Candle, max_len: int = 3000) -> None:
        self.candles.append(c)
        if len(self.candles) > max_len:
            del self.candles[: len(self.candles) - max_len]

    def index_of(self, t: int) -> int | None:
        if not self.candles:
            return None
        step = (self.candles[-1].t - self.candles[0].t) / max(1, len(self.candles) - 1) or 1
        i = int(round((t - self.candles[0].t) / step))
        if 0 <= i < len(self.candles) and self.candles[i].t == t:
            return i
        for j, c in enumerate(self.candles):  # repli si trous dans l'historique
            if c.t == t:
                return j
        return None


OPERANDS = {"close", "ema", "sma", "rsi", "atr", "bb_up", "bb_lo", "bb_mid", "don_hi", "don_lo", "macd", "macd_sig", "num"}
NEED_N = {"ema", "sma", "rsi", "atr", "bb_up", "bb_lo", "bb_mid", "don_hi", "don_lo", "er"}
OPS = {">", "<", "cross_above", "cross_below"}


def series(m: Market, o: dict) -> list[float]:
    if m._cache_n != len(m.candles):
        m._cache, m._cache_n = {}, len(m.candles)
    key = (o["k"], o.get("n"), o.get("m"))
    s = m._cache.get(key)
    if s is not None:
        return s
    C = m.candles
    cl = [c.c for c in C]
    k = o["k"]
    if k == "close":
        s = cl
    elif k == "ema":
        s = ind.ema(cl, o["n"])
    elif k == "sma":
        s = ind.sma(cl, o["n"])
    elif k == "rsi":
        s = ind.rsi(cl, o["n"])
    elif k == "atr":
        s = ind.atr([c.h for c in C], [c.l for c in C], cl, o["n"])
    elif k in ("bb_up", "bb_lo", "bb_mid"):
        mid, up, lo = ind.bollinger(cl, o["n"], o.get("m") or 2)
        s = {"bb_up": up, "bb_lo": lo, "bb_mid": mid}[k]
    elif k in ("don_hi", "don_lo"):
        s = ind.donchian([c.h for c in C], [c.l for c in C], o["n"], k == "don_hi")
    elif k in ("macd", "macd_sig"):
        line, sig = ind.macd(cl)
        s = line if k == "macd" else sig
    elif k == "er":
        s = ind.efficiency_ratio(cl, o["n"])
    elif k == "atrp":
        a = ind.atr([c.h for c in C], [c.l for c in C], cl, 14)
        s = [x / y if y else ind.NAN for x, y in zip(a, cl)]
    else:
        s = [ind.NAN] * len(C)
    m._cache[key] = s
    return s


def val(m: Market, o: dict, i: int) -> float:
    if o["k"] == "num":
        return o["v"]
    if i < 0:
        return ind.NAN
    return series(m, o)[i]


def ok(x: float) -> bool:
    return x is not None and not math.isnan(x) and not math.isinf(x)


def cond_true(m: Market, c: dict, i: int) -> bool:
    a, b = val(m, c["a"], i), val(m, c["b"], i)
    if not ok(a) or not ok(b):
        return False
    op = c["op"]
    if op == ">":
        return a > b
    if op == "<":
        return a < b
    a0, b0 = val(m, c["a"], i - 1), val(m, c["b"], i - 1)
    if not ok(a0) or not ok(b0):
        return False
    if op == "cross_above":
        return a0 <= b0 and a > b
    if op == "cross_below":
        return a0 >= b0 and a < b
    return False


def atr_pct(m: Market, i: int) -> float:
    s = series(m, {"k": "atrp"})
    v = s[i] if 0 <= i < len(s) else ind.NAN
    if not ok(v):
        return 0.5
    win = [x for x in s[max(14, i - 200) : i] if ok(x)]
    return sum(1 for x in win if x < v) / len(win) if win else 0.5


def filters_pass(sp: dict, m: Market, i: int, side: str) -> bool:
    f = sp.get("filters") or {}
    if f.get("minTrend") is not None and not (val(m, {"k": "er", "n": 20}, i) >= f["minTrend"]):
        return False
    if f.get("maxTrend") is not None and not (val(m, {"k": "er", "n": 20}, i) <= f["maxTrend"]):
        return False
    if f.get("htfAlign"):
        e, c = val(m, {"k": "ema", "n": 100}, i), m.candles[i].c
        if not ok(e) or (c < e if side == "long" else c > e):
            return False
    if f.get("maxVolPct") is not None and atr_pct(m, i) > f["maxVolPct"]:
        return False
    return True


def signal_at(sp: dict, m: Market, i: int, allow_short: bool) -> str | None:
    for side in ("long", "short"):
        conds = sp.get(side) or []
        if not conds or (side == "short" and not allow_short):
            continue
        if all(cond_true(m, c, i) for c in conds) and filters_pass(sp, m, i, side):
            return side
    return None


def exit_signal(sp: dict, m: Market, i: int, side: str) -> bool:
    conds = sp.get("exitLong" if side == "long" else "exitShort") or []
    return bool(conds) and all(cond_true(m, c, i) for c in conds)


def levels(sp: dict, m: Market, i: int, side: str, price: float) -> tuple[float, float, float]:
    """Renvoie (distance du stop, stop, objectif)."""
    a = val(m, {"k": "atr", "n": 14}, i)
    st = sp["stop"]
    dist = price * st["value"] / 100 if st["type"] == "pct" else (a if ok(a) else price * 0.005) * st["mult"]
    dist = max(dist, price * 0.002)
    rr = sp["tp"]["rr"]
    if side == "long":
        return dist, price - dist, price + dist * rr
    return dist, price + dist, price - dist * rr


def features(m: Market, i: int, side: str, price: float, dist: float) -> dict:
    e100, a = val(m, {"k": "ema", "n": 100}, i), val(m, {"k": "atr", "n": 14}, i)
    er = val(m, {"k": "er", "n": 20}, i)
    return {
        "er": round(er, 3) if ok(er) else 0.0,
        "htf": True if not ok(e100) else (price >= e100 if side == "long" else price <= e100),
        "atrP": round(atr_pct(m, i), 2),
        "stopAtr": round(dist / a, 2) if ok(a) and a else 1.5,
    }


# --- Stratégies de base -------------------------------------------------------

def C(ka, na, op, kb, nb=None, mb=None) -> dict:
    a = {"k": "close"} if ka == "close" else {"k": ka, "n": na}
    if kb == "num":
        b = {"k": "num", "v": nb}
    elif kb == "close":
        b = {"k": "close"}
    else:
        b = {"k": kb, "n": nb} if mb is None else {"k": kb, "n": nb, "m": mb}
    return {"a": a, "op": op, "b": b}


def builtins() -> list[dict]:
    return [
        {"name": "Croisement EMA 9/21", "family": "trend", "source": "Formation de base",
         "long": [C("ema", 9, "cross_above", "ema", 21), C("close", 0, ">", "ema", 50)],
         "short": [C("ema", 9, "cross_below", "ema", 21), C("close", 0, "<", "ema", 50)],
         "stop": {"type": "atr", "mult": 1.5}, "tp": {"rr": 2.0}},
        {"name": "Rebond RSI", "family": "meanrev", "source": "Formation de base",
         "long": [C("rsi", 14, "cross_above", "num", 30)], "short": [C("rsi", 14, "cross_below", "num", 70)],
         "exitLong": [C("rsi", 14, ">", "num", 58)], "exitShort": [C("rsi", 14, "<", "num", 42)],
         "stop": {"type": "atr", "mult": 1.2}, "tp": {"rr": 1.5}},
        {"name": "Cassure Donchian 20", "family": "breakout", "source": "Formation de base",
         "long": [C("close", 0, "cross_above", "don_hi", 20)], "short": [C("close", 0, "cross_below", "don_lo", 20)],
         "stop": {"type": "atr", "mult": 2.0}, "tp": {"rr": 2.5}},
    ]


def new_strategy(sp: dict, num: int) -> dict:
    base = {"id": f"s{int(time.time() * 1000):x}{num}", "num": num, "status": "active", "filters": {}, "version": 1,
            "live": {"n": 0, "w": 0, "sumR": 0.0, "since": 0, "consec": 0}, "bt": None, "url": "", "summary": "",
            "notes": [], "exitLong": [], "exitShort": []}
    base.update(copy.deepcopy(sp))
    return base


# --- Nettoyage d'une stratégie venue de l'IA ou d'un texte -------------------

def _clamp(v, a, b):
    return max(a, min(b, v))


def clean_operand(o) -> dict | None:
    if not isinstance(o, dict) or o.get("k") not in OPERANDS:
        return None
    q = {"k": o["k"]}
    try:
        if o["k"] in NEED_N:
            q["n"] = int(_clamp(round(float(o.get("n") or 14)), 2, 200))
        if o["k"].startswith("bb_"):
            q["m"] = float(_clamp(float(o.get("m") or 2), 0.5, 4))
        if o["k"] == "num":
            q["v"] = float(o.get("v"))
            if not ok(q["v"]):
                return None
    except (TypeError, ValueError):
        return None
    return q


def clean_cond(c) -> dict | None:
    if not isinstance(c, dict) or c.get("op") not in OPS:
        return None
    a, b = clean_operand(c.get("a")), clean_operand(c.get("b"))
    return {"a": a, "op": c["op"], "b": b} if a and b else None


def clean_list(lst) -> list[dict]:
    return [x for x in (clean_cond(c) for c in (lst if isinstance(lst, list) else [])) if x][:4]


def spec_from_raw(raw: dict) -> dict | None:
    """Convertit la réponse JSON (IA ou analyse locale) en stratégie exécutable, ou None."""
    if not isinstance(raw, dict) or raw.get("insufficient"):
        return None
    long, short = clean_list(raw.get("long")), clean_list(raw.get("short"))
    if not long and not short:
        return None
    st = raw.get("stop") or {}
    try:
        if st.get("type") == "pct" and float(st.get("value") or 0) > 0:
            stop = {"type": "pct", "value": _clamp(float(st["value"]), 0.2, 10)}
        else:
            mult = float(st.get("mult") or st.get("value") or 1.5)
            stop = {"type": "atr", "mult": _clamp(mult if mult > 0 else 1.5, 0.5, 5)}
    except (TypeError, ValueError):
        stop = {"type": "atr", "mult": 1.5}
    tp = raw.get("tp") or {}
    try:
        rr = _clamp(float(tp.get("rr") if isinstance(tp, dict) else raw.get("tp_rr") or 2), 0.8, 6)
    except (TypeError, ValueError):
        rr = 2.0
    fam = raw.get("family") if raw.get("family") in ("trend", "meanrev", "breakout") else "trend"
    return {"name": str(raw.get("name") or "Stratégie vidéo")[:32], "family": fam, "summary": str(raw.get("summary") or "")[:300],
            "long": long, "short": short, "exitLong": clean_list(raw.get("exitLong")), "exitShort": clean_list(raw.get("exitShort")),
            "stop": stop, "tp": {"rr": rr}, "assumptions": [str(x)[:160] for x in (raw.get("assumptions") or [])][:4]}


# --- Descriptions en français -------------------------------------------------

def operand_txt(o: dict) -> str:
    k = o["k"]
    return {"close": "prix", "ema": f"EMA {o.get('n')}", "sma": f"MM {o.get('n')}", "rsi": f"RSI {o.get('n')}",
            "atr": f"ATR {o.get('n')}", "bb_up": "Bollinger haute", "bb_lo": "Bollinger basse", "bb_mid": "Bollinger milieu",
            "don_hi": f"plus haut {o.get('n')} bougies", "don_lo": f"plus bas {o.get('n')} bougies", "macd": "MACD",
            "macd_sig": "signal MACD", "num": str(o.get("v")).rstrip("0").rstrip(".").replace(".", ",")}.get(k, k)


def cond_txt(c: dict) -> str:
    op = {">": "au-dessus de", "<": "sous", "cross_above": "croise au-dessus de", "cross_below": "croise sous"}[c["op"]]
    return f"{operand_txt(c['a'])} {op} {operand_txt(c['b'])}"
