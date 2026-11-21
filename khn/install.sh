#!/usr/bin/env bash
# Installation de KHN (phase 2) sur un serveur Ubuntu neuf, en une commande :
#   curl -fsSL https://raw.githubusercontent.com/kehaunuikehaunui-web/ui-ux-pro-max-skill/ccr-61e597e0-kxc16c/khn/install.sh | bash
# Relancer la même commande met KHN à jour sans toucher au mot de passe ni à la mémoire.
set -euo pipefail

BRANCH="${KHN_BRANCH:-ccr-61e597e0-kxc16c}"
REPO="https://github.com/kehaunuikehaunui-web/ui-ux-pro-max-skill.git"
DIR="${KHN_DIR:-/opt/khn}"
say() { printf '\n\033[1;33m== %s\033[0m\n' "$*"; }

if [ "$(id -u)" -ne 0 ]; then
  echo "Ce script doit tourner en administrateur. Relance-le avec sudo devant bash :"
  echo "  curl -fsSL https://raw.githubusercontent.com/kehaunuikehaunui-web/ui-ux-pro-max-skill/ccr-61e597e0-kxc16c/khn/install.sh | sudo bash"
  exit 1
fi

say "1/5 Mise à jour du système"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y -q
apt-get install -y -q git curl ca-certificates openssl

say "2/5 Installation de Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker >/dev/null 2>&1 || true

say "3/5 Téléchargement de KHN"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" fetch -q origin "$BRANCH"
  git -C "$DIR" checkout -q "$BRANCH"
  git -C "$DIR" reset -q --hard "origin/$BRANCH"
else
  git clone -q --branch "$BRANCH" "$REPO" "$DIR"
fi
cd "$DIR/khn"

say "4/5 Réglages"
NEWPW=""
if [ ! -f .env ]; then
  cp .env.example .env
  NEWPW="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | cut -c1-20)"
  sed -i "s/^KHN_PASSWORD=.*/KHN_PASSWORD=$NEWPW/" .env
  sed -i "s/^KHN_MODE=.*/KHN_MODE=paper/" .env
  chmod 600 .env
  echo "Fichier .env créé (mode papier : prix réels, ordres simulés)."
else
  echo "Fichier .env existant conservé."
fi
mkdir -p data
chown -R 1000:1000 data   # KHN tourne avec l'utilisateur 1000 dans le conteneur et doit écrire sa mémoire ici

say "5/5 Construction et démarrage (2 à 5 minutes la première fois)"
docker compose up -d --build
echo "Attente du démarrage…"
for _ in $(seq 1 30); do
  if curl -fs http://127.0.0.1:8000/healthz >/dev/null 2>&1; then break; fi
  sleep 2
done

say "Vérification"
docker compose run --rm khn python -m engine check || true
if curl -fs http://127.0.0.1:8000/healthz >/dev/null 2>&1; then
  echo "KHN est en service."
else
  echo "KHN ne répond pas encore. Regarde le journal : cd $DIR/khn && docker compose logs --tail 50 khn"
fi

if [ -n "$NEWPW" ]; then
  printf '\n\033[1;32mMOT DE PASSE DE L\x27INTERFACE : %s\033[0m\n' "$NEWPW"
  echo "Note-le dans un endroit sûr. Il est aussi dans $DIR/khn/.env"
fi
echo
echo "Ouvre ensuite le tunnel (port 8000 du serveur vers ton appareil) puis http://localhost:8000"
