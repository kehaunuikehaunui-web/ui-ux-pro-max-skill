"""Exécution des ordres.

- PaperBroker   : ordres simulés sur prix réels (démo).
- TestnetBroker : testnet Binance (argent fictif). Stop et objectif posés chez Binance en OCO.
- KrakenBroker  : argent réel. Stop-loss posé chez Kraken dès l'entrée, objectif géré par KHN.
                  Tant que le mode réel n'est pas armé, chaque ordre est seulement validé par Kraken (essai à blanc).

Interface commune :
  open(f, side, notional, price, stop, tp) -> Fill          (Fill.ref : ordres de protection chez l'exchange)
  close(f, side, qty, price, ref) -> Fill                   (annule la protection puis vend)
  sync(f, pos) -> (Fill, "tp" | "stop") | (None, "lost") | None   (protection exécutée ou disparue chez l'exchange)
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

from .exchange import BinanceSpot, ExchangeError, SymbolFilters
from .kraken import KrakenPair, KrakenSpot

FEE, SLIP = 0.001, 0.0002
KRAKEN_TAKER = 0.004  # frais estimés pour l'essai à blanc (palier de base Kraken)


@dataclass
class Fill:
    price: float
    qty: float
    fee: float  # en USDT
    ref: dict | None = None
    warn: str = ""
    dry: bool = False
    extra: dict = field(default_factory=dict)


class PaperBroker:
    name = "papier"
    can_short = True
    protects = False

    async def open(self, f: SymbolFilters, side: str, notional: float, price: float, stop: float = 0, tp: float = 0) -> Fill:
        px = price * (1 + SLIP if side == "long" else 1 - SLIP)
        qty = f.floor_qty(notional / px)
        if qty <= 0:
            raise ExchangeError("quantité nulle après arrondi au pas de l'exchange")
        return Fill(px, qty, qty * px * FEE)

    async def close(self, f: SymbolFilters, side: str, qty: float, price: float, ref: dict | None = None) -> Fill:
        px = price * (1 - SLIP if side == "long" else 1 + SLIP)
        return Fill(px, qty, qty * px * FEE)

    async def sync(self, f: SymbolFilters, pos: dict):
        return None


def _binance_fill(res: dict, base: str) -> Fill:
    qty = float(res.get("executedQty") or 0)
    quote = float(res.get("cummulativeQuoteQty") or 0)
    if qty <= 0:
        raise ExchangeError(f"ordre non exécuté : {res.get('status')}")
    px = quote / qty
    fee = 0.0
    fills = res.get("fills") or []
    for fl in fills:
        c = float(fl.get("commission") or 0)
        if fl.get("commissionAsset") == base:
            fee += c * px
            if res.get("side") == "BUY":
                qty -= c
        elif fl.get("commissionAsset") == "USDT":
            fee += c
        else:
            fee += quote * FEE / max(1, len(fills))
    if not fills:
        fee = quote * FEE
    return Fill(px, qty, fee)


class TestnetBroker:
    """testnet.binance.vision : achat au marché puis OCO de vente (objectif + stop) chez Binance."""

    name = "testnet"
    can_short = False
    protects = True

    def __init__(self, client: BinanceSpot):
        self.client = client

    _parse = staticmethod(_binance_fill)

    async def open(self, f: SymbolFilters, side: str, notional: float, price: float, stop: float = 0, tp: float = 0) -> Fill:
        if side != "long":
            raise ExchangeError("le testnet spot ne permet pas la vente à découvert")
        fill = _binance_fill(await self.client.market_order(f.symbol, "BUY", quote_qty=notional), f.base)
        fill.qty = f.floor_qty(fill.qty)
        if stop and tp:
            try:
                res = await self.client.oco_sell(f, fill.qty, tp, stop)
                fill.ref = {"list": res["orderListId"], "orders": [o["orderId"] for o in res.get("orders", [])]}
            except (ExchangeError, KeyError) as e:
                fill.warn = f"OCO refusé par Binance ({e}) : stop et objectif surveillés par KHN"
        return fill

    async def close(self, f: SymbolFilters, side: str, qty: float, price: float, ref: dict | None = None) -> Fill:
        if ref and ref.get("list") is not None:
            try:
                await self.client.cancel_order_list(f.symbol, ref["list"])
            except ExchangeError:
                pass  # déjà exécuté ou annulé : sync() le détectera
        q = f.floor_qty(qty)
        if q <= 0:
            raise ExchangeError("quantité à vendre nulle après arrondi")
        return _binance_fill(await self.client.market_order(f.symbol, "SELL", qty=q), f.base)

    async def sync(self, f: SymbolFilters, pos: dict):
        ref = pos.get("ref") or {}
        if ref.get("list") is None:
            return None
        ol = await self.client.order_list(ref["list"])
        if ol.get("listOrderStatus") != "ALL_DONE" and ol.get("listStatusType") != "ALL_DONE":
            return None
        for oid in ref.get("orders", []):
            o = await self.client.get_order(f.symbol, oid)
            if o.get("status") == "FILLED":
                qty = float(o.get("executedQty") or 0)
                quote = float(o.get("cummulativeQuoteQty") or 0)
                if qty > 0:
                    reason = "tp" if o.get("type") in ("LIMIT_MAKER", "TAKE_PROFIT", "TAKE_PROFIT_LIMIT") else "stop"
                    return Fill(quote / qty, qty, quote * FEE), reason
        return None, "lost"


class KrakenBroker:
    """Kraken Spot, argent réel, achat seulement."""

    name = "kraken"
    can_short = False
    protects = True

    def __init__(self, client: KrakenSpot, dry: Callable[[], bool]):
        self.client, self.dry = client, dry
        self.pairs: dict[str, KrakenPair] = {}

    async def prepare(self, symbols: list[str]) -> None:
        self.pairs = await self.client.pairs(symbols)

    def _pair(self, symbol: str) -> KrakenPair:
        p = self.pairs.get(symbol)
        if not p:
            raise ExchangeError("paires Kraken non chargées (clés, réseau ou paire inconnue)")
        return p

    def min_notional(self, symbol: str, price: float) -> float:
        p = self.pairs.get(symbol)
        return max(p.costmin, p.ordermin * price) if p else 5.0

    def _volume(self, p: KrakenPair, notional: float, price: float) -> float:
        vol = p.floor_volume(notional / price)
        if vol < p.ordermin or vol * price < p.costmin:
            raise ExchangeError(f"ordre trop petit pour Kraken (minimum {p.ordermin} {p.symbol.replace('USDT', '')})")
        return vol

    async def open(self, f: SymbolFilters, side: str, notional: float, price: float, stop: float = 0, tp: float = 0) -> Fill:
        if side != "long":
            raise ExchangeError("Kraken spot : achat seulement")
        p = self._pair(f.symbol)
        vol = self._volume(p, notional, price)
        if self.dry():
            await self.client.add_order(p, "buy", "market", vol, validate=True)
            await self.client.add_order(p, "sell", "stop-loss", vol, price=stop, validate=True)
            px = price * (1 + SLIP)
            return Fill(px, vol, vol * px * KRAKEN_TAKER, ref={"dry": True}, dry=True)
        res = await self.client.add_order(p, "buy", "market", vol, oflags="fciq")
        txid = (res.get("txid") or [None])[0]
        if not txid:
            raise ExchangeError("Kraken n'a pas renvoyé d'identifiant d'ordre")
        o = await self.client.wait_closed(txid)
        filled = float(o.get("vol_exec") or 0)
        if o["status"] != "closed" or filled <= 0:
            raise ExchangeError(f"achat non exécuté ({o['status']})")
        cost = float(o.get("cost") or 0)
        fill = Fill(cost / filled if filled else price, p.floor_volume(filled), float(o.get("fee") or 0))
        try:
            s = await self.client.add_order(p, "sell", "stop-loss", fill.qty, price=stop)
            fill.ref = {"stop": (s.get("txid") or [None])[0]}
            if not fill.ref["stop"]:
                raise ExchangeError("stop-loss sans identifiant")
        except ExchangeError as e:
            # Jamais de position réelle sans protection : on revend immédiatement.
            back = await self._sell(p, fill.qty)
            raise ExchangeError(f"stop-loss refusé par Kraken ({e}) : position revendue aussitôt à {back.price:.2f}") from e
        return fill

    async def _sell(self, p: KrakenPair, qty: float) -> Fill:
        bal = await self.client.balance()
        vol = p.floor_volume(min(qty, bal.get(p.base, qty)))
        res = await self.client.add_order(p, "sell", "market", vol)
        o = await self.client.wait_closed((res.get("txid") or [""])[0])
        filled = float(o.get("vol_exec") or 0)
        if filled <= 0:
            raise ExchangeError(f"vente non exécutée ({o['status']})")
        return Fill(float(o.get("cost") or 0) / filled, filled, float(o.get("fee") or 0))

    async def close(self, f: SymbolFilters, side: str, qty: float, price: float, ref: dict | None = None) -> Fill:
        p = self._pair(f.symbol)
        if (ref or {}).get("dry"):
            await self.client.add_order(p, "sell", "market", p.floor_volume(qty), validate=True)
            px = price * (1 - SLIP)
            return Fill(px, qty, qty * px * KRAKEN_TAKER, dry=True)
        if ref and ref.get("stop"):
            try:
                await self.client.cancel(ref["stop"])
            except ExchangeError:
                o = await self.client.query(ref["stop"])
                if o["status"] == "closed":  # le stop vient de s'exécuter : rien à revendre
                    filled = float(o.get("vol_exec") or 0)
                    return Fill(float(o.get("cost") or 0) / filled if filled else price, filled, float(o.get("fee") or 0))
                raise
        return await self._sell(p, qty)

    async def sync(self, f: SymbolFilters, pos: dict):
        ref = pos.get("ref") or {}
        if ref.get("dry") or not ref.get("stop"):
            return None
        o = await self.client.query(ref["stop"])
        if o["status"] == "closed":
            filled = float(o.get("vol_exec") or 0)
            if filled > 0:
                return Fill(float(o.get("cost") or 0) / filled, filled, float(o.get("fee") or 0)), "stop"
        if o["status"] in ("canceled", "expired"):
            return None, "lost"
        return None
