"""Diagnostic des pertes et auto-correction validée hors échantillon."""
from __future__ import annotations

import copy

from .backtest import eval_spec

CAUSE_TXT = {
    "contre": "Entrée contre la tendance de fond (prix du mauvais côté de l'EMA 100)",
    "regime_t": "Stratégie de tendance utilisée dans un marché sans direction",
    "regime_m": "Retour à la moyenne tenté pendant une forte tendance",
    "vol": "Volatilité extrême au moment de l'entrée",
    "sortie": "Trade gagnant qui s'est retourné : sortie trop tardive",
    "stop": "Stop trop serré, touché par le bruit du marché",
}


def diagnose(tr: dict, sp: dict | None) -> dict:
    if tr["r"] > 0:
        return {"ok": True, "causes": [], "text": "Gain conforme au plan" if tr["r"] >= 1 else "Petit gain, sortie anticipée"}
    fam = (sp or {}).get("family", "trend")
    f = tr.get("feat") or {}
    c: list[str] = []
    if f.get("htf") is False:
        c.append("contre")
    if fam != "meanrev" and f.get("er", 1) < 0.22:
        c.append("regime_t")
    if fam == "meanrev" and f.get("er", 0) > 0.45:
        c.append("regime_m")
    if f.get("atrP", 0) > 0.88:
        c.append("vol")
    if tr.get("mfeR", 0) >= 1.1:
        c.append("sortie")
    if tr.get("reason") == "stop" and f.get("stopAtr", 9) < 1.3 and tr.get("bars", 99) <= 4:
        c.append("stop")
    if not c:
        return {"ok": False, "causes": [], "text": "Perte normale : règles respectées, aucune erreur détectée"}
    return {"ok": False, "causes": c, "text": " · ".join(CAUSE_TXT[x] for x in c)}


def _fmt(x: float) -> str:
    return f"{x:.1f}".replace(".", ",")


def _fix(cause: str, s: dict) -> str | None:
    f = s.setdefault("filters", {})
    if cause == "contre":
        if f.get("htfAlign"):
            return None
        f["htfAlign"] = True
        return "Filtre ajouté : ne trader que dans le sens de l'EMA 100"
    if cause == "regime_t":
        if f.get("minTrend") is not None:
            return None
        f["minTrend"] = 0.25
        return "Filtre ajouté : force de tendance minimale 0,25"
    if cause == "regime_m":
        if f.get("maxTrend") is not None:
            return None
        f["maxTrend"] = 0.4
        return "Filtre ajouté : pas de retour à la moyenne si la tendance est forte (> 0,40)"
    if cause == "vol":
        if f.get("maxVolPct") is not None:
            return None
        f["maxVolPct"] = 0.85
        return "Filtre ajouté : pas d'entrée quand la volatilité dépasse le 85e centile"
    if cause == "sortie":
        if s["tp"]["rr"] <= 1.2:
            return None
        s["tp"]["rr"] = max(1.2, round(s["tp"]["rr"] - 0.5, 1))
        return f"Objectif rapproché à {_fmt(s['tp']['rr'])} R"
    if cause == "stop":
        if s["stop"]["type"] == "atr":
            if s["stop"]["mult"] >= 3:
                return None
            s["stop"]["mult"] = round(s["stop"]["mult"] + 0.5, 1)
            return f"Stop élargi à {_fmt(s['stop']['mult'])} × ATR"
        s["stop"]["value"] = round(s["stop"]["value"] * 1.3, 2)
        return f"Stop élargi à {s['stop']['value']} %"
    return None


def auto_correct(sp: dict, journal: list[dict], markets, allow_short: bool) -> dict:
    """Teste une correction par cause d'erreur fréquente. N'adopte que si le test hors échantillon progresse.

    Renvoie {"adopted": bool, "message": str, "before": float, "after": float, "cause": str, "suspend": bool}.
    """
    losses = [t for t in journal if t.get("sid") == sp["id"] and t.get("diag") and not t["diag"]["ok"]][:12]
    cnt: dict[str, int] = {}
    for t in losses:
        for c in t["diag"]["causes"]:
            cnt[c] = cnt.get(c, 0) + 1
    base = eval_spec(sp, markets, allow_short)
    sp["bt"] = base
    cands = [c for c, n in sorted(cnt.items(), key=lambda x: -x[1]) if n >= 2 and n >= len(losses) * 0.3] or ["stop", "sortie"]
    best = None
    for cause in cands:
        cl = copy.deepcopy(sp)
        msg = _fix(cause, cl)
        if not msg:
            continue
        e = eval_spec(cl, markets, allow_short)
        if e["oos"]["n"] >= 8 and e["is"]["expR"] > 0 and e["oos"]["expR"] > base["oos"]["expR"] + 0.04 and (best is None or e["oos"]["expR"] > best[1]["oos"]["expR"]):
            best = (cl, e, msg, cause)
    out = {"adopted": False, "message": "", "before": base["oos"]["expR"], "after": base["oos"]["expR"], "cause": "", "suspend": False}
    if best:
        cl, e, msg, cause = best
        sp.update({"filters": cl["filters"], "stop": cl["stop"], "tp": cl["tp"], "bt": e, "version": sp.get("version", 1) + 1})
        out.update(adopted=True, message=msg, after=e["oos"]["expR"], cause=CAUSE_TXT.get(cause, ""))
    live = sp.get("live") or {}
    if live.get("n", 0) >= 12 and live["sumR"] / live["n"] < -0.15 and sp["bt"]["oos"]["expR"] < 0 and sp.get("status") == "active":
        sp["status"] = "suspendue"
        out["suspend"] = True
    return out
