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
| Exécution | `paper` : ordres simulés sur prix réels, achat et vente à découvert. `testnet` : ordres signés sur testnet.binance.vision avec OCO (spot, achat seulement). `live` : Kraken spot, stop-loss posé chez Kraken, essai à blanc tant que le réel n'est pas armé. |
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
3. **Phase 3 (fait)** : stops posés chez l'exchange (OCO sur le testnet, stop-loss chez Kraken), mode réel
   Kraken verrouillé par 9 conditions (`engine/gate.py`), essai à blanc, validation de chaque ordre,
   plafond de perte hebdomadaire, notifications ntfy.

## Limites connues

- Les résultats en démo ne garantissent rien en réel.
- YouTube bloque souvent les adresses de serveurs loués : coller la transcription reste possible.
- Kraken spot n'accepte qu'un ordre de protection par position : le stop est chez Kraken, l'objectif est géré par KHN.
- Le client Kraken n'a été testé que contre des réponses simulées : l'essai à blanc au démarrage du mode `live`
  est la première vérification réelle.
