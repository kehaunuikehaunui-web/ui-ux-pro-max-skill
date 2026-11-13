/* ==========================================================================
   KHN JUICE — Catalogue & configuration
   ATTENTION — CONTENU D'EXEMPLE : prix, recettes, avis et coordonnées sont des
   valeurs de démonstration à remplacer par les vraies données de la boutique.
   ========================================================================== */
window.KHN = window.KHN || {};

KHN.config = {
  brand: 'KHN JUICE',
  locale: 'fr-FR',
  currency: 'EUR',
  freeShippingThreshold: 35, // livraison offerte dès ce montant
  shippingFee: 4.9,
  // true : la commande et la newsletter sont simulées (aucune donnée envoyée).
  demoMode: true
};

KHN.categories = [
  { id: 'presses', label: 'Jus pressés à froid', short: 'Jus pressés' },
  { id: 'smoothies', label: 'Smoothies', short: 'Smoothies' },
  { id: 'shots', label: 'Shots', short: 'Shots' },
  { id: 'limonades', label: 'Limonades & infusions', short: 'Limonades' },
  { id: 'cures', label: 'Cures & packs', short: 'Cures & packs' }
];

/* colors.liquid / colors.deep : couleurs d'illustration des bouteilles
   (décoratives, jamais utilisées pour du texte). */
