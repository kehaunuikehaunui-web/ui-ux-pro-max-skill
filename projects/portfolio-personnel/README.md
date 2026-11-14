# Portfolio personnel

Site statique d'une page, en français : accueil, projets, à propos, parcours, contact.
HTML + Tailwind CSS v4 + design tokens. Thème clair/sombre, responsive, navigable au clavier.

Le contenu est provisoire : tout ce qui doit être remplacé est signalé dans
`index.html` par un commentaire `À PERSONNALISER`.

## Voir le site

Aucune installation n'est nécessaire, le CSS compilé est déjà dans `assets/styles.css`.

```bash
python3 -m http.server 4173
# puis ouvrir http://localhost:4173
```

## Personnaliser le contenu

Tout se passe dans `index.html` :

| Élément | Où |
| --- | --- |
| Nom, métier, titre de l'onglet, description | `<head>`, en-tête, section Accueil, pied de page |
| Initiales du monogramme (`VN`) | en-tête, bloc portrait, `assets/favicon.svg` |
| Phrase d'accroche et les trois chiffres | section Accueil |
| Projets (titre, texte, lien, image) | section Projets, une `<li>` par projet |
| Présentation, méthode, compétences, portrait | section À propos |
| Postes et diplômes | section Parcours |
| E-mail, LinkedIn, GitHub | section Contact |

Les visuels des projets sont des compositions provisoires. Pour mettre une vraie
image, remplacez le `<svg>` de `.project-cover` par la balise `<img>` donnée en
commentaire au-dessus de la liste.

## Changer les couleurs, les polices ou les espacements

Ne modifiez pas `assets/styles.css` ni `src/tokens.css` à la main : ils sont générés.

1. Modifiez `tokens.json` (trois niveaux : `primitive` → `semantic` → `component`,
   plus `dark` pour le thème sombre).
2. Régénérez :

```bash
npm install     # une seule fois
npm run build   # tokens.json -> src/tokens.css -> assets/styles.css
```

`npm run dev` recompile le CSS à chaque enregistrement.
Si vous ajoutez des classes Tailwind dans `index.html`, relancez aussi `npm run build`.

## Fichiers

```
index.html          la page
assets/styles.css   CSS compilé (généré)
assets/main.js      thème, menu mobile, apparition au défilement
assets/favicon.svg  monogramme
tokens.json         source des design tokens
src/tokens.css      variables CSS (généré depuis tokens.json)
src/input.css       entrée Tailwind + composants (boutons, cartes, navigation)
```

## Mise en ligne

Le dossier se déploie tel quel sur n'importe quel hébergement statique
(GitHub Pages, Netlify, Vercel…) : seuls `index.html` et `assets/` sont servis.

Icônes : [Phosphor](https://phosphoricons.com) (licence MIT).
Polices : Space Grotesk et Archivo, via Google Fonts.
