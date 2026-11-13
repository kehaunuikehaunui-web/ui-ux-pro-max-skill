/* KHN JUICE — Page d'accueil */
(function () {
  'use strict';
  var KHN = window.KHN;
  var esc = KHN.escape;

  /* ---------- Hero : bouteille + sélecteur de saveur ---------- */
  var heroBottle = document.querySelector('[data-hero-bottle]');
  var heroLink = document.querySelector('[data-hero-link]');
  var picker = document.querySelector('[data-flavor-list]');
  var hero = document.querySelector('[data-hero]');
  var flavors = KHN.heroFlavors.map(KHN.getProduct).filter(Boolean);
  var current = flavors[0];

  function paintHeroLink(p) {
    heroLink.href = 'produit.html?id=' + encodeURIComponent(p.id);
    heroLink.innerHTML = '<span>' + esc(p.name) + '</span><span class="price">' + KHN.priceHTML(p) + '</span>' + KHN.icon('arrow-right');
  }

  function renderHero() {
    if (!heroBottle) return;
    heroBottle.innerHTML = KHN.bottleSVG(current, { live: true, bubbles: true, title: 'Bouteille de ' + current.name });
    hero.style.setProperty('--hero-flavor', current.colors.liquid);
    paintHeroLink(current);
    picker.innerHTML = flavors.map(function (p, i) {
      return '<li><button type="button" class="flavor-chip" data-flavor="' + esc(p.id) + '" aria-pressed="' + (i === 0) + '">' +
        '<span class="flavor-dot" style="--dot:' + p.colors.liquid + '" aria-hidden="true"></span>' + esc(p.name) + '</button></li>';
    }).join('');
  }

  function setFlavor(id) {
    var p = KHN.getProduct(id);
    if (!p || p === current) return;
    current = p;
    picker.querySelectorAll('[data-flavor]').forEach(function (b) {
      b.setAttribute('aria-pressed', String(b.dataset.flavor === id));
    });
    var svg = heroBottle.querySelector('svg');
    // La couleur glisse via transition CSS (stop-color / fill) ; texte de l'étiquette mis à jour
    svg.style.setProperty('--liquid', p.colors.liquid);
    svg.style.setProperty('--deep', p.colors.deep);
    svg.querySelector('title').textContent = 'Bouteille de ' + p.name;
    var texts = svg.querySelectorAll('text');
    // texts[0] = KHN, puis le nom (1 ou 2 lignes), puis le format : on régénère la bouteille si le nombre de lignes change
    var fresh = document.createElement('div');
    fresh.innerHTML = KHN.bottleSVG(p, { live: true, bubbles: true, title: 'Bouteille de ' + p.name });
    var freshTexts = fresh.querySelectorAll('text');
    if (freshTexts.length === texts.length) {
      texts.forEach(function (t, i) { if (i > 0) t.textContent = freshTexts[i].textContent; });
    } else {
      heroBottle.innerHTML = fresh.innerHTML;
      svg = heroBottle.querySelector('svg');
    }
    hero.style.setProperty('--hero-flavor', p.colors.liquid);
    paintHeroLink(p);

    // Mouvement porteur de sens : la bouteille « se secoue » quand la saveur change
    if (KHN.motion && KHN.motion.ok()) {
      var g = window.gsap;
      g.killTweensOf(heroBottle);
      g.timeline()
        .to(heroBottle, { rotation: -7, y: -14, duration: 0.18, ease: 'power2.out' })
        .to(heroBottle, { rotation: 0, y: 0, duration: 0.6, ease: 'elastic.out(1, 0.45)' });
    }
  }

  if (picker) {
    renderHero();
    picker.addEventListener('click', function (e) {
      var b = e.target.closest('[data-flavor]');
      if (b) setFlavor(b.dataset.flavor);
    });
  }

  /* ---------- Décors fruités (calques parallaxe) ---------- */
  var deco = document.querySelector('[data-hero-deco]');
  if (deco) {
    var items = [
      { html: KHN.deco.slice('#EA580C', '#FDBA74'), style: 'top:4%;left:6%;--size:84px', depth: 3 },
      { html: KHN.deco.leaf('#10B981'), style: 'top:12%;right:4%;--size:70px', depth: 2 },
      { html: KHN.deco.slice('#65A30D', '#D9F99D'), style: 'bottom:16%;left:0;--size:64px', depth: 1 },
      { html: KHN.deco.berry('#E11D48'), style: 'bottom:22%;right:6%;--size:56px', depth: 2.5 }
    ];
    deco.innerHTML = items.map(function (it) {
      return '<div class="deco" style="' + it.style + '" data-parallax="' + it.depth + '">' + it.html + '</div>';
    }).join('');
  }

  /* ---------- Best-sellers ---------- */
  var best = document.querySelector('[data-bestsellers]');
  if (best) {
    var top = KHN.products.filter(function (p) { return !p.pack; })
      .sort(function (a, b) { return b.popularity - a.popularity; }).slice(0, 4);
    best.innerHTML = top.map(function (p) { return '<li>' + KHN.cardHTML(p) + '</li>'; }).join('');
  }

  /* ---------- Bloc cure ---------- */
  var cureVisual = document.querySelector('[data-cure-visual]');
  if (cureVisual) {
    var cure = KHN.getProduct('cure-3-jours');
    cureVisual.innerHTML = KHN.packSVG(cure, { title: 'Coffret Cure 3 jours avec trois bouteilles' });
    var cureBtn = document.querySelector('[data-cure-add]');
    if (cureBtn) cureBtn.setAttribute('aria-label', 'Ajouter la Cure 3 jours (' + KHN.money(cure.sizes[0].price) + ') au panier');
    var curePrice = document.querySelector('[data-cure-price]');
    if (curePrice) curePrice.textContent = KHN.money(cure.sizes[0].price);
  }

  /* ---------- Avis (contenu d'exemple) ---------- */
  var testi = document.querySelector('[data-testimonials]');
  if (testi) {
    var stars = '<div class="stars" role="img" aria-label="Note : 5 sur 5">' +
      [1, 2, 3, 4, 5].map(function () { return KHN.icon('star-fill'); }).join('') + '</div>';
    testi.innerHTML = KHN.testimonials.map(function (t) {
      return '<li><figure class="testimonial">' + stars +
        '<blockquote><p>« ' + esc(t.text) + ' »</p></blockquote>' +
        '<figcaption><span class="avatar" style="--av:' + t.color + '" aria-hidden="true">' + esc(t.name.charAt(0)) + '</span>' +
        '<span>' + esc(t.name) + '<small>' + esc(t.city) + '</small></span></figcaption></figure></li>';
    }).join('');
  }
})();