KHN.products = [
  {
    id: 'sunrise-orange',
    name: 'Sunrise Orange',
    category: 'presses',
    tagline: 'Orange, carotte, curcuma',
    description: "Le réveil en douceur : des oranges juteuses pressées à froid, la rondeur de la carotte et une pointe de curcuma pour un jus solaire et velouté.",
    ingredients: ['Orange', 'Carotte', 'Citron', 'Curcuma'],
    sizes: [{ id: '33', label: '33 cl', price: 4.9 }, { id: '50', label: '50 cl', price: 6.5 }],
    colors: { liquid: '#FB923C', deep: '#EA580C' },
    badge: 'Best-seller',
    popularity: 100
  },
  {
    id: 'green-glow',
    name: 'Green Glow',
    category: 'presses',
    tagline: 'Pomme verte, concombre, épinard, gingembre',
    description: "Un vert vif et désaltérant : pomme verte acidulée, concombre croquant, jeunes pousses d'épinard et un trait de gingembre.",
    ingredients: ['Pomme verte', 'Concombre', 'Épinard', 'Citron', 'Gingembre'],
    sizes: [{ id: '33', label: '33 cl', price: 5.5 }, { id: '50', label: '50 cl', price: 7.2 }],
    colors: { liquid: '#84CC16', deep: '#4D7C0F' },
    badge: 'Best-seller',
    popularity: 95
  },
  {
    id: 'ruby-beet',
    name: 'Ruby Beet',
    category: 'presses',
    tagline: 'Betterave, pomme, citron, gingembre',
    description: "Une couleur rubis profonde et un goût terrien adouci par la pomme. Le citron et le gingembre apportent du peps.",
    ingredients: ['Betterave', 'Pomme', 'Citron', 'Gingembre'],
    sizes: [{ id: '33', label: '33 cl', price: 5.5 }, { id: '50', label: '50 cl', price: 7.2 }],
    colors: { liquid: '#BE185D', deep: '#831843' },
    popularity: 70
  },
  {
    id: 'pink-paradise',
    name: 'Pink Paradise',
    category: 'presses',
    tagline: 'Pastèque, fraise, menthe',
    description: "L'été en bouteille : pastèque gorgée d'eau, fraises mûres et menthe fraîche. Léger, fruité, irrésistible bien frais.",
    ingredients: ['Pastèque', 'Fraise', 'Menthe', 'Citron vert'],
    sizes: [{ id: '33', label: '33 cl', price: 5.2 }, { id: '50', label: '50 cl', price: 6.9 }],
    colors: { liquid: '#FB7185', deep: '#E11D48' },
    badge: 'Nouveau',
    popularity: 80
  },
  {
    id: 'mango-tango',
    name: 'Mango Tango',
    category: 'smoothies',
    tagline: 'Mangue, ananas, passion, banane',
    description: "Un smoothie onctueux aux accents tropicaux : mangue fondante, ananas, fruit de la passion et banane pour la texture.",
    ingredients: ['Mangue', 'Ananas', 'Fruit de la passion', 'Banane'],
    sizes: [{ id: '33', label: '33 cl', price: 5.9 }, { id: '50', label: '50 cl', price: 7.9 }],
    colors: { liquid: '#FBBF24', deep: '#D97706' },
    badge: 'Best-seller',
    popularity: 92
  },
  {
    id: 'berry-bliss',
    name: 'Berry Bliss',
    category: 'smoothies',
    tagline: "Fruits rouges, banane, lait d'avoine",
    description: "Fraise, myrtille et framboise mixées avec de la banane et du lait d'avoine : un smoothie gourmand et velouté.",
    ingredients: ['Fraise', 'Myrtille', 'Framboise', 'Banane', "Lait d'avoine"],
    sizes: [{ id: '33', label: '33 cl', price: 5.9 }, { id: '50', label: '50 cl', price: 7.9 }],
    colors: { liquid: '#A21CAF', deep: '#701A75' },
    allergens: "Contient de l'avoine (gluten).",
    popularity: 75
  },
  {
    id: 'coco-lagoon',
    name: 'Coco Lagoon',
    category: 'smoothies',
    tagline: 'Eau de coco, ananas, citron vert',
    description: "Une boisson lagon ultra-rafraîchissante : eau de coco, ananas pressé et zeste de citron vert.",
    ingredients: ['Eau de coco', 'Ananas', 'Citron vert'],
    sizes: [{ id: '33', label: '33 cl', price: 4.9 }, { id: '50', label: '50 cl', price: 6.5 }],
    colors: { liquid: '#67E8F9', deep: '#0891B2' },
    popularity: 65
  },
  {
    id: 'ginger-kick',
    name: 'Ginger Kick',
    category: 'shots',
    tagline: 'Gingembre, citron, piment de Cayenne',
    description: "Un shot intense et piquant : gingembre frais pressé, citron et une pincée de piment de Cayenne. À boire cul sec !",
    ingredients: ['Gingembre', 'Citron', 'Pomme', 'Piment de Cayenne'],
    sizes: [{ id: '6', label: '6 cl', price: 2.9 }],
    colors: { liquid: '#FDE047', deep: '#CA8A04' },
    popularity: 85
  },
  {
    id: 'golden-shot',
    name: 'Golden Shot',
    category: 'shots',
    tagline: 'Curcuma, orange, poivre noir',
    description: "Doré et épicé : curcuma frais, orange pressée et une touche de poivre noir.",
    ingredients: ['Curcuma', 'Orange', 'Citron', 'Poivre noir'],
    sizes: [{ id: '6', label: '6 cl', price: 2.9 }],
    colors: { liquid: '#F59E0B', deep: '#B45309' },
    popularity: 60
  },
  {
    id: 'citronnade-maison',
    name: 'Citronnade Maison',
    category: 'limonades',
    tagline: "Citron, menthe fraîche, agave",
    description: "Notre limonade signature : citrons pressés, feuilles de menthe et une touche de sirop d'agave. Acidulée juste comme il faut.",
    ingredients: ['Citron', 'Menthe', "Sirop d'agave", 'Eau filtrée'],
    sizes: [{ id: '33', label: '33 cl', price: 3.9 }, { id: '50', label: '50 cl', price: 5.2 }],
    colors: { liquid: '#FEF08A', deep: '#EAB308' },
    popularity: 78
  },
  {
    id: 'hibiscus-fizz',
    name: 'Hibiscus Fizz',
    category: 'limonades',
    tagline: 'Hibiscus, framboise, eau pétillante',
    description: "Une infusion d'hibiscus fleurie, relevée de framboise et allongée d'eau pétillante. Pétillant et peu sucré.",
    ingredients: ['Fleur d\'hibiscus', 'Framboise', 'Citron', 'Eau pétillante'],
    sizes: [{ id: '33', label: '33 cl', price: 3.9 }, { id: '50', label: '50 cl', price: 5.2 }],
    colors: { liquid: '#DB2777', deep: '#9D174D' },
    badge: 'Nouveau',
    popularity: 68
  },
  {
    id: 'pack-decouverte',
    name: 'Pack Découverte',
    category: 'cures',
    tagline: '6 bouteilles de 33 cl assorties',
    description: "Le meilleur moyen de goûter la gamme : Sunrise Orange, Green Glow, Ruby Beet, Pink Paradise, Mango Tango et Citronnade Maison.",
    ingredients: ['6 recettes différentes', '6 × 33 cl'],
    sizes: [{ id: 'pack6', label: '6 × 33 cl', price: 27 }],
    colors: { liquid: '#34D399', deep: '#059669' },
    pack: ['sunrise-orange', 'green-glow', 'mango-tango'],
    badge: 'Idée cadeau',
    popularity: 88
  },
  {
    id: 'cure-3-jours',
    name: 'Cure 3 jours',
    category: 'cures',
    tagline: '18 bouteilles · 6 jus par jour',
    description: "Trois jours de jus pressés à froid, organisés du matin au soir. Livrée réfrigérée avec un guide de dégustation. À intégrer dans une alimentation variée et équilibrée.",
    ingredients: ['18 × 33 cl', '6 recettes', 'Guide de dégustation'],
    sizes: [{ id: 'cure3', label: '18 × 33 cl', price: 79 }],
    colors: { liquid: '#10B981', deep: '#047857' },
    pack: ['green-glow', 'ruby-beet', 'sunrise-orange'],
    popularity: 72
  }
];

/* Saveurs mises en avant dans le sélecteur du hero (page d'accueil) */
KHN.heroFlavors = ['sunrise-orange', 'green-glow', 'ruby-beet', 'mango-tango'];

/* CONTENU D'EXEMPLE — à remplacer par de vrais avis clients vérifiés. */
KHN.testimonials = [
  { name: 'Camille', city: 'Client·e exemple', text: "Le Green Glow est devenu mon rituel du matin. Livré bien frais, bouteille en verre : parfait.", color: '#A7F3D0' },
  { name: 'Yanis', city: 'Client·e exemple', text: "Le Ginger Kick pique juste ce qu'il faut. Commande simple, livraison à l'heure.", color: '#FDE68A' },
  { name: 'Léa', city: 'Client·e exemple', text: "Le Pack Découverte m'a permis de tout goûter. Coup de cœur pour Pink Paradise !", color: '#FBCFE8' }
];

KHN.getProduct = function (id) {
  return KHN.products.find(function (p) { return p.id === id; }) || null;
};
KHN.getCategory = function (id) {
  return KHN.categories.find(function (c) { return c.id === id; }) || null;
};
KHN.minPrice = function (p) {
  return Math.min.apply(null, p.sizes.map(function (s) { return s.price; }));
};
