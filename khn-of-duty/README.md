# KHN OF DUTY

FPS militaire moderne jouable dans le navigateur (Three.js, sans moteur externe). Univers, factions,
armes et cartes sont fictifs et originaux. Interface conçue avec le skill `ui-ux-pro-max` de ce dépôt.

## Lancer le jeu

```bash
cd khn-of-duty
npm install
npm run build        # produit dist/index.html (un seul fichier autonome)
```

Ouvrez ensuite `dist/index.html` dans un navigateur : le mode Entraînement fonctionne hors ligne.
Pour le multijoueur, lancez le serveur (il sert aussi le jeu) :

```bash
npm start            # build + serveur sur http://localhost:8080
```

## Commandes

| Action | Clavier / souris | Tactile |
|---|---|---|
| Se déplacer | ZQSD / WASD (positions physiques) | Joystick gauche |
| Viser | Souris | Glisser à droite |
| Tirer / viser à l'épaule | Clic gauche / clic droit | Boutons dédiés |
| Sprinter / sauter | Maj / Espace | Joystick à fond / bouton |
| Recharger / changer d'arme / équipement | R / 1·2·molette / G | Boutons dédiés |
| Scores / pause | Tab / Échap·P | Boutons dédiés |

## Contenu

- **Modes** : Match à mort par équipe, Mêlée générale, Domination (zones A/B/C).
- **Cartes** : Dépôt Kestrel (jour), Avant-poste Sirocco (crépuscule), Station Borée (nuit).
- **Arsenal** : 4 armes principales, 2 secondaires, 2 équipements, 5 camouflages, 4 opérateurs.
- **Bots** : navigation A* sur grille, perception (champ de vision, ligne de vue, bruit des tirs),
  visée avec temps de réaction et erreur selon 3 niveaux de difficulté.
- **Progression** : XP, 30 niveaux, grades, déblocages, classement local, statistiques, historique.

## Multijoueur

`npm start` lance le serveur de parties : il sert le jeu sur `http://<machine>:8080` et accepte les
connexions WebSocket sur `/ws`. Les joueurs ouvrent cette adresse, vont dans **Multijoueur**, se
connectent, puis créent ou rejoignent un salon (Match à mort par équipe ou Mêlée générale, 2 à 8 joueurs).

- **Client** : chaque joueur simule son personnage et envoie sa position 15 fois par seconde ; les
  autres joueurs sont interpolés.
- **Serveur** : fait autorité sur les salons, la santé, les éliminations, les scores, les
  réapparitions et la fin de partie. Il valide les tirs annoncés (cible vivante et ennemie,
  protection d'apparition, dégâts et cadence plausibles) et retire les connexions mortes.
- **Hébergement** : n'importe quelle machine Node 18+ joignable par les joueurs (`PORT` configurable).
  Derrière HTTPS, placez un proxy qui relaie aussi `/ws` (le client bascule seul en `wss://`).

Limites actuelles : pas de bots en ligne, pas de Domination en ligne, pas de comptes ni de
classement mondial, déplacements non vérifiés par le serveur, aucune limitation de débit par client.
Le multijoueur ne fonctionne pas dans l'aperçu publié en Artifact (connexions réseau interdites).

## Architecture

```
src/
├── main.js            point d'entrée (assemble les modules)
├── styles.css         jetons de design + styles menus / HUD / tactile
├── core/              config (arsenal, modes…), cartes, profil, audio synthétisé, entrées
├── game/
│   ├── physics.js     collisions AABB + lancers de rayon analytiques
│   ├── nav.js         grille de navigation + A*
│   ├── worlddata.js   partie non visuelle d'une carte (client, tests, serveur)
│   ├── match.js       simulation : personnages, armes, IA, modes, score, fin de partie
│   ├── world.js       rendu d'une carte (géométrie fusionnée, textures procédurales)
│   ├── characters.js  modèles d'armes et de soldats
│   └── engine.js      rendu, caméra, effets, liaison entrées ↔ simulation ↔ HUD
├── ui/                menus, HUD, écran multijoueur
└── net/               client réseau
server/server.mjs      serveur de parties (WebSocket) + service du jeu
tests/                 sim.mjs (logique), smoke.mjs (navigateur), net.mjs (multijoueur)
```

La simulation (`match.js`) ne dépend pas du rendu : elle tourne telle quelle sous Node pour les tests.

## Tests

```bash
node tests/sim.mjs     # 3 cartes × 3 modes, bots seuls, jusqu'à la fin de partie
npm test               # build + parcours complet dans Chromium (menus, partie, tactile)
npm run test:net       # serveur + plusieurs clients réels
```

`smoke.mjs` et `net.mjs` nécessitent Playwright et Chromium installés sur la machine.

## Limites connues

- Les performances n'ont été mesurées qu'en rendu logiciel (sans carte graphique) ; elles restent à
  valider sur de vrais téléphones.
- Le classement est local à l'appareil (pas de classement mondial).
- Le multijoueur a été testé avec trois clients sur une même machine ; il ne l'a pas été sur Internet
  (latence réelle, pertes de paquets) ni avec huit joueurs.
- Modèles 3D volontairement simples (volumes), sans animations squelettiques ni ressources externes.
