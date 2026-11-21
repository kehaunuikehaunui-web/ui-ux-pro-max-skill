# Installer KHN sur un serveur (phase 2)

Durée : environ 30 minutes. Coût : un serveur à environ 4–6 € par mois, plus l'API Claude
(environ 0,05 à 0,15 $ par vidéo analysée, quelques centimes par question).

## 1. Ce qu'il te faut

| Élément | Où | Pourquoi |
|---|---|---|
| Un serveur Linux (Ubuntu 24.04, 2 Go de RAM) | Hetzner, OVH, Scaleway… **région Europe** | KHN tourne 24 h/24. Binance bloque les serveurs situés aux États-Unis. |
| Des clés testnet Binance | https://testnet.binance.vision → *Log In with GitHub* → *Generate HMAC_SHA256 Key* | Ordres en argent fictif (mode `testnet`). Inutile en mode `paper`. |
| Une clé API Claude | https://console.anthropic.com → *API Keys* | Analyse des vidéos et réponses aux questions. Fixe une limite de dépense mensuelle dans la console. |

Ne colle jamais ces clés dans un message ou un chat : elles vont uniquement dans le fichier `.env` du serveur.

## 2. Installation

Depuis ton ordinateur :

```bash
ssh root@ADRESSE_IP_DU_SERVEUR
curl -fsSL https://get.docker.com | sh
git clone --branch ccr-61e597e0-kxc16c https://github.com/kehaunuikehaunui-web/ui-ux-pro-max-skill.git
cd ui-ux-pro-max-skill/khn
cp .env.example .env
nano .env        # mot de passe, mode, clés (Ctrl+O pour enregistrer, Ctrl+X pour quitter)
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
3. Réel : pas avant 2 à 3 mois de résultats positifs en démo et un capital suffisant (voir l'onglet Broker).
   Le code de la phase 2 refuse d'envoyer un ordre ailleurs qu'au testnet.

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
- **Stops en mode testnet** : ils sont surveillés par KHN (contrôle toutes les 10 s), pas posés chez Binance.
  Si le serveur s'arrête, les positions ouvertes ne sont plus protégées : ce sera corrigé avant tout passage au réel.
