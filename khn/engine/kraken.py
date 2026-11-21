"""Client Kraken Spot (argent réel). Ordres au marché, stop-loss posé chez Kraken, validation à blanc.

Les champs de réponse sont lus de façon défensive : une réponse inattendue lève une erreur au lieu d'être devinée.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import time
from dataclasses import dataclass
from decimal import ROUND_DOWN, Decimal
from urllib.parse import urlencode

import httpx

from .exchange import ExchangeError

API = "https://api.kraken.com"
PAIRS = {"BTCUSDT": "XBTUSDT", "ETHUSDT": "ETHUSDT", "SOLUSDT": "SOLUSDT"}  # paires Kraken correspondantes


def kraken_sign(secret_b64: str, path: str, nonce: str, postdata: str) -> str:
    """API-Sign = base64(HMAC-SHA512(base64decode(secret), chemin + SHA256(nonce + données POST)))."""
    sha = hashlib.sha256((nonce + postdata).encode()).digest()
    mac = hmac.new(base64.b64decode(secret_b64), path.encode() + sha, hashlib.sha512)
    return base64.b64encode(mac.digest()).decode()


@dataclass
class KrakenPair:
    symbol: str      # BTCUSDT
    name: str        # nom Kraken utilisé dans les ordres
    base: str        # code de l'actif de base dans les soldes (ex. XXBT)
    quote: str
    ordermin: float
    costmin: float
    lot_decimals: int
    pair_decimals: int
    tick: float

    def floor_volume(self, v: float) -> float:
        q = Decimal(1).scaleb(-self.lot_decimals)
        return float(Decimal(str(v)).quantize(q, rounding=ROUND_DOWN))

    def fmt_volume(self, v: float) -> str:
        return f"{v:.{self.lot_decimals}f}"

    def fmt_price(self, p: float) -> str:
        if self.tick > 0:
            p = round(p / self.tick) * self.tick
        return f"{p:.{self.pair_decimals}f}"


class KrakenSpot:
    def __init__(self, key: str = "", secret: str = "", client: httpx.AsyncClient | None = None, base: str = API):
        self.key, self.secret, self.base = key, secret, base.rstrip("/")
        self.http = client or httpx.AsyncClient(timeout=20)
        self._nonce = 0

    async def close(self) -> None:
        await self.http.aclose()

    @staticmethod
    def _result(r: httpx.Response):
        if r.status_code != 200:
            raise ExchangeError(f"Kraken {r.status_code} : {r.text[:200]}")
        data = r.json()
        if data.get("error"):
            raise ExchangeError("Kraken : " + ", ".join(data["error"]))
        if "result" not in data:
            raise ExchangeError("Kraken : réponse sans résultat")
        return data["result"]

    async def public(self, method: str, params: dict | None = None):
        try:
            r = await self.http.get(f"{self.base}/0/public/{method}", params=params)
        except httpx.HTTPError as e:
            raise ExchangeError(f"Kraken injoignable ({type(e).__name__})") from e
        return self._result(r)

    async def private(self, method: str, data: dict | None = None):
        if not (self.key and self.secret):
            raise ExchangeError("clés Kraken manquantes (KRAKEN_KEY / KRAKEN_SECRET)")
        self._nonce = max(self._nonce + 1, int(time.time() * 1000))
        body = {"nonce": str(self._nonce), **(data or {})}
        post = urlencode(body)
        path = f"/0/private/{method}"
        headers = {"API-Key": self.key, "API-Sign": kraken_sign(self.secret, path, str(self._nonce), post),
                   "Content-Type": "application/x-www-form-urlencoded; charset=utf-8"}
        try:
            r = await self.http.post(self.base + path, content=post, headers=headers)
        except httpx.HTTPError as e:
            raise ExchangeError(f"Kraken injoignable ({type(e).__name__})") from e
        return self._result(r)

    async def pairs(self, symbols: list[str]) -> dict[str, KrakenPair]:
        out = {}
        for s in symbols:
            name = PAIRS.get(s)
            if not name:
                raise ExchangeError(f"pas de paire Kraken connue pour {s}")
            res = await self.public("AssetPairs", {"pair": name})
            if not res:
                raise ExchangeError(f"paire {name} introuvable chez Kraken")
            key, info = next(iter(res.items()))
            try:
                out[s] = KrakenPair(s, info.get("altname") or key, info["base"], info["quote"], float(info["ordermin"]),
                                    float(info.get("costmin") or 0), int(info["lot_decimals"]), int(info["pair_decimals"]),
                                    float(info.get("tick_size") or 0))
            except (KeyError, TypeError, ValueError) as e:
                raise ExchangeError(f"métadonnées Kraken incomplètes pour {name} ({e})") from e
        return out

    async def balance(self) -> dict[str, float]:
        return {k: float(v) for k, v in (await self.private("Balance")).items()}

    async def add_order(self, pair: KrakenPair, side: str, ordertype: str, volume: float, price: float | None = None,
                        validate: bool = False, oflags: str | None = None) -> dict:
        d = {"pair": pair.name, "type": side, "ordertype": ordertype, "volume": pair.fmt_volume(volume)}
        if price is not None:
            d["price"] = pair.fmt_price(price)  # pour un stop-loss : prix de déclenchement
        if oflags:
            d["oflags"] = oflags
        if validate:
            d["validate"] = "true"
        return await self.private("AddOrder", d)

    async def query(self, txid: str) -> dict:
        res = await self.private("QueryOrders", {"txid": txid})
        o = res.get(txid)
        if not isinstance(o, dict) or "status" not in o:
            raise ExchangeError(f"ordre {txid} introuvable chez Kraken")
        return o

    async def cancel(self, txid: str) -> None:
        await self.private("CancelOrder", {"txid": txid})

    async def can_withdraw(self) -> bool | None:
        """False si la clé n'a PAS le droit de retrait (ce qu'on veut). True ou None (inconnu) bloquent le réel.

        On interroge WithdrawInfo avec une adresse de retrait qui n'existe pas : aucun retrait n'est possible,
        seule la nature de l'erreur indique si la clé a la permission.
        """
        try:
            await self.private("WithdrawInfo", {"asset": "USDT", "key": "khn-verification-inexistante", "amount": "1"})
        except ExchangeError as e:
            msg = str(e)
            if "Permission denied" in msg:
                return False
            if "Unknown withdraw key" in msg or "EFunding" in msg:
                return True
            return None
        return True

    async def usdt_eur(self) -> float:
        res = await self.public("Ticker", {"pair": "USDTEUR"})
        return float(next(iter(res.values()))["c"][0])

    async def wait_closed(self, txid: str, timeout: float = 15.0) -> dict:
        t0 = time.monotonic()
        while True:
            o = await self.query(txid)
            if o["status"] in ("closed", "canceled", "expired"):
                return o
            if time.monotonic() - t0 > timeout:
                raise ExchangeError(f"ordre {txid} toujours {o['status']} après {timeout:.0f} s")
            await asyncio_sleep(0.5)


async def asyncio_sleep(s: float) -> None:
    import asyncio
    await asyncio.sleep(s)
