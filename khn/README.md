# KHN — bot de trading crypto (démo)

Prototype : `prototype/index.html` (publié en artifact privé claude.ai).
Bureau en pixel art où des robots surveillent un marché crypto simulé (BTC, ETH, SOL),
prennent des positions en démo, analysent leurs pertes et corrigent leurs stratégies.

## Ce que fait le prototype

| Fonction | Mise en œuvre |
|---|---|
| Surveillance | Un scan par marché à chaque bougie de 5 min (simulée). Signal → ordre. |
| Exécution autonome | Stop et objectif posés à l'entrée, taille calculée sur le risque. |
| Gestion du risque | 1 % par trade, perte max du jour 3 %, baisse max 10 %, ordre minimum 5 USDT, arrêt d'urgence. |
| Auto-correction | Diagnostic de chaque perte (contre-tendance, mauvais régime, volatilité, stop serré, sortie tardive). Correction adoptée seulement si elle améliore le résultat sur des données jamais vues (test hors échantillon 30 %). |
| Équipe | KHN seul au départ. Tâches ratées ou file trop longue → recrutement d'un spécialiste (SCOUT, EXEC, QUANT, LEARN, RISK). |
| Apprentissage vidéo | Lien + transcription → Claude extrait des règles chiffrées (format JSON ci-dessous) → backtest → démo si positif. |
| Mémoire | Stratégies, leçons et journal sauvegardés (base de l'artifact, sinon navigateur). |

## Format des stratégies (commun prototype / moteur phase 2)

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

1. **Phase 1 (fait)** : prototype visuel, marché simulé, moteur complet côté navigateur.
2. **Phase 2** : moteur Python sur serveur 24/7 (VPS ~5 €/mois) branché sur
   Binance Spot Testnet (gratuit, sans dépôt) avec données réelles ; transcription YouTube
   récupérée automatiquement ; API Claude pour l'analyse ; même interface pixel en lecture.
3. **Phase 3** : passage en réel uniquement après 2–3 mois de démo positive et un capital
   suffisant. Broker visé : Kraken (licence MiCA). Depuis la Polynésie : virement SWIFT
   15–50 € → inutile avec 1 000 XPF.

## Limites connues

- Le marché est synthétique : les résultats ne prédisent rien sur le marché réel.
- KHN ne travaille que lorsque la page est ouverte (phase 1).
- Sans transcription, une vidéo ne peut pas être analysée.
