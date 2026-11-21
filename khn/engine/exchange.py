"""Client Binance Spot minimal : données publiques + ordres signés sur le TESTNET uniquement."""
from __future__ import annotations

import hashlib
import hmac
import time
from dataclasses import dataclass
from decimal import ROUND_DOWN, Decimal
from urllib.parse import urlencode

import httpx

from .strategy import Candle

DATA_API = "https://data-api.binance.vision"   # données publiques du marché réel
TESTNET_API = "https://testnet.binance.vision"  # compte de démo Binance (argent fictif)


class ExchangeError(Exception):
    pass


@dataclass
class SymbolFilters:
    symbol: str
    base: str
    quote: str
    min_notional: float = 5.0
    step: float = 0.00001
    tick: float = 0.01
    min_qty: float = 0.0

    def floor_qty(self, q: float) -> float:
        st = Decimal(str(self.step))
        return float((Decimal(str(q)) / st).to_integral_value(rounding=ROUND_DOWN) * st)

    def fmt_qty(self, q: float) -> str:
        return format(Decimal(str(self.floor_qty(q))).normalize(), "f")

    def fmt_price(self, p: float) -> str:
        t = Decimal(str(self.tick))
        return format(((Decimal(str(p)) / t).to_integral_value() * t).normalize(), "f")


def sign(secret: str, query: str) -> str:
    return hmac.new(secret.encode(), query.encode(), hashlib.sha256).hexdigest()


def parse_klines(rows: list, now_ms: int) -> tuple[list[Candle], Candle | None]:
    """Sépare les bougies fermées de la bougie en cours. Format Binance : [ouverture, o, h, l, c, v, fermeture, ...]."""
    closed, cur = [], None
    for r in rows:
        c = Candle(int(r[0]), float(r[1]), float(r[2]), float(r[3]), float(r[4]))
        if int(r[6]) < now_ms:
            closed.append(c)
        else:
            cur = c
    return closed, cur


class BinanceSpot:
    def __init__(self, base: str = DATA_API, key: str = "", secret: str = "", client: httpx.AsyncClient | None = None):
        self.base = base.rstrip("/")
        self.key, self.secret = key, secret
        self.http = client or httpx.AsyncClient(timeout=15)
        self._offset = 0

    async def close(self) -> None:
        await self.http.aclose()

    async def _get(self, path: str, params: dict | None = None):
        try:
            r = await self.http.get(self.base + path, params=params)
        except httpx.HTTPError as e:
            raise ExchangeError(f"Binance injoignable ({type(e).__name__})") from e
        if r.status_code != 200:
            raise ExchangeError(f"Binance {r.status_code} sur {path} : {r.text[:200]}")
        return r.json()

    async def ping(self) -> bool:
        await self._get("/api/v3/ping")
        return True

    async def klines(self, symbol: str, interval: str = "5m", limit: int = 500) -> list:
        return await self._get("/api/v3/klines", {"symbol": symbol, "interval": interval, "limit": limit})

    async def price(self, symbol: str) -> float:
        return float((await self._get("/api/v3/ticker/price", {"symbol": symbol}))["price"])

    async def filters(self, symbols: list[str]) -> dict[str, SymbolFilters]:
        import json
        data = await self._get("/api/v3/exchangeInfo", {"symbols": json.dumps(symbols, separators=(",", ":"))})
        out = {}
        for s in data.get("symbols", []):
            f = SymbolFilters(s["symbol"], s.get("baseAsset", ""), s.get("quoteAsset", "USDT"))
            for flt in s.get("filters", []):
                t = flt.get("filterType")
                if t == "LOT_SIZE":
                    f.step, f.min_qty = float(flt["stepSize"]), float(flt["minQty"])
                elif t == "PRICE_FILTER":
                    f.tick = float(flt["tickSize"])
                elif t in ("NOTIONAL", "MIN_NOTIONAL"):
                    f.min_notional = float(flt.get("minNotional", f.min_notional))
            out[s["symbol"]] = f
        return out

    # --- Appels signés (testnet seulement) -------------------------------------
    async def _signed(self, method: str, path: str, params: dict):
        if self.base != TESTNET_API:
            raise ExchangeError("Ordres refusés : KHN n'envoie des ordres qu'au testnet Binance en phase 2.")
        if not (self.key and self.secret):
            raise ExchangeError("Clés testnet manquantes (BINANCE_TESTNET_KEY / BINANCE_TESTNET_SECRET).")
        p = dict(params, recvWindow=5000, timestamp=int(time.time() * 1000) + self._offset)
        q = urlencode(p)
        url = f"{self.base}{path}?{q}&signature={sign(self.secret, q)}"
        try:
            r = await self.http.request(method, url, headers={"X-MBX-APIKEY": self.key})
        except httpx.HTTPError as e:
            raise ExchangeError(f"Testnet injoignable ({type(e).__name__})") from e
        if r.status_code != 200:
            raise ExchangeError(f"Testnet {r.status_code} : {r.text[:200]}")
        return r.json()

    async def sync_time(self) -> None:
        t = (await self._get("/api/v3/time"))["serverTime"]
        self._offset = int(t) - int(time.time() * 1000)

    async def account(self) -> dict:
        return await self._signed("GET", "/api/v3/account", {"omitZeroBalances": "true"})

    async def oco_sell(self, f: "SymbolFilters", qty: float, tp: float, stop: float) -> dict:
        """Vend en OCO : objectif (LIMIT_MAKER au-dessus) et stop (STOP_LOSS au marché en dessous). L'un annule l'autre."""
        return await self._signed("POST", "/api/v3/orderList/oco", {
            "symbol": f.symbol, "side": "SELL", "quantity": f.fmt_qty(qty),
            "aboveType": "LIMIT_MAKER", "abovePrice": f.fmt_price(tp),
            "belowType": "STOP_LOSS", "belowStopPrice": f.fmt_price(stop), "newOrderRespType": "FULL"})

    async def order_list(self, list_id: int) -> dict:
        return await self._signed("GET", "/api/v3/orderList", {"orderListId": list_id})

    async def cancel_order_list(self, symbol: str, list_id: int) -> dict:
        return await self._signed("DELETE", "/api/v3/orderList", {"symbol": symbol, "orderListId": list_id})

    async def get_order(self, symbol: str, order_id: int) -> dict:
        return await self._signed("GET", "/api/v3/order", {"symbol": symbol, "orderId": order_id})

    async def market_order(self, symbol: str, side: str, quote_qty: float | None = None, qty: float | None = None) -> dict:
        p = {"symbol": symbol, "side": side, "type": "MARKET", "newOrderRespType": "FULL"}
        if quote_qty is not None:
            p["quoteOrderQty"] = f"{quote_qty:.2f}"
        else:
            p["quantity"] = format(Decimal(str(qty)).normalize(), "f")
        return await self._signed("POST", "/api/v3/order", p)
