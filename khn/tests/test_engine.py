"""Tests du moteur KHN (stdlib unittest). Lancer depuis khn/ : python -m unittest discover -s tests -v"""
from __future__ import annotations

import asyncio
import json
import math
import os
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.environ.pop("ANTHROPIC_API_KEY", None)
os.environ.pop("ANTHROPIC_AUTH_TOKEN", None)

import httpx  # noqa: E402

from engine import indicators as ind  # noqa: E402
from engine.backtest import eval_spec  # noqa: E402
from engine.bot import Bot, local_parse  # noqa: E402
from engine.broker import TestnetBroker  # noqa: E402
from engine.config import Config  # noqa: E402
from engine.exchange import TESTNET_API, BinanceSpot, ExchangeError, SymbolFilters, parse_klines, sign  # noqa: E402
from engine.learning import diagnose  # noqa: E402
from engine.llm import Brain  # noqa: E402
from engine.market import SimFeed  # noqa: E402
from engine.server import create_app, dumps  # noqa: E402
from engine.strategy import C, Candle, Market, builtins, cond_true, spec_from_raw  # noqa: E402
from engine.youtube import video_id  # noqa: E402


def run(coro):
    return asyncio.run(coro)


async def settle(bot, cap: int = 200):
    for _ in range(cap):
        if not bot.team.queue:
            return
        await asyncio.sleep(0.002)


def cfg(tmp: str, **kw) -> Config:
    base = dict(mode="sim", data_dir=tmp, password="secret-test", history=600, sim_candle_sec=0.05, team_speed=0.002)
    base.update(kw)
    return Config(**base)


class Indicators(unittest.TestCase):
    def test_sma_ema(self):
        a = [1, 2, 3, 4, 5]
        self.assertEqual(ind.sma(a, 3)[2:], [2, 3, 4])
        e = ind.ema(a, 3)
        self.assertTrue(math.isnan(e[1]))
        self.assertAlmostEqual(e[2], 2.0)
        self.assertAlmostEqual(e[4], 4.0)

    def test_rsi_bounds(self):
        up = [float(i) for i in range(40)]
        self.assertEqual(ind.rsi(up, 14)[-1], 100.0)
        r = ind.rsi([100 + (i % 2) for i in range(40)], 14)
        self.assertTrue(30 < r[-1] < 70)

    def test_donchian_excludes_current(self):
        h = [1, 2, 3, 10]
        self.assertEqual(ind.donchian(h, h, 3, True)[3], 3)


class Strategy(unittest.TestCase):
    def market(self, closes):
        m = Market("BTCUSDT")
        for i, c in enumerate(closes):
            m.append(Candle(i, c, c, c, c))
        return m

    def test_cross(self):
        m = self.market([1, 1, 1, 5])
        cond = {"a": {"k": "close"}, "op": "cross_above", "b": {"k": "num", "v": 3}}
        self.assertTrue(cond_true(m, cond, 3))
        self.assertFalse(cond_true(m, cond, 2))

    def test_clean_raw(self):
        raw = {"name": "x" * 50, "family": "zzz", "long": [{"a": {"k": "ema", "n": 9999}, "op": "cross_above", "b": {"k": "ema", "n": 21}},
                                                      {"a": {"k": "hack"}, "op": ">", "b": {"k": "num", "v": 1}}],
               "short": [], "stop": {"type": "pct", "value": 50}, "tp": {"rr": 99}}
        sp = spec_from_raw(raw)
        self.assertEqual(len(sp["name"]), 32)
        self.assertEqual(sp["family"], "trend")
        self.assertEqual(sp["long"][0]["a"]["n"], 200)
        self.assertEqual(len(sp["long"]), 1)
        self.assertEqual(sp["stop"], {"type": "pct", "value": 10})
        self.assertEqual(sp["tp"]["rr"], 6)
        self.assertIsNone(spec_from_raw({"insufficient": True}))
        self.assertIsNone(spec_from_raw({"long": [], "short": []}))

    def test_local_parse(self):
        sp = local_parse("J'achète quand le RSI 14 passe sous 25 puis remonte, stop 1.5 ATR, ratio 1:3")
        self.assertEqual(sp["family"], "meanrev")
        self.assertEqual(sp["long"][0]["b"]["v"], 25)
        self.assertEqual(sp["stop"], {"type": "atr", "mult": 1.5})
        self.assertEqual(sp["tp"]["rr"], 3)
        sp = local_parse("croisement de la moyenne mobile 20 et de la moyenne mobile 50")
        self.assertEqual((sp["long"][0]["a"]["n"], sp["long"][0]["b"]["n"]), (20, 50))
        sp = local_parse("Achat quand le RSI 14 croise au-dessus de 30, vente quand il passe sous 70")
        self.assertEqual((sp["long"][0]["b"]["v"], sp["short"][0]["b"]["v"]), (30, 70))
        self.assertIsNone(local_parse("j'aime le café"))

    def test_diagnose(self):
        tr = {"r": -1, "reason": "stop", "bars": 2, "mfeR": 0.2, "feat": {"htf": False, "er": 0.1, "atrP": 0.5, "stopAtr": 1.0}}
        d = diagnose(tr, {"family": "trend"})
        self.assertEqual(set(d["causes"]), {"contre", "regime_t", "stop"})
        self.assertTrue(diagnose({"r": 1.5}, None)["ok"])

    def test_backtest_runs(self):
        feed = SimFeed(["BTCUSDT", "ETHUSDT"], history=800, seed=3)
        run(feed.bootstrap())
        for sp in builtins():
            e = eval_spec(sp, list(feed.markets.values()), True)
            self.assertGreater(e["is"]["n"] + e["oos"]["n"], 0)


