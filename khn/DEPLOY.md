# Installer KHN sur un serveur (phase 2)

Durée : environ 30 minutes. Coût : un serveur à environ 4–6 € par mois, plus l'API Claude
(environ 0,05 à 0,15 $ par vidéo analysée, quelques centimes par question).

## 1. Ce qu'il te faut

| Élément | Où | Pourquoi |
|---|---|---|
| Un serveur Linux (Ubuntu 24.04, 2 Go de RAM) | OVHcloud VPS-1, Hetzner, Scaleway… **région Europe** | KHN tourne 24 h/24. Binance bloque les serveurs situés aux États-Unis. |
| Des clés testnet Binance | https://testnet.binance.vision → *Log In with GitHub* → *Generate HMAC_SHA256 Key* | Ordres en argent fictif (mode `testnet`). Inutile en mode `paper`. |
| Une clé API Claude | https://console.anthropic.com → *API Keys* | Analyse des vidéos et réponses aux questions. Fixe une limite de dépense mensuelle dans la console. |

Ne colle jamais ces clés dans un message ou un chat : elles vont uniquement dans le fichier `.env` du serveur.

## 2. Installation

**En une commande** (pratique depuis un téléphone), une fois connecté au serveur en root :

```bash
curl -fsSL https://raw.githubusercontent.com/kehaunuikehaunui-web/ui-ux-pro-max-skill/ccr-61e597e0-kxc16c/khn/install.sh | bash
```

Si tu es connecté avec un utilisateur qui n'est pas root (par exemple `ubuntu` chez OVHcloud), mets `sudo`
devant `bash` : `… | sudo bash`.

Le script installe Docker, télécharge KHN dans `/opt/khn`, crée le `.env` en mode papier avec un mot de passe
aléatoire (affiché à la fin), démarre KHN et lance la vérification. Relancer la même commande met KHN à jour.

**À la main**, depuis ton ordinateur :

```bash
ssh root@ADRESSE_IP_DU_SERVEUR
curl -fsSL https://get.docker.com | sh
git clone --branch ccr-61e597e0-kxc16c https://github.com/kehaunuikehaunui-web/ui-ux-pro-max-skill.git
cd ui-ux-pro-max-skill/khn
cp .env.example .env
nano .env        # mot de passe, mode, clés (Ctrl+O pour enregistrer, Ctrl+X pour quitter)
mkdir -p data && chown -R 1000:1000 data   # KHN écrit sa mémoire ici avec l'utilisateur 1000
docker compose up -d --build
docker compose run --rm khn python -m engine check
```

La dernière commande doit afficher `Tout est prêt.` Sinon elle dit quoi corriger.

## 3. Ouvrir l'interface

**Option A, sans nom de domaine (le plus simple)** : un tunnel SSH depuis ton ordinateur.

```bash
ssh -L 8000:127.0.0.1:8000 root@ADRESSE_IP_DU_SERVEUR
```

Puis ouvre http://localhost:8000 dans ton navigateur et entre le mot de passe du `.env`.

**Option B, depuis ton téléphone n'importe où** : un nom de domaine gratuit (par exemple sur
https://www.duckdns.org) qui pointe vers l'adresse IP du serveur. Mets-le dans `KHN_DOMAIN` du `.env`, puis :

```bash
docker compose --profile https up -d
```

L'interface est alors sur `https://ton-domaine`, en HTTPS automatique, protégée par ton mot de passe.

## 4. Ordre conseillé des modes

1. `KHN_MODE=paper` pendant au moins une semaine : prix réels, ordres simulés. Aucun compte requis.
2. `KHN_MODE=testnet` ensuite : vrais ordres sur le testnet Binance (argent fictif, achat seulement).
3. `KHN_MODE=live` : argent réel chez Kraken (voir section 7). Le réel reste verrouillé tant que les
   conditions de l'onglet **Réel** ne sont pas toutes remplies ; en attendant, KHN tourne en essai à blanc.

Chaque mode a sa propre mémoire (`data/khn-paper.sqlite`, `data/khn-testnet.sqlite`). Pour changer de mode :
modifie `KHN_MODE` dans `.env`, puis `docker compose up -d`.

## 5. Entretien

| Besoin | Commande (dans `ui-ux-pro-max-skill/khn`) |
|---|---|
| Voir ce que fait KHN | `docker compose logs -f khn` |
| Mettre à jour | `git pull && docker compose up -d --build` |
| Redémarrer | `docker compose restart khn` |
| Sauvegarder la mémoire | copier le dossier `data/` |
| Tester une transcription | `docker compose run --rm khn python -m engine check --video https://youtu.be/...` |

## 6. Problèmes connus

- **YouTube bloque le serveur** (« YouTube bloque l'adresse IP du serveur ») : fréquent sur les serveurs loués.
  Colle la transcription dans le formulaire (sous la vidéo : « … » → « Afficher la transcription »).
- **Binance répond 451** : le serveur est dans un pays bloqué. Choisis une région européenne.
- **Testnet remis à zéro** : Binance réinitialise régulièrement le testnet. Recrée des clés si `check` échoue.
- **Protection des positions** : en testnet, stop et objectif sont posés chez Binance (ordre OCO). En réel,
  le stop est posé chez Kraken dès l'achat ; si Kraken refuse le stop, KHN revend aussitôt. Les positions
  restent donc protégées même si le serveur s'arrête.

## 7. Passer en argent réel (phase 3)

Le réel ne s'active que si **toutes** ces conditions sont vraies (l'onglet **Réel** les suit en direct) :

| Condition | Seuil |
|---|---|
| Démo (papier ou testnet) | au moins 60 jours et 50 trades |
| Résultat de la démo | au moins +0,05 R par trade et facteur de profit ≥ 1,10 |
| Baisse max de la démo | sous la limite réglée |
| Capital sur Kraken | au moins `KHN_LIVE_MIN_XPF` (60 000 XPF par défaut) en USDT |
| Clé Kraken | valide, **sans** droit de retrait |
| Ordre d'essai | validé par Kraken sans exécution |
| Activation | tu tapes « JE RISQUE MON ARGENT » dans l'onglet Réel |

Étapes :
1. Kraken → Paramètres → API → créer une clé avec seulement : *Query Funds*, *Query Open Orders & Trades*,
   *Query Closed Orders & Trades*, *Create & Modify Orders*, *Cancel/Close Orders*. Jamais *Withdraw Funds*.
2. Dans `.env` : `KRAKEN_KEY`, `KRAKEN_SECRET`, puis `KHN_MODE=live`, et `docker compose up -d`.
   La mémoire de la démo (`data/khn-paper.sqlite` ou `data/khn-testnet.sqlite`) doit rester dans `data/`.
3. Optionnel : installe l'appli **ntfy** sur ton téléphone, abonne-toi à un sujet secret et mets son adresse
   dans `KHN_NTFY_URL` pour être prévenu de chaque ordre à valider.

Réglages imposés en réel : 0,5 % de risque par trade, une position à la fois, perte max 2 % par jour et 5 %
par semaine, validation de chaque ordre par toi (10 minutes pour répondre, sinon l'ordre est annulé ; elle
ne peut être coupée qu'après 20 trades réels). L'arrêt d'urgence ferme les positions et désarme le réel.
