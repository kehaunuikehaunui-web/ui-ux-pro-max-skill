"""Exécution des ordres : papier (simulé sur prix réels) ou testnet Binance (spot, achat seulement)."""
from __future__ import annotations

from dataclasses import dataclass

from .exchange import BinanceSpot, ExchangeError, SymbolFilters

FEE, SLIP = 0.001, 0.0002


@dataclass
class Fill:
    price: float
    qty: float
    fee: float  # en USDT


class PaperBroker:
    name = "papier"
    can_short = True

    async def open(self, f: SymbolFilters, side: str, notional: float, price: float) -> Fill:
        px = price * (1 + SLIP if side == "long" else 1 - SLIP)
        qty = f.floor_qty(notional / px)
        if qty <= 0:
            raise ExchangeError("quantité nulle après arrondi au pas de l'exchange")
        return Fill(px, qty, qty * px * FEE)

    async def close(self, f: SymbolFilters, side: str, qty: float, price: float) -> Fill:
        px = price * (1 - SLIP if side == "long" else 1 + SLIP)
        return Fill(px, qty, qty * px * FEE)


class TestnetBroker:
    """Ordres au marché sur testnet.binance.vision. Stop et objectif surveillés par KHN (pas d'ordre stop côté exchange)."""

    name = "testnet"
    can_short = False

    def __init__(self, client: BinanceSpot):
        self.client = client

    @staticmethod
    def _parse(res: dict, base: str) -> Fill:
        qty = float(res.get("executedQty") or 0)
        quote = float(res.get("cummulativeQuoteQty") or 0)
        if qty <= 0:
            raise ExchangeError(f"ordre non exécuté : {res.get('status')}")
        px = quote / qty
        fee = 0.0
        for fl in res.get("fills", []):
            c = float(fl.get("commission") or 0)
            if fl.get("commissionAsset") == base:
                fee += c * px
                qty -= c if res.get("side") == "BUY" else 0
            elif fl.get("commissionAsset") == "USDT":
                fee += c
            else:
                fee += quote * FEE / max(1, len(res.get("fills", [])))
        return Fill(px, qty, fee)

    async def open(self, f: SymbolFilters, side: str, notional: float, price: float) -> Fill:
        if side != "long":
            raise ExchangeError("le testnet spot ne permet pas la vente à découvert")
        res = await self.client.market_order(f.symbol, "BUY", quote_qty=notional)
        fill = self._parse(res, f.base)
        fill.qty = f.floor_qty(fill.qty)
        return fill

    async def close(self, f: SymbolFilters, side: str, qty: float, price: float) -> Fill:
        q = f.floor_qty(qty)
        if q <= 0:
            raise ExchangeError("quantité à vendre nulle après arrondi")
        res = await self.client.market_order(f.symbol, "SELL", qty=q)
        return self._parse(res, f.base)
