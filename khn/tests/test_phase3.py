"""Tests de la phase 3 : protections chez l'exchange, Kraken (simulé), verrou du réel, validation des ordres."""
from __future__ import annotations

import asyncio
import os
import sys
import tempfile
import time
import unittest
from urllib.parse import parse_qs

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.environ.pop("ANTHROPIC_API_KEY", None)

import httpx  # noqa: E402

from engine import gate as gatemod  # noqa: E402
from engine.bot import Bot  # noqa: E402
from engine.broker import KrakenBroker, TestnetBroker  # noqa: E402
from engine.config import Config  # noqa: E402
from engine.exchange import TESTNET_API, BinanceSpot, ExchangeError, SymbolFilters  # noqa: E402
from engine.kraken import KrakenSpot, kraken_sign  # noqa: E402
from engine.llm import Brain  # noqa: E402
from engine.market import SimFeed  # noqa: E402
from engine.store import Store  # noqa: E402

PAIR_INFO = {"XBTUSDT": {"altname": "XBTUSDT", "base": "XXBT", "quote": "USDT", "ordermin": "0.00005", "costmin": "0.5",
                         "lot_decimals": 8, "pair_decimals": 1, "tick_size": "0.1"}}
SECRET = "kQH5HW/8p1uGOVjbgWA7FunAmGO8lsSUXNsu3eow76sz84Q18fWxnyRzBHCd3pd5nE9qa99HAZtuZuj6F1huXg=="


def run(c):
    return asyncio.run(c)


class FakeKraken:
    """Réponses au format Kraken {error, result}. Enregistre chaque appel privé."""

    def __init__(self, stop_ok=True, withdraw_err="EGeneral:Permission denied"):
        self.calls, self.stop_ok, self.withdraw_err = [], stop_ok, withdraw_err
        self.orders = {}

    def __call__(self, req: httpx.Request):
        path = req.url.path
        if path.endswith("/AssetPairs"):
            return httpx.Response(200, json={"error": [], "result": PAIR_INFO})
        form = {k: v[0] for k, v in parse_qs(req.content.decode()).items()}
        self.calls.append((path.rsplit("/", 1)[-1], form, dict(req.headers)))
        m = path.rsplit("/", 1)[-1]
        if m == "Balance":
            return httpx.Response(200, json={"error": [], "result": {"USDT": "1000.0", "XXBT": "0.01"}})
        if m == "WithdrawInfo":
            return httpx.Response(200, json={"error": [self.withdraw_err], "result": {}})
        if m == "AddOrder":
            if form.get("validate") == "true":
                return httpx.Response(200, json={"error": [], "result": {"descr": {"order": "validé"}}})
            if form["ordertype"] == "stop-loss" and not self.stop_ok:
                return httpx.Response(200, json={"error": ["EOrder:Invalid price"], "result": {}})
            txid = f"O{len(self.orders) + 1}"
            vol = float(form["volume"])
            status = "open" if form["ordertype"] == "stop-loss" else "closed"
            self.orders[txid] = {"status": status, "vol_exec": str(vol if status == "closed" else 0), "cost": str(vol * 95000 if status == "closed" else 0), "fee": "0.04"}
            return httpx.Response(200, json={"error": [], "result": {"txid": [txid], "descr": {}}})
        if m == "QueryOrders":
            return httpx.Response(200, json={"error": [], "result": {form["txid"]: self.orders[form["txid"]]}})
        if m == "CancelOrder":
            self.orders[form["txid"]]["status"] = "canceled"
            return httpx.Response(200, json={"error": [], "result": {"count": 1}})
        return httpx.Response(404, json={"error": ["EGeneral:Unknown method"]})


def kraken(fake, dry=False):
    k = KrakenSpot("cle", SECRET, client=httpx.AsyncClient(transport=httpx.MockTransport(fake)))
    return KrakenBroker(k, dry=lambda: dry)


F = SymbolFilters("BTCUSDT", "BTC", "USDT", 5, 0.00001, 0.01)


