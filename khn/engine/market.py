"""Flux de prix : Binance (réel) ou marché synthétique (mode sim et tests)."""
from __future__ import annotations

import math
import random
import time

from .exchange import DATA_API, BinanceSpot, ExchangeError, SymbolFilters, parse_klines
from .strategy import Candle, Market

XPF_PER_EUR = 119.33  # parité fixe du franc CFP
INTERVAL_MS = 5 * 60 * 1000


class BinanceFeed:
    """Bougies 5 min réelles. En mode testnet, prix du testnet (cohérents avec les exécutions)."""

    def __init__(self, client: BinanceSpot, symbols: list[str], history: int = 1000):
        self.client = client
        self.rate_client = client if client.base == DATA_API else BinanceSpot(DATA_API)
        self.markets = {s: Market(s) for s in symbols}
        self.history = history
        self.filters: dict[str, SymbolFilters] = {}
        self.xpf_per_usdt = 110.0
        self._rate_t = 0.0
        self.connected = False

    async def bootstrap(self) -> None:
        self.filters = await self.client.filters(list(self.markets))
        now = int(time.time() * 1000)
        for s, m in self.markets.items():
            closed, cur = parse_klines(await self.client.klines(s, "5m", min(1000, self.history)), now)
            m.candles = closed
            m.cur = cur or (closed[-1] if closed else None)
            m.price = m.cur.c if m.cur else 0.0
        await self._rate()
        self.connected = True

    async def _rate(self) -> None:
        try:
            eur_usdt = await self.rate_client.price("EURUSDT")
            if eur_usdt > 0:
                self.xpf_per_usdt = XPF_PER_EUR / eur_usdt
        except ExchangeError:
            pass
        self._rate_t = time.time()

    async def poll(self) -> dict[str, list[Candle]]:
        now = int(time.time() * 1000)
        new: dict[str, list[Candle]] = {}
        try:
            for s, m in self.markets.items():
                last_t = m.candles[-1].t if m.candles else 0
                gap = max(3, min(1000, (now - last_t) // INTERVAL_MS + 2)) if last_t else 3
                closed, cur = parse_klines(await self.client.klines(s, "5m", int(gap)), now)
                fresh = [c for c in closed if c.t > last_t]
                for c in fresh:
                    m.append(c)
                if cur:
                    m.cur, m.price = cur, cur.c
                new[s] = fresh
            if time.time() - self._rate_t > 600:
                await self._rate()
            self.connected = True
        except ExchangeError:
            self.connected = False
            raise
        return new


class SimFeed:
    """Marché synthétique à régimes (tendance, range, chaos), comme le prototype. Pour tester sans réseau."""

    P0 = {"BTCUSDT": 96000.0, "ETHUSDT": 3400.0, "SOLUSDT": 190.0}
    VOL = {"BTCUSDT": 0.0011, "ETHUSDT": 0.0014, "SOLUSDT": 0.0019}

    def __init__(self, symbols: list[str], history: int = 1000, candle_sec: float = 3.0, seed: int | None = None):
        self.markets = {s: Market(s) for s in symbols}
        self.history, self.candle_sec = history, candle_sec
        self.rng = random.Random(seed)
        self.filters = {s: SymbolFilters(s, s.replace("USDT", ""), "USDT", 5.0, 0.00001, 0.01) for s in symbols}
        self.xpf_per_usdt = 110.0
        self.connected = True
        self._st: dict[str, dict] = {}
        self._t = int(time.time() * 1000) // INTERVAL_MS * INTERVAL_MS - history * INTERVAL_MS
        self._last = time.monotonic()

    def _regime(self, st: dict, price: float) -> None:
        r = self.rng.random()
        st["reg"] = "up" if r < .27 else "down" if r < .54 else "range" if r < .88 else "chaos"
        st["left"] = self.rng.randint(10, 35) if st["reg"] == "chaos" else self.rng.randint(30, 100)
        st["center"] = price

    def _candle(self, s: str) -> Candle:
        m, st = self.markets[s], self._st[s]
        vol0, p = self.VOL.get(s, 0.0015), m.price
        o = h = l = p
        for _ in range(6):
            vol = vol0 * st["vk"]
            drift = 0.0
            if st["reg"] == "up":
                drift = vol0 * 0.12
            elif st["reg"] == "down":
                drift = -vol0 * 0.12
            elif st["reg"] == "range":
                drift, vol = -0.035 * math.log(p / st["center"]), vol * 0.8
            else:
                vol *= 2.3
            st["vk"] = min(3, max(0.5, st["vk"] * 0.985 + 0.015 * (0.7 + 0.6 * self.rng.random()) + (0.7 if self.rng.random() < 0.003 else 0)))
            ret = drift + vol * self.rng.gauss(0, 1)
            if self.rng.random() < 0.0012:
                ret += (1 if self.rng.random() < .5 else -1) * vol * 6
            p *= math.exp(ret)
            h, l = max(h, p), min(l, p)
        st["left"] -= 1
        if st["left"] <= 0:
            self._regime(st, p)
        m.price = p
        return Candle(self._t, o, h, l, p)

    def _step(self) -> dict[str, list[Candle]]:
        out = {}
        for s, m in self.markets.items():
            c = self._candle(s)
            m.append(c)
            m.cur = Candle(self._t + INTERVAL_MS, m.price, m.price, m.price, m.price)
            out[s] = [c]
        self._t += INTERVAL_MS
        return out

    async def bootstrap(self) -> None:
        for s, m in self.markets.items():
            m.price = self.P0.get(s, 100.0)
            self._st[s] = {"vk": 1.0}
            self._regime(self._st[s], m.price)
        for _ in range(self.history):
            self._step()
        self._last = time.monotonic()

    def step(self, n: int = 1) -> dict[str, list[Candle]]:
        out: dict[str, list[Candle]] = {s: [] for s in self.markets}
        for _ in range(n):
            for s, cs in self._step().items():
                out[s] += cs
        return out

    async def poll(self) -> dict[str, list[Candle]]:
        n = int((time.monotonic() - self._last) / self.candle_sec)
        if n <= 0:
            for m in self.markets.values():  # petite agitation du prix entre deux bougies
                m.price *= math.exp(self.VOL.get(m.symbol, 0.0015) * 0.3 * self.rng.gauss(0, 1))
                if m.cur:
                    m.cur = Candle(m.cur.t, m.cur.o, max(m.cur.h, m.price), min(m.cur.l, m.price), m.price)
            return {s: [] for s in self.markets}
        self._last += n * self.candle_sec
        return self.step(min(n, 50))