class Exchange(unittest.TestCase):
    def test_signature_matches_binance_docs(self):
        q = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559"
        s = sign("NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j", q)
        self.assertEqual(s, "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71")

    def test_parse_klines(self):
        now = 1_700_000_600_000
        rows = [[1_700_000_000_000, "1", "2", "0.5", "1.5", "10", 1_700_000_299_999], [1_700_000_300_000, "1.5", "3", "1", "2", "5", 1_700_000_599_999],
                [1_700_000_600_000, "2", "2", "2", "2", "1", 1_700_000_899_999]]
        closed, cur = parse_klines(rows, now)
        self.assertEqual(len(closed), 2)
        self.assertEqual(cur.c, 2.0)

    def test_floor_qty(self):
        f = SymbolFilters("BTCUSDT", "BTC", "USDT", 5, 0.00001, 0.01)
        self.assertEqual(f.floor_qty(0.000129999), 0.00012)

    def test_filters_and_mainnet_guard(self):
        info = {"symbols": [{"symbol": "BTCUSDT", "baseAsset": "BTC", "quoteAsset": "USDT", "filters": [
            {"filterType": "PRICE_FILTER", "tickSize": "0.01"}, {"filterType": "LOT_SIZE", "stepSize": "0.00001", "minQty": "0.00001"},
            {"filterType": "NOTIONAL", "minNotional": "5.00"}]}]}

        def handler(req: httpx.Request):
            return httpx.Response(200, json=info)

        async def go():
            c = BinanceSpot(client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
            f = (await c.filters(["BTCUSDT"]))["BTCUSDT"]
            self.assertEqual((f.min_notional, f.step, f.tick), (5.0, 0.00001, 0.01))
            with self.assertRaises(ExchangeError):  # jamais d'ordre hors testnet
                await c.market_order("BTCUSDT", "BUY", quote_qty=10)
        run(go())

    def test_testnet_order_signed(self):
        seen = {}

        def handler(req: httpx.Request):
            seen["url"], seen["key"] = str(req.url), req.headers.get("X-MBX-APIKEY")
            return httpx.Response(200, json={"side": "BUY", "status": "FILLED", "executedQty": "0.00010000", "cummulativeQuoteQty": "9.50",
                                             "fills": [{"commission": "0.00000010", "commissionAsset": "BTC"}]})

        async def go():
            c = BinanceSpot(TESTNET_API, "k", "s", client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
            fill = await TestnetBroker(c).open(SymbolFilters("BTCUSDT", "BTC", "USDT"), "long", 9.5, 95000)
            self.assertIn("signature=", seen["url"])
            self.assertIn("quoteOrderQty=9.50", seen["url"])
            self.assertEqual(seen["key"], "k")
            self.assertAlmostEqual(fill.price, 95000)
            self.assertAlmostEqual(fill.qty, 0.00009)
        run(go())

    def test_video_id(self):
        self.assertEqual(video_id("https://youtu.be/dQw4w9WgXcQ?t=3"), "dQw4w9WgXcQ")
        self.assertEqual(video_id("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=x"), "dQw4w9WgXcQ")
        self.assertEqual(video_id("https://www.youtube.com/shorts/dQw4w9WgXcQ"), "dQw4w9WgXcQ")
        self.assertIsNone(video_id("https://example.com"))


class Session(unittest.TestCase):
    """Une session simulée complète : signaux, ordres, risque, diagnostic, correction, commandes, mémoire."""

    def test_full_session(self):
        tmp = tempfile.mkdtemp()

        async def go():
            c = cfg(tmp)
            bot = Bot(c, feed=SimFeed(c.symbols, c.history, seed=11), brain=Brain())
            self.assertFalse(bot.brain.available)
            await bot.start()
            team = asyncio.create_task(bot.team.run())
            for _ in range(900):
                await bot.tick(bot.feed.step(1))
                await settle(bot)
                self.assertLessEqual(len(bot.positions), bot.risk["max_pos"])
            for cmd in ("statut", "risque 0.5", "strat off 1", "strat on 1", "long btc", "fermer tout", "pause", "reprendre", "backtest 2", "corriger 3", "phrase inconnue"):
                await bot.command(cmd)
            await bot.command("apprendre https://youtu.be/dQw4w9WgXcQ Entrée quand la moyenne mobile 10 croise au-dessus de la moyenne mobile 30, stop 2 ATR, ratio 1:2")
            for _ in range(150):
                await bot.tick(bot.feed.step(1))
                await settle(bot)
            await bot.command("stop")
            team.cancel()
            return bot

        bot = run(go())
        self.assertTrue(math.isfinite(bot.equity()))
        self.assertGreater(len(bot.journal), 5, "la session doit produire des trades")
        self.assertTrue(any(t["diag"] for t in bot.journal), "les pertes doivent être analysées")
        self.assertEqual(bot.risk["per_trade"], 0.5)
        self.assertTrue(bot.flags["killed"])
        self.assertEqual(bot.positions, [])
        video = [s for s in bot.strategies if s.get("url")]
        self.assertEqual(len(video), 1)
        self.assertIn(video[0]["status"], ("active", "rejetee"))
        self.assertEqual(video[0]["long"][0]["a"]["n"], 10)
        texts = " ".join(e["text"] for e in bot.logs)
        self.assertIn("Commande inconnue", texts)
        json.loads(dumps(bot.snapshot()))  # JSON valide pour le navigateur
        # la mémoire survit à un redémarrage
        bot.save()
        bot2 = Bot(cfg(tmp), feed=SimFeed(["BTCUSDT", "ETHUSDT", "SOLUSDT"], 600, seed=2), brain=Brain())
        run(bot2.start())
        self.assertEqual(bot2.risk["per_trade"], 0.5)
        self.assertEqual(len(bot2.strategies), len(bot.strategies))
        self.assertTrue(bot2.flags["killed"])

    def test_min_notional_sizing(self):
        tmp = tempfile.mkdtemp()

        async def go():
            c = cfg(tmp, capital_xpf=1000)
            bot = Bot(c, feed=SimFeed(c.symbols, 300, seed=5), brain=Brain())
            await bot.start()
            return bot

        bot = run(go())
        eq = bot.equity()
        self.assertAlmostEqual(eq * bot.rate(), 1000, delta=1)
        n, note = bot.size("BTCUSDT", 100.0, 1.0)  # stop à 1 % : 1 % de risque → 9 USDT > capital → plafonné
        self.assertLessEqual(n, eq)
        n, note = bot.size("BTCUSDT", 100.0, 10.0)  # stop à 10 % : taille < 5 USDT et risque trop grand → refus
        self.assertIsNone(n)
        self.assertIn("minimum", note)


class Server(unittest.TestCase):
    def test_auth_api_ws(self):
        from fastapi.testclient import TestClient

        tmp = tempfile.mkdtemp()
        c = cfg(tmp)
        bot = Bot(c, feed=SimFeed(c.symbols, 400, candle_sec=0.05, seed=1), brain=Brain())
        app = create_app(c, bot)
        with TestClient(app) as cl:
            t0 = time.time()
            while not bot.ready and time.time() - t0 < 20:
                time.sleep(0.05)
            self.assertTrue(bot.ready)
            self.assertEqual(cl.get("/api/state").status_code, 401)
            self.assertEqual(cl.post("/login", data={"password": "faux"}, follow_redirects=False).status_code, 401)
            r = cl.post("/login", data={"password": "secret-test"}, follow_redirects=False)
            self.assertEqual(r.status_code, 303)
            st = cl.get("/api/state").json()
            self.assertEqual(st["mode"], "sim")
            self.assertIn("BTC", st["markets"])
            self.assertEqual(cl.post("/api/command", json={"text": "statut"}).json(), {"ok": True})
            self.assertTrue(cl.post("/api/risk", json={"per_trade": 0.75, "max_pos": 9}).json()["risk"]["max_pos"] == 3)
            with cl.websocket_connect("/ws") as ws:
                first = json.loads(ws.receive_text())
                self.assertEqual(first["type"], "state")
                self.assertTrue(first["logs"])
            self.assertIn("KHN", cl.get("/").text)


if __name__ == "__main__":
    unittest.main()