class Kraken(unittest.TestCase):
    def test_signature_matches_kraken_docs(self):
        sig = kraken_sign(SECRET, "/0/private/AddOrder", "1616492376594",
                          "nonce=1616492376594&ordertype=limit&pair=XBTUSD&price=37500&type=buy&volume=1.25")
        self.assertEqual(sig, "4/dpxb3iT4tp/ZCVEwSnEsLxx0bqyhLpdfOpc6fn7OR8+UClSV5n9E6aSS8MPtnRfp32bAb0nmbRn6H8ndwLUQ==")

    def test_dry_run_sends_validate_only(self):
        fake = FakeKraken()
        b = kraken(fake, dry=True)

        async def go():
            await b.prepare(["BTCUSDT"])
            return await b.open(F, "long", 10.0, 95000.0, 94000.0, 97000.0)
        fill = run(go())
        self.assertTrue(fill.dry)
        adds = [c for c in fake.calls if c[0] == "AddOrder"]
        self.assertEqual(len(adds), 2)
        self.assertTrue(all(c[1].get("validate") == "true" for c in adds), "aucun ordre réel en essai à blanc")
        self.assertEqual(adds[1][1]["ordertype"], "stop-loss")
        self.assertEqual(adds[1][1]["price"], "94000.0")
        self.assertIn("api-sign", adds[0][2])

    def test_live_open_places_stop_and_sync_detects_it(self):
        fake = FakeKraken()
        b = kraken(fake)

        async def go():
            await b.prepare(["BTCUSDT"])
            fill = await b.open(F, "long", 10.0, 95000.0, 94000.0, 97000.0)
            pos = {"ref": fill.ref}
            self.assertIsNone(await b.sync(F, pos))  # stop encore ouvert
            fake.orders[fill.ref["stop"]].update(status="closed", vol_exec=str(fill.qty), cost=str(fill.qty * 94000))
            return fill, await b.sync(F, pos)
        fill, res = run(go())
        self.assertEqual(fill.ref["stop"], "O2")
        self.assertEqual(fill.qty, 0.00010526)
        self.assertEqual(res[1], "stop")
        self.assertAlmostEqual(res[0].price, 94000)
        buy = [c for c in fake.calls if c[0] == "AddOrder"][0][1]
        self.assertEqual((buy["type"], buy["ordertype"], buy["oflags"]), ("buy", "market", "fciq"))

    def test_no_unprotected_position(self):
        fake = FakeKraken(stop_ok=False)
        b = kraken(fake)

        async def go():
            await b.prepare(["BTCUSDT"])
            await b.open(F, "long", 10.0, 95000.0, 94000.0, 97000.0)
        with self.assertRaises(ExchangeError) as e:
            run(go())
        self.assertIn("revendue", str(e.exception))
        sells = [c for c in fake.calls if c[0] == "AddOrder" and c[1]["type"] == "sell" and c[1]["ordertype"] == "market"]
        self.assertEqual(len(sells), 1)

    def test_too_small_order_refused(self):
        b = kraken(FakeKraken(), dry=True)

        async def go():
            await b.prepare(["BTCUSDT"])
            await b.open(F, "long", 0.4, 95000.0, 94000.0, 97000.0)
        with self.assertRaises(ExchangeError):
            run(go())

    def test_withdraw_permission(self):
        async def check(err):
            return await KrakenSpot("k", SECRET, client=httpx.AsyncClient(transport=httpx.MockTransport(FakeKraken(withdraw_err=err)))).can_withdraw()
        self.assertIs(run(check("EGeneral:Permission denied")), False)
        self.assertIs(run(check("EFunding:Unknown withdraw key")), True)
        self.assertIsNone(run(check("EGeneral:Internal error")))


