# KHN JUICE — Boutique en ligne de boissons

Site vitrine et boutique (jus pressés à froid, smoothies, shots, limonades, cures) en HTML, CSS et JavaScript, sans étape de compilation. Les animations utilisent GSAP 3.13 et ScrollTrigger, embarqués en local.

## Lancer le site

```bash
cd projects/khn-juice
python3 -m http.server 8000
# puis ouvrir http://localhost:8000
```

On peut aussi ouvrir `index.html` directement dans le navigateur (testé en `file://`).

| Page | Rôle |
|------|------|
| `index.html` | Accueil. Hero avec sélecteur de saveur, engagements, best-sellers, étapes, cure, avis, newsletter |
| `boutique.html` | Catalogue : recherche, filtres par catégorie et tri. L'état est conservé dans l'URL (`?q=&cat=&tri=`) |
| `produit.html?id=…` | Fiche produit : format, quantité, ingrédients, suggestions |
| `commande.html` | Commande : coordonnées, livraison ou retrait, validation accessible, confirmation |

Le panier est un tiroir latéral (`<dialog>` natif) disponible sur toutes les pages. Il est enregistré dans `localStorage`.

## Avant une mise en production

Le contenu actuel est un **contenu d'exemple**. À remplacer :

- **Produits, prix, recettes, allergènes** : `assets/js/data.js` (`KHN.products`).
- **Avis clients** : `KHN.testimonials` dans `data.js`. Ce sont des exemples, marqués « Client·e exemple » sur la page. Mettez-y de vrais avis vérifiés.
- **Coordonnées, horaires, adresse** : pied de page des 4 fichiers HTML (mentions « à compléter »).
- **Allégations marketing** (« pressé à froid », « sans sucre ajouté »…) : à garder seulement si elles décrivent vos pratiques réelles.
- **Paiement et commandes** : `KHN.config.demoMode = true` simule la commande et la newsletter, sans rien envoyer ni encaisser. Pour vendre réellement, il faut brancher un service (Stripe Checkout, Shopify Buy Button, etc.) dans `assets/js/pages/checkout.js`, puis passer `demoMode` à `false`.
- **Livraison** : seuil de gratuité et frais dans `KHN.config` (`freeShippingThreshold`, `shippingFee`).
- **Devise** : `KHN.config.currency` / `locale` (EUR, `fr-FR` par défaut).

## Design system : UI UX Pro Max

Toutes les décisions visuelles viennent du skill UI UX Pro Max (`.claude/skills/ui-ux-pro-max/`). Sa sortie est enregistrée dans `design-system/khn-juice/MASTER.md`, qui fait foi.

Commande utilisée :

```bash
python3 .claude/skills/ui-ux-pro-max/scripts/search.py "juice bar beverage e-commerce fresh" \
  --design-system --persist -p "KHN JUICE" --variance 6 --motion 7 --density 4 \
  --output-dir projects/khn-juice
```

| Domaine | Recommandation retenue | Où elle est appliquée |
|---------|------------------------|------------------------|
| Produit | Aucune ligne « jus/boissons » dans la base (0 résultat, vérifié) → catégorie **E-commerce** | Toute la structure |
| Style | **Vibrant & Block-based** (+ Motion-Driven) : blocs de couleur, grands titres, motif animé | Blocs vert foncé et orange, bandeau défilant |
| Pattern | **Feature-Rich Showcase** : Hero → 6 cartes → bénéfices → preuve sociale → CTA | Ordre des sections de l'accueil, CTA répété 3 fois |
| Couleurs | Palette E-commerce : `#059669` / `#10B981` / CTA `#EA580C` / fond `#ECFDF5` / texte `#064E3B` | Jetons `--color-*` dans `styles.css` |
| Typo | **Rubik** (titres) + **Nunito Sans** (texte) | Google Fonts |
| Espacements | Densité 4/10 : `--space-xs` 4 px → `--space-3xl` 64 px | Jetons `--space-*` |
| Mouvement | GSAP *Stagger List*, *Scroll Reveal*, *Parallax*, *Page Transition*, *Hover* (niveau Standard) | `assets/js/motion.js` |
| Icônes | Phosphor (`--domain icons`), aucun emoji | `assets/js/icons.js` (sprite SVG) |
| UX | Labels visibles, validation au blur, récapitulatif d'erreurs focalisable, annonces contextuelles du panier, annulation de suppression, lien d'évitement, `prefers-reduced-motion` | Formulaires, panier, toutes les pages |
| Page `commande` | Override `pages/commande.md` : retour de chargement puis succès, pas de champ sans label | `commande.html` |

### Écarts assumés (avec justification)

- **Overrides « boutique » et « produit » non conservés** : le générateur renvoyait des sections de portfolio et du WebGL/3D, hors sujet, et une 2e requête n'a pas fait mieux. Ces pages suivent donc MASTER.md et les règles UX vérifiées.
- **Bordure des champs** : `#E2E8F0` (spec MASTER) ne donne que 1,2:1 sur blanc. J'utilise `--color-muted-foreground` (7,6:1), car l'accessibilité est la priorité n° 1 du skill.
- **Fond des cartes** : le token `--color-card` (#FFF) de la palette, plutôt que #ECFDF5 (identique au fond de page).
- **Pas de scroll-snap** : la règle tactile *gesture-conflicts* (priorité 2) passe avant l'effet de style.
- **Contraste 7:1 du style** : le texte courant le respecte (9,2:1 et 7,2:1). Les boutons sont entre 5,6 et 5,9:1, ce qui satisfait le seuil WCAG AA de 4,5:1.

## Vérifications effectuées

- Playwright (Chromium) à 375, 768, 1024, 1280 et 1440 px : aucun débordement horizontal, aucune erreur console.
- 37 tests d'interaction : ajout au panier, quantités, suppression et « Annuler », Échap et retour du focus, filtres et URL, état vide, validation du formulaire, chargement puis confirmation, mode mouvement réduit.
- axe-core 4.10 (WCAG 2.2 AA + bonnes pratiques) sur 14 vues : **0 violation**. Un audit automatique ne couvre qu'une partie de WCAG ; un test manuel au lecteur d'écran reste recommandé.

## Licences tierces

- GSAP 3.13.0 / ScrollTrigger : licence standard GreenSock (gratuite), https://gsap.com/standard-license
- Phosphor Icons (`@phosphor-icons/core` 2.1.1) : licence MIT
