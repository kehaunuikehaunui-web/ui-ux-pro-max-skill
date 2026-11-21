"""Réglages lus dans les variables d'environnement (voir .env.example)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field


def _env(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


@dataclass
class Config:
    mode: str = "sim"                      # sim | paper | testnet
    symbols: list[str] = field(default_factory=lambda: ["BTCUSDT", "ETHUSDT", "SOLUSDT"])
    capital_xpf: float = 1000.0
    data_dir: str = "data"
    password: str = ""
    secret: str = ""
    testnet_key: str = ""
    testnet_secret: str = ""
    model: str = "claude-opus-5-5"
    poll_sec: float = 10.0
    sim_candle_sec: float = 3.0
    history: int = 1000
    host: str = "127.0.0.1"
    port: int = 8000
    team_speed: float = 1.0

    @classmethod
    def from_env(cls) -> "Config":
        mode = _env("KHN_MODE", "sim").lower()
        if mode not in ("sim", "paper", "testnet"):
            raise SystemExit(f"KHN_MODE inconnu : {mode} (sim, paper ou testnet)")
        return cls(
            mode=mode,
            symbols=[s.strip().upper() for s in _env("KHN_SYMBOLS", "BTCUSDT,ETHUSDT,SOLUSDT").split(",") if s.strip()],
            capital_xpf=float(_env("KHN_CAPITAL_XPF", "1000")),
            data_dir=_env("KHN_DATA_DIR", "data"),
            password=_env("KHN_PASSWORD"),
            secret=_env("KHN_SECRET"),
            testnet_key=_env("BINANCE_TESTNET_KEY"),
            testnet_secret=_env("BINANCE_TESTNET_SECRET"),
            model=_env("KHN_MODEL", "claude-opus-5-5"),
            poll_sec=float(_env("KHN_POLL_SEC", "10")),
            sim_candle_sec=float(_env("KHN_SIM_CANDLE_SEC", "3")),
            history=int(_env("KHN_HISTORY", "1000")),
            host=_env("KHN_HOST", "127.0.0.1"),
            port=int(_env("KHN_PORT", "8000")),
        )
