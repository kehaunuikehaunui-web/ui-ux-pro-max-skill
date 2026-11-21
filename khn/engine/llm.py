"""Cerveau de KHN : Claude extrait des règles chiffrées d'une transcription et répond aux questions."""
from __future__ import annotations

import json
import os

import anthropic

OPERAND = {
    "type": "object",
    "properties": {
        "k": {"type": "string", "enum": ["close", "ema", "sma", "rsi", "atr", "bb_up", "bb_lo", "bb_mid", "don_hi", "don_lo", "macd", "macd_sig", "num"]},
        "n": {"type": ["integer", "null"]},
        "m": {"type": ["number", "null"]},
        "v": {"type": ["number", "null"]},
    },
    "required": ["k", "n", "m", "v"],
    "additionalProperties": False,
}
COND = {"type": "object", "properties": {"a": OPERAND, "op": {"type": "string", "enum": [">", "<", "cross_above", "cross_below"]}, "b": OPERAND},
        "required": ["a", "op", "b"], "additionalProperties": False}
CONDS = {"type": "array", "items": COND}
SCHEMA = {
    "type": "object",
    "properties": {
        "insufficient": {"type": "boolean"},
        "missing": {"type": "array", "items": {"type": "string"}},
        "name": {"type": "string"},
        "family": {"type": "string", "enum": ["trend", "meanrev", "breakout"]},
        "summary": {"type": "string"},
        "long": CONDS, "short": CONDS, "exitLong": CONDS, "exitShort": CONDS,
        "stop": {"type": "object", "properties": {"type": {"type": "string", "enum": ["atr", "pct"]}, "value": {"type": "number"}},
                 "required": ["type", "value"], "additionalProperties": False},
        "tp_rr": {"type": "number"},
        "assumptions": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["insufficient", "missing", "name", "family", "summary", "long", "short", "exitLong", "exitShort", "stop", "tp_rr", "assumptions"],
    "additionalProperties": False,
}

SYSTEM_EXTRACT = """Tu es l'analyste de stratégies de KHN, un bot de trading crypto (bougies de 5 minutes, BTC/ETH/SOL face à l'USDT).
Tu reçois la transcription ou les notes d'une vidéo de trading. Tu en extrais UNE stratégie exécutable, uniquement à partir du texte.
Tu n'inventes jamais de règle absente du texte. Si le texte ne contient pas de règle d'entrée chiffrable, mets "insufficient": true,
liste ce qui manque dans "missing", résume le texte dans "summary" et laisse les listes de conditions vides.

Règles du format :
- Une liste de conditions est vraie quand toutes ses conditions sont vraies. Liste vide = sens non tradé.
- Opérande "k" : close (prix de clôture), ema, sma, rsi, atr, bb_up/bb_lo/bb_mid (Bollinger, "m" = écarts-types), don_hi/don_lo
  (plus haut/plus bas des n bougies précédentes), macd, macd_sig, num (valeur fixe dans "v"). "n" = période (2 à 200), null si inutile.
- "stop" : {"type":"atr","value":multiple de l'ATR 14} ou {"type":"pct","value":pourcentage du prix}. "tp_rr" : objectif en multiple du risque.
- Un indicateur absent de la liste : approxime avec ceux disponibles et écris-le dans "assumptions".
- Textes en français. "name" : 28 caractères maximum."""

SYSTEM_CHAT = """Tu es KHN, robot trader en compte de démo (aucun argent réel). Tu réponds à ton utilisateur en français, en 90 mots maximum,
de façon directe et honnête, en t'appuyant uniquement sur l'état du compte fourni. Tu ne promets jamais de gains."""


class BrainError(Exception):
    pass


class Brain:
    def __init__(self, model: str | None = None):
        self.model = model or os.getenv("KHN_MODEL", "claude-opus-5-5")
        self.available = bool(os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN"))
        self.client = anthropic.AsyncAnthropic() if self.available else None

    async def _call(self, system: str, user: str, effort: str, schema: dict | None = None, max_tokens: int = 16000) -> str:
        if not self.client:
            raise BrainError("aucune clé API Anthropic configurée (ANTHROPIC_API_KEY)")
        output_config: dict = {"effort": effort}
        if schema:
            output_config["format"] = {"type": "json_schema", "schema": schema}
        try:
            # Repli automatique sur un autre modèle si celui-ci décline la demande (refus de sécurité).
            resp = await self.client.beta.messages.create(
                model=self.model, max_tokens=max_tokens, system=system,
                messages=[{"role": "user", "content": user}],
                output_config=output_config,
                betas=["server-side-fallback-2026-07-01"], fallbacks="default",
            )
        except anthropic.AuthenticationError as e:
            raise BrainError("clé API Anthropic refusée") from e
        except anthropic.RateLimitError as e:
            raise BrainError("trop de demandes à Claude, réessaie dans une minute") from e
        except anthropic.BadRequestError as e:
            raise BrainError(f"requête refusée par l'API : {e.message}") from e
        except anthropic.APIStatusError as e:
            raise BrainError(f"erreur de l'API Claude ({e.status_code})") from e
        except anthropic.APIConnectionError as e:
            raise BrainError("API Claude injoignable") from e
        if resp.stop_reason == "refusal":
            raise BrainError("Claude a décliné cette demande")
        text = next((b.text for b in resp.content if b.type == "text"), "")
        if not text:
            raise BrainError("réponse vide")
        return text

    async def extract_strategy(self, url: str, transcript: str) -> dict:
        user = f"Source : {url}\n\nTranscription ou notes :\n\"\"\"\n{transcript[:60000]}\n\"\"\""
        text = await self._call(SYSTEM_EXTRACT, user, "high", SCHEMA)
        try:
            raw = json.loads(text)
        except json.JSONDecodeError as e:
            raise BrainError("réponse JSON illisible") from e
        stop = raw.get("stop") or {}
        raw["stop"] = {"type": "pct", "value": stop.get("value")} if stop.get("type") == "pct" else {"type": "atr", "mult": stop.get("value")}
        raw["tp"] = {"rr": raw.get("tp_rr") or 2}
        return raw

    async def answer(self, question: str, context: dict) -> str:
        user = f"État du compte :\n{json.dumps(context, ensure_ascii=False)}\n\nQuestion : {question[:1500]}"
        return (await self._call(SYSTEM_CHAT, user, "low", max_tokens=2000)).strip()

    async def check(self) -> str:
        if not self.client:
            raise BrainError("aucune clé API Anthropic configurée")
        try:
            m = await self.client.models.retrieve(self.model)
        except anthropic.APIError as e:
            raise BrainError(str(e)) from e
        return m.id
