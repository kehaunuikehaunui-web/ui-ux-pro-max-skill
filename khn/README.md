# KHN — bot de trading crypto (démo)

| Dossier | Contenu |
|---|---|
| `prototype/` | Phase 1 : prototype autonome dans le navigateur (marché synthétique), publié en artifact. |
| `engine/` | Phase 2 : moteur Python (FastAPI) qui tourne 24 h/24 sur un serveur. |
| `web/` | Phase 2 : interface pixel branchée sur le moteur (`index.html` est généré par `build_client.py`). |
| `tests/` | Tests du moteur (`python -m unittest discover -s tests`). |
| `DEPLOY.md` | Installation pas à pas sur un serveur. |

## Phase 2 : ce que fait le moteur

| Fonction | Mise en œuvre |
|---|---|
| Données | Bougies 5 min réelles de Binance (`paper`) ou du testnet (`testnet`), marché synthétique (`sim`). |
| Exécution | `paper` : ordres simulés sur prix réels, achat et vente à découvert. `testnet` : ordres au marché signés sur testnet.binance.vision (spot, achat seulement). Aucun ordre possible ailleurs. |
| Risque | 1 % par trade, perte max du jour 3 % (jour de Tahiti), baisse max 10 %, filtres Binance (minimum d'ordre, pas de quantité), arrêt d'urgence. |
| Auto-correction | Diagnostic de chaque perte, correction adoptée seulement si elle tient sur les 30 % d'historique jamais vus. |
| Équipe | Tâches réelles (scan, ordre, analyse, correction, backtest, vidéo, risque) exécutées par des robots ; recrutement quand une tâche attend ou est ratée. |
| Vidéos | Transcription YouTube récupérée automatiquement, règles extraites par Claude (`claude-opus-5-5`, sortie JSON structurée, repli automatique sur un autre modèle en cas de refus), puis backtest. |
| Mémoire | SQLite dans `data/`, une base par mode. |
| Accès | Mot de passe, cookie signé, interface et commandes en temps réel (WebSocket). |

## Lancer en local (sans serveur)

```bash
cd khn
pip install -r requirements.txt
KHN_MODE=sim KHN_PASSWORD=essai python -m engine     # puis http://localhost:8000
```

## Format des stratégies

```json
{
  "name": "Croisement EMA 9/21", "family": "trend",
  "long":  [{"a":{"k":"ema","n":9},"op":"cross_above","b":{"k":"ema","n":21}}],
  "short": [{"a":{"k":"ema","n":9},"op":"cross_below","b":{"k":"ema","n":21}}],
  "stop": {"type":"atr","mult":1.5}, "tp": {"rr":2},
  "filters": {"htfAlign": true, "minTrend": 0.25}
}
```
Opérandes : `close, ema, sma, rsi, atr, bb_up, bb_lo, bb_mid, don_hi, don_lo, macd, macd_sig, num`.

## Feuille de route

1. **Phase 1 (fait)** : prototype visuel.
2. **Phase 2 (fait)** : moteur serveur, prix réels, testnet, Claude, transcription YouTube.
3. **Phase 3** : stops posés chez l'exchange (ordres OCO), puis passage en réel seulement après 2–3 mois
   de démo positive et un capital suffisant. Broker visé : Kraken (licence MiCA).

## Limites connues

- Les résultats en démo ne garantissent rien en réel.
- YouTube bloque souvent les adresses de serveurs loués : coller la transcription reste possible.
- En testnet, les stops sont surveillés par KHN, pas posés chez Binance.
