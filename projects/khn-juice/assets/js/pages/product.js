/* KHN JUICE — Fiche produit (produit.html?id=...) */
(function () {
  'use strict';
  var KHN = window.KHN;
  var esc = KHN.escape;

  var mount = document.querySelector('[data-pdp]');
  var crumb = document.querySelector('[data-breadcrumb]');
  var relatedSection = document.querySelector('[data-related-section]');
  var relatedGrid = document.querySelector('[data-related]');
  if (!mount) return;

  var id = new URLSearchParams(window.location.search).get('id');
  var p = KHN.getProduct(id);
  var caret = KHN.icon('caret-right');

  if (!p) {
    document.title = 'Boisson introuvable — KHN JUICE';
    crumb.innerHTML = '<li><a href="index.html">Accueil</a></li><li>' + caret + '<a href="boutique.html">Boutique</a></li>' +
      '<li>' + caret + '<span aria-current="page">Introuvable</span></li>';
    mount.innerHTML = '<div class="empty-state">' +
      '<h1>Cette boisson est introuvable</h1>' +
      '<p>Le lien est peut-être incomplet, ou la recette n\'est plus disponible. Toute la gamme vous attend dans la boutique.</p>' +
      '<a class="btn btn-primary" href="boutique.html">Voir la boutique ' + KHN.icon('arrow-right') + '</a></div>';
    if (relatedSection) relatedSection.hidden = true;
    return;
  }

  var cat = KHN.getCategory(p.category);
  document.title = p.name + ' — ' + p.tagline + ' | KHN JUICE';
  var meta = document.querySelector('meta[name="description"]');
  if (meta) meta.setAttribute('content', p.name + ' : ' + p.description);

  crumb.innerHTML =
    '<li><a href="index.html">Accueil</a></li>' +
    '<li>' + caret + '<a href="boutique.html">Boutique</a></li>' +
    '<li>' + caret + '<a href="boutique.html?cat=' + cat.id + '">' + esc(cat.label) + '</a></li>' +
    '<li>' + caret + '<span aria-current="page">' + esc(p.name) + '</span></li>';

  var multi = p.sizes.length > 1;
  var sizesHTML = multi
    ? '<fieldset class="field"><legend class="label">Format</legend><div class="choice-group choice-group--row">' +
        p.sizes.map(function (s, i) {
          return '<label class="choice"><input type="radio" name="size" value="' + s.id + '"' + (i === 0 ? ' checked' : '') + '>' +
            '<span class="choice-text"><span class="choice-title">' + esc(s.label) + '</span>' +
            '<span class="choice-desc">' + (i === 0 ? 'Format individuel' : 'Grand format') + '</span></span>' +
            '<span class="choice-price">' + KHN.money(s.price) + '</span></label>';
        }).join('') + '</div></fieldset>'
    : '<input type="hidden" name="size" value="' + p.sizes[0].id + '">';

  mount.innerHTML =
    '<article class="pdp" style="--tint:' + p.colors.liquid + '">' +
      '<div class="pdp-visual"><div class="hero-blob" aria-hidden="true"></div>' +
        KHN.visual(p, { live: true, bubbles: !p.pack, title: (p.pack ? 'Coffret ' : 'Bouteille de ') + p.name }) +
        KHN.motionToggleHTML() +
      '</div>' +
      '<div class="pdp-info">' +
        '<div class="stack-lg" style="gap:var(--space-sm)">' +
          '<p class="eyebrow">' + esc(cat.label) + '</p>' +
          '<h1>' + esc(p.name) + '</h1>' +
          '<p class="pdp-tagline">' + esc(p.tagline) + '</p>' +
        '</div>' +
        '<p class="pdp-price"><span data-price>' + KHN.money(p.sizes[0].price) + '</span> ' +
          '<small class="pdp-unit" data-price-detail>' + (multi ? '/ ' + esc(p.sizes[0].label) : esc(p.sizes[0].label)) + '</small></p>' +
        '<p class="pdp-desc">' + esc(p.description) + '</p>' +
        '<form class="stack-lg" id="buy-form" novalidate>' +
          sizesHTML +
          '<div class="pdp-buy">' +
            '<div class="field"><label class="label" for="qty">Quantité</label>' +
              '<div class="qty">' +
                '<button type="button" data-qty="-1" aria-label="Diminuer la quantité">' + KHN.icon('minus') + '</button>' +
                '<input id="qty" name="qty" type="number" inputmode="numeric" min="1" max="' + KHN.cart.MAX_QTY + '" value="1">' +
                '<button type="button" data-qty="1" aria-label="Augmenter la quantité">' + KHN.icon('plus') + '</button>' +
              '</div></div>' +
            '<button type="button" class="btn btn-primary add-btn" data-add data-id="' + esc(p.id) + '" data-size="' + p.sizes[0].id + '" data-form="#buy-form">' +
              '<span class="add-btn-content">' + KHN.icon('shopping-bag') + '<span>Ajouter au panier</span></span></button>' +
          '</div>' +
          '<p class="field-hint" data-line-total aria-live="polite"></p>' +
        '</form>' +
        '<div class="stack-lg" style="gap:var(--space-md)">' +
          '<h2 style="font-size:var(--fs-lg)">Ingrédients</h2>' +
          '<ul class="ingredients" role="list">' + p.ingredients.map(function (i) { return '<li>' + esc(i) + '</li>'; }).join('') + '</ul>' +
          (p.allergens ? '<p class="notice">' + KHN.icon('info') + '<span><strong>Allergènes :</strong> ' + esc(p.allergens) + '</span></p>' : '') +
        '</div>' +
        '<ul class="reassurance" role="list">' +
          '<li>' + KHN.icon('truck') + '<div><strong>Livraison réfrigérée</strong><span>Offerte dès ' + KHN.money(KHN.config.freeShippingThreshold) + ', ou retrait gratuit en boutique.</span></div></li>' +
          '<li>' + KHN.icon('snowflake') + '<div><strong>À garder au frais</strong><span>Entre 0 et 4 °C. Date limite indiquée sur chaque bouteille.</span></div></li>' +
          '<li>' + KHN.icon('recycle') + '<div><strong>Bouteille en verre</strong><span>Recyclable à l\'infini.</span></div></li>' +
        '</ul>' +
      '</div>' +
    '</article>';

  if (KHN.observeLoop) KHN.observeLoop(mount.querySelector('.pdp-visual'));

  /* ---------- Prix dynamique, quantité ---------- */
  var formEl = document.getElementById('buy-form');
  var qtyInput = document.getElementById('qty');
  var priceEl = mount.querySelector('[data-price]');
  var detailEl = mount.querySelector('[data-price-detail]');
  var totalEl = mount.querySelector('[data-line-total]');

  function selectedSize() {
    var r = formEl.querySelector('input[name="size"]:checked') || formEl.querySelector('input[name="size"]');
    return p.sizes.find(function (s) { return s.id === r.value; });
  }
  function clampQty() {
    var v = parseInt(qtyInput.value, 10);
    if (!v || v < 1) v = 1;
    if (v > KHN.cart.MAX_QTY) v = KHN.cart.MAX_QTY;
    qtyInput.value = v;
    return v;
  }
  function refresh() {
    var s = selectedSize();
    var q = parseInt(qtyInput.value, 10) || 1;
    priceEl.textContent = KHN.money(s.price);
    if (multi) detailEl.textContent = '/ ' + s.label;
    totalEl.textContent = q > 1 ? q + ' × ' + s.label + ' = ' + KHN.money(s.price * q) : '';
    formEl.querySelector('[data-qty="-1"]').disabled = q <= 1;
    formEl.querySelector('[data-qty="1"]').disabled = q >= KHN.cart.MAX_QTY;
  }
  formEl.addEventListener('change', refresh);
  qtyInput.addEventListener('input', refresh);
  qtyInput.addEventListener('blur', function () { clampQty(); refresh(); });
  formEl.addEventListener('click', function (e) {
    var b = e.target.closest('[data-qty]');
    if (!b) return;
    qtyInput.value = Math.min(KHN.cart.MAX_QTY, Math.max(1, clampQty() + parseInt(b.dataset.qty, 10)));
    refresh();
    if (b.disabled) qtyInput.focus(); // le bouton vient d'être désactivé : on ne perd pas le focus
  });
  formEl.addEventListener('submit', function (e) { e.preventDefault(); });
  refresh();

  /* ---------- Suggestions ---------- */
  if (relatedGrid) {
    var related = KHN.products.filter(function (x) { return x.id !== p.id; })
      .sort(function (a, b) {
        var sa = (a.category === p.category ? 1000 : 0) + a.popularity;
        var sb = (b.category === p.category ? 1000 : 0) + b.popularity;
        return sb - sa;
      }).slice(0, 4);
    relatedGrid.innerHTML = related.map(function (x) { return '<li>' + KHN.cardHTML(x) + '</li>'; }).join('');
  }

  /* ---------- Entrée de la bouteille ---------- */
  if (KHN.motion && KHN.motion.ok()) {
    window.gsap.from(mount.querySelector('.pdp-visual > svg'), {
      y: 40, rotation: -6, opacity: 0, duration: 0.7, ease: 'back.out(1.4)', clearProps: 'opacity,transform'
    });
    window.gsap.from(mount.querySelectorAll('.pdp-info > *'), {
      y: 20, opacity: 0, duration: 0.5, stagger: 0.06, ease: 'power2.out', clearProps: 'opacity,transform'
    });
  }
})();