class BinanceOCO(unittest.TestCase):
    def test_oco_placed_and_tp_detected(self):
        seen = []

        def handler(req: httpx.Request):
            seen.append((req.method, req.url.path, dict(req.url.params)))
            p = req.url.path
            if p == "/api/v3/order" and req.method == "POST":
                return httpx.Response(200, json={"side": "BUY", "status": "FILLED", "executedQty": "0.00010000", "cummulativeQuoteQty": "9.50", "fills": []})
            if p == "/api/v3/orderList/oco":
                return httpx.Response(200, json={"orderListId": 7, "orders": [{"orderId": 11}, {"orderId": 12}]})
            if p == "/api/v3/orderList":
                return httpx.Response(200, json={"orderListId": 7, "listOrderStatus": "ALL_DONE", "listStatusType": "ALL_DONE"})
            if p == "/api/v3/order" and req.method == "GET":
                oid = req.url.params["orderId"]
                if oid == "11":
                    return httpx.Response(200, json={"status": "EXPIRED", "type": "STOP_LOSS", "executedQty": "0", "cummulativeQuoteQty": "0"})
                return httpx.Response(200, json={"status": "FILLED", "type": "LIMIT_MAKER", "executedQty": "0.0001", "cummulativeQuoteQty": "9.7"})
            return httpx.Response(404)

        async def go():
            c = BinanceSpot(TESTNET_API, "k", "s", client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
            b = TestnetBroker(c)
            fill = await b.open(F, "long", 9.5, 95000, 94000.123, 97000.456)
            return fill, await b.sync(F, {"ref": fill.ref})
        fill, res = run(go())
        oco = next(s for s in seen if s[1] == "/api/v3/orderList/oco")[2]
        self.assertEqual((oco["aboveType"], oco["belowType"], oco["abovePrice"], oco["belowStopPrice"]), ("LIMIT_MAKER", "STOP_LOSS", "97000.46", "94000.12"))
        self.assertEqual(fill.ref["list"], 7)
        self.assertEqual(res[1], "tp")
        self.assertAlmostEqual(res[0].price, 97000)


class Gate(unittest.TestCase):
    def test_rules(self):
        empty = {"days": 0, "n": 0, "expR": 0.0, "pf": 0.0, "dd": 0.0, "max_dd": 10.0}
        self.assertFalse(gatemod.ready(gatemod.evaluate(empty, 1000, 60000, None, False)))
        good = {"days": 75, "n": 80, "expR": 0.2, "pf": 1.4, "dd": 0.06, "max_dd": 10.0}
        c = gatemod.evaluate(good, 1000, 60000, None, False)
        self.assertEqual([x["key"] for x in c if x["ok"] is False], ["capital", "armed"])
        live = {"keys": True, "withdraw": False, "dryrun": True}
        self.assertTrue(gatemod.ready(gatemod.evaluate(good, 100000, 60000, live, True)))
        self.assertFalse(gatemod.ready(gatemod.evaluate(good, 100000, 60000, {**live, "withdraw": None}, True)), "retrait inconnu = bloqué")

    def test_demo_stats_from_store(self):
        st = Store(os.path.join(tempfile.mkdtemp(), "khn-paper.sqlite"))
        now = time.time()
        st.put_many({"acct": {"created": now - 70 * 86400}, "risk": {"max_dd": 10},
                     "journal": [{"r": 0.5 if i % 2 else -0.3, "closed": (now - i * 3600) * 1000} for i in range(60)] + [{"r": 5, "manual": True}],
                     "eq": [100, 104, 99, 108]})
        d = gatemod.demo_stats(st)
        self.assertEqual(d["n"], 60)
        self.assertGreater(d["days"], 69)
        self.assertAlmostEqual(d["expR"], 0.1, places=6)
        self.assertAlmostEqual(d["dd"], 5 / 104, places=6)


def sim_bot(tmp, **kw) -> Bot:
    c = Config(mode="sim", data_dir=tmp, password="x", history=400, team_speed=0.002, **kw)
    return Bot(c, feed=SimFeed(c.symbols, 400, seed=4), brain=Brain())


class Confirmation(unittest.TestCase):
    def test_propose_accept_refuse_expire(self):
        bot = sim_bot(tempfile.mkdtemp())

        async def go():
            await bot.start()
            bot.risk["confirm"] = True
            sp = bot.strategies[0]
            p1 = bot.propose(sp, "BTCUSDT", "long", "KHN")
            self.assertIsNotNone(p1)
            self.assertIsNone(bot.propose(sp, "BTCUSDT", "long", "KHN"), "une seule proposition par marché")
            await bot.command(f"valider {p1['id']}")
            self.assertEqual(len(bot.positions), 1)
            p2 = bot.propose(sp, "ETHUSDT", "long", "KHN")
            await bot.confirm(p2["id"], False)
            p3 = bot.propose(sp, "SOLUSDT", "long", "KHN")
            p3["expires"] = 0
            await bot.check_prices()
            self.assertEqual(bot.pending, [])
            self.assertEqual(len(bot.positions), 1)
        run(go())

    def test_order_task_proposes_when_confirm_on(self):
        bot = sim_bot(tempfile.mkdtemp())

        async def go():
            await bot.start()
            bot.risk["confirm"] = True
            m = bot.markets["BTCUSDT"]
            t = bot.team.push("order", {"k": "BTC", "sym": "BTCUSDT", "side": "long", "sid": bot.strategies[0]["id"], "t": m.candles[-1].t})
            await bot._handle(t, bot.team.agents[0])
            self.assertEqual(len(bot.pending), 1)
            self.assertEqual(bot.positions, [])
        run(go())

    def test_weekly_cap(self):
        bot = sim_bot(tempfile.mkdtemp())

        async def go():
            await bot.start()
            bot.acct["week_start"] = bot.equity() / 0.92  # −8 % sur la semaine
            bot.risk_monitor()
            self.assertEqual(bot.gate("BTCUSDT"), "perte max de la semaine atteinte")
        run(go())


class LiveBot(unittest.TestCase):
    def test_live_stays_dry_until_gate_passes(self):
        tmp = tempfile.mkdtemp()
        fake = FakeKraken()
        c = Config(mode="live", data_dir=tmp, password="x", history=400, team_speed=0.002, kraken_key="cle", kraken_secret=SECRET, live_min_xpf=60000)
        bot = Bot(c, feed=SimFeed(c.symbols, 400, seed=9), brain=Brain(), broker=kraken(fake))
        bot.broker.dry = lambda: not bot.live_armed()

        async def go():
            await bot.start()
            self.assertTrue(bot.live["keys"])
            self.assertIs(bot.live["withdraw"], False)
            self.assertTrue(bot.live["dryrun"])
            self.assertEqual(bot.risk["per_trade"], 0.5)
            self.assertTrue(bot.risk["confirm"])
            ok, msg = await bot.arm("je risque mon argent")
            self.assertFalse(ok)
            self.assertIn("jours de démo", msg)
            p = await bot.open_position(bot.strategies[0], "BTCUSDT", "long", "KHN")
            return p
        p = run(go())
        self.assertTrue(p["ref"]["dry"])
        real = [x for x in fake.calls if x[0] == "AddOrder" and x[1].get("validate") != "true"]
        self.assertEqual(real, [], "aucun ordre réel tant que le mode réel n'est pas armé")
        labels = {g["key"]: g["ok"] for g in bot.snapshot()["gate"]}
        self.assertTrue(labels["keys"] and labels["withdraw"] and labels["dryrun"])
        self.assertFalse(labels["days"])


if __name__ == "__main__":
    unittest.main()
