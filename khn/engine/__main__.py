"""Point d'entrée : python -m engine [serve|check|backtest]."""
from __future__ import annotations

import argparse
import asyncio
import sys

from .config import Config


async def check(cfg: Config, video: str | None) -> int:
    from .exchange import DATA_API, TESTNET_API, BinanceSpot, ExchangeError
    from .llm import Brain, BrainError

    bad = 0

    def line(ok: bool, label: str, detail: str = "") -> None:
        nonlocal bad
        bad += 0 if ok else 1
        print(f"  {'OK ' if ok else 'ÉCHEC'}  {label}{' : ' + detail if detail else ''}")

    print(f"Vérification de KHN (mode {cfg.mode})")
    data = BinanceSpot(DATA_API)
    try:
        rows = await data.klines(cfg.symbols[0], "5m", 2)
        line(True, "Prix réels Binance", f"{cfg.symbols[0]} = {float(rows[-1][4]):,.2f} USDT")
    except ExchangeError as e:
        line(False, "Prix réels Binance", str(e))
    finally:
        await data.close()
    if cfg.mode == "testnet" or cfg.testnet_key:
        tn = BinanceSpot(TESTNET_API, cfg.testnet_key, cfg.testnet_secret)
        try:
            await tn.sync_time()
            acc = await tn.account()
            usdt = next((b["free"] for b in acc.get("balances", []) if b["asset"] == "USDT"), "0")
            line(True, "Testnet Binance (clés)", f"solde fictif {float(usdt):,.2f} USDT")
        except ExchangeError as e:
            line(False, "Testnet Binance (clés)", str(e))
        finally:
            await tn.close()
    brain = Brain(cfg.model)
    try:
        line(True, "API Claude", f"modèle {await brain.check()}")
    except BrainError as e:
        line(cfg.mode == "sim", "API Claude", f"{e} (optionnel : sans clé, analyse des vidéos par mots-clés)")
    if video:
        from .youtube import transcript
        text, err = await transcript(video)
        line(bool(text), "Transcription YouTube", f"{len(text)} caractères" if text else err)
    print("Tout est prêt." if not bad else f"{bad} point(s) à corriger.")
    return 1 if bad else 0


async def backtest(cfg: Config) -> None:
    from .backtest import eval_spec
    from .bot import make_feed
    from .strategy import builtins

    feed = make_feed(cfg)
    await feed.bootstrap()
    ms = list(feed.markets.values())
    print(f"Backtest sur {min(len(m.candles) for m in ms)} bougies de 5 min ({', '.join(m.key for m in ms)})")
    for sp in builtins():
        e = eval_spec(sp, ms, cfg.mode != "testnet")
        print(f"  {sp['name']:<24} historique {e['is']['n']:>3} trades {e['is']['expR']:+.2f} R · hors échantillon {e['oos']['n']:>3} trades {e['oos']['expR']:+.2f} R")


def main() -> None:
    ap = argparse.ArgumentParser(prog="python -m engine", description="KHN, bot de trading crypto en démo")
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("serve", help="lancer le bot et l'interface (par défaut)")
    c = sub.add_parser("check", help="vérifier Binance, les clés testnet, Claude et YouTube")
    c.add_argument("--video", help="lien YouTube pour tester la transcription")
    sub.add_parser("backtest", help="tester les stratégies de base sur l'historique")
    args = ap.parse_args()
    cfg = Config.from_env()
    if args.cmd == "check":
        sys.exit(asyncio.run(check(cfg, args.video)))
    if args.cmd == "backtest":
        asyncio.run(backtest(cfg))
        return
    import uvicorn

    from .server import create_app

    uvicorn.run(create_app(cfg), host=cfg.host, port=cfg.port, proxy_headers=True, forwarded_allow_ips="*", log_level="warning")


if __name__ == "__main__":
    main()
