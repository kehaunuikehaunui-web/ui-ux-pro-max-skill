/* ==========================================================================
   KHN JUICE — Composants partagés
   Visuels SVG des bouteilles, cartes produit, en-tête, panier (dialog),
   toasts, statut accessible, newsletter, bandeau défilant.
   ========================================================================== */
(function () {
  'use strict';
  var KHN = window.KHN;
  var esc = KHN.escape;
  var uidCounter = 0;
  function uid(prefix) { uidCounter += 1; return (prefix || 'k') + uidCounter; }

  /* ---------- Icônes (sprite Phosphor injecté par icons.js) ---------- */
  KHN.icon = function (name, cls) {
    return '<svg class="icon ' + (cls || '') + '" aria-hidden="true" focusable="false"><use href="#i-' + name + '"></use></svg>';
  };

  /* ---------- Logo ---------- */
  KHN.logoMark = function (cls) {
    return '<svg class="logo-mark ' + (cls || '') + '" viewBox="0 0 64 64" aria-hidden="true" focusable="false">' +
      '<rect width="64" height="64" rx="18" fill="#064E3B"/>' +
      '<circle cx="32" cy="35" r="19" fill="#EA580C"/>' +
      '<circle cx="32" cy="35" r="14.5" fill="#FDBA74"/>' +
      '<g stroke="#EA580C" stroke-width="2.4" stroke-linecap="round">' +
      '<path d="M32 35V21.5M32 35l9.5-9.5M32 35h13.5M32 35l9.5 9.5M32 35v13.5M32 35l-9.5 9.5M32 35H18.5M32 35l-9.5-9.5"/></g>' +
      '<path d="M33 17c2-7 8-10 15-9-1 7-6 11-15 9z" fill="#10B981"/>' +
      '</svg>';
  };

  /* ---------- Bouteille SVG ----------
     opts.live : vague animée en continu · opts.bubbles : bulles · opts.title : texte alternatif
     (sans title, le SVG est décoratif et masqué aux lecteurs d'écran) */
  function splitName(name) {
    if (name.length <= 12) return [name];
    var words = name.split(' ');
    var a = '', b = '';
    words.forEach(function (w) { if ((a + ' ' + w).trim().length <= 12 && !b) a = (a + ' ' + w).trim(); else b = (b + ' ' + w).trim(); });
    return b ? [a, b] : [a];
  }

  function bottleInner(p, opts, id) {
    var sizeLabel = p.sizes[0].label;
    var isShot = p.category === 'shots';
    var level = isShot ? 150 : 118;
    var lines = splitName(p.name.toUpperCase());
    var nameSvg = lines.map(function (l, i) {
      return '<text x="100" y="' + (lines.length === 1 ? 252 : 245 + i * 14) + '" text-anchor="middle" font-family="Rubik, sans-serif" font-weight="600" font-size="11.5" letter-spacing=".6" fill="#064E3B">' + esc(l) + '</text>';
    }).join('');
    var body = 'M76 44H124V70C124 84 166 92 166 130V318C166 334 154 346 138 346H62C46 346 34 334 34 318V130C34 92 76 84 76 70Z';
    var wave = function (y, a) {
      return 'M-100 ' + y + ' Q-75 ' + (y - a) + ' -50 ' + y + ' T0 ' + y + ' T50 ' + y + ' T100 ' + y + ' T150 ' + y + ' T200 ' + y + ' T250 ' + y + ' T300 ' + y + ' T350 ' + y + ' T400 ' + y + ' V360 H-100Z';
    };
    var bubbles = '';
    if (opts.bubbles) {
      [[70, 5, 0], [112, 3.5, 1.3], [92, 4.5, 2.4], [136, 3, 0.7], [80, 3, 3.1]].forEach(function (b) {
        bubbles += '<circle class="bubble" cx="' + b[0] + '" cy="330" r="' + b[1] + '" fill="#fff" style="animation-delay:' + b[2] + 's"/>';
      });
    }
    return '' +
      '<defs>' +
        '<clipPath id="' + id + '-clip"><path d="' + body + '"/></clipPath>' +
        '<linearGradient id="' + id + '-grad" x1="0" y1="0" x2="0" y2="1">' +
          '<stop offset="0" class="stop-liquid" style="stop-color:var(--liquid)"/>' +
          '<stop offset="1" class="stop-deep" style="stop-color:var(--deep)"/>' +
        '</linearGradient>' +
      '</defs>' +
      '<rect x="72" y="8" width="56" height="40" rx="9" fill="#064E3B"/>' +
      '<path d="M82 14v28M92 14v28M102 14v28M112 14v28M122 14v28" stroke="#0B6B52" stroke-width="3"/>' +
      '<g clip-path="url(#' + id + '-clip)">' +
        '<rect x="0" y="40" width="200" height="320" fill="#F8FFFC" opacity=".75"/>' +
        '<path class="wave-back bottle-liquid-deep" d="' + wave(level - 6, 7) + '" style="fill:var(--deep)" opacity=".55"/>' +
        '<path class="wave-front" d="' + wave(level, 9) + '" fill="url(#' + id + '-grad)"/>' +
        bubbles +
        '<rect x="44" y="140" width="11" height="176" rx="5.5" fill="#fff" opacity=".45"/>' +
      '</g>' +
      '<path d="' + body + '" fill="none" stroke="#064E3B" stroke-opacity=".28" stroke-width="3"/>' +
      '<rect x="52" y="196" width="96" height="92" rx="10" fill="#fff"/>' +
      '<text x="100" y="226" text-anchor="middle" font-family="Rubik, sans-serif" font-weight="800" font-size="22" letter-spacing="1" fill="#064E3B">KHN</text>' +
      '<rect class="bottle-liquid" x="80" y="232" width="40" height="3" rx="1.5" style="fill:var(--deep)"/>' +
      nameSvg +
      '<text class="bottle-size" x="100" y="279" text-anchor="middle" font-family="Nunito Sans, sans-serif" font-weight="700" font-size="10.5" fill="#475569">' + esc(sizeLabel) + '</text>';
  }

  KHN.bottleSVG = function (p, opts) {
    opts = opts || {};
    var id = uid('b');
    var a11y = opts.title
      ? 'role="img" aria-labelledby="' + id + '-t"'
      : 'aria-hidden="true" focusable="false"';
    return '<svg class="bottle' + (opts.live ? ' bottle--live' : '') + '" viewBox="0 0 200 360" width="200" height="360" ' + a11y +
      ' style="--liquid:' + p.colors.liquid + ';--deep:' + p.colors.deep + '">' +
      (opts.title ? '<title id="' + id + '-t">' + esc(opts.title) + '</title>' : '') +
      bottleInner(p, opts, id) + '</svg>';
  };

  KHN.packSVG = function (p, opts) {
    opts = opts || {};
    var id = uid('pk');
    var trio = (p.pack || []).map(KHN.getProduct).filter(Boolean);
    var placements = [
      'translate(18 40) rotate(-8 100 180) scale(.78)',
      'translate(202 40) rotate(8 100 180) scale(.78)',
      'translate(95 0) scale(.86)'
    ];
    var bottles = trio.map(function (b, i) {
      var bid = id + '-' + i;
      return '<g transform="' + placements[i] + '" style="--liquid:' + b.colors.liquid + ';--deep:' + b.colors.deep + '">' + bottleInner(b, {}, bid) + '</g>';
    }).join('');
    var a11y = opts.title ? 'role="img" aria-labelledby="' + id + '-t"' : 'aria-hidden="true" focusable="false"';
    return '<svg class="pack bottle' + (opts.live ? ' bottle--live' : '') + '" viewBox="0 0 380 380" width="380" height="380" ' + a11y + '>' +
      (opts.title ? '<title id="' + id + '-t">' + esc(opts.title) + '</title>' : '') +
      bottles +
      '<path d="M24 246h332l-14 120a14 14 0 0 1-14 12H52a14 14 0 0 1-14-12z" fill="#064E3B"/>' +
      '<path d="M24 246h332v18H24z" fill="#0B6B52"/>' +
      '<text x="190" y="322" text-anchor="middle" font-family="Rubik, sans-serif" font-weight="800" font-size="34" letter-spacing="3" fill="#fff">KHN</text>' +
      '<text x="190" y="350" text-anchor="middle" font-family="Rubik, sans-serif" font-weight="600" font-size="15" letter-spacing="4" fill="#A7F3D0">' + esc(p.sizes[0].label.toUpperCase()) + '</text>' +
      '</svg>';
  };

  KHN.visual = function (p, opts) {
    return p.pack ? KHN.packSVG(p, opts) : KHN.bottleSVG(p, opts);
  };

  /* ---------- Décors fruités (décoratifs) ---------- */
  KHN.deco = {
    slice: function (rind, flesh) {
      return '<svg viewBox="0 0 100 100" aria-hidden="true" focusable="false"><circle cx="50" cy="50" r="48" fill="' + rind + '"/><circle cx="50" cy="50" r="40" fill="' + flesh + '"/>' +
        '<g stroke="' + rind + '" stroke-width="3" stroke-linecap="round" opacity=".7"><path d="M50 50V14M50 50l25.5-25.5M50 50h36M50 50l25.5 25.5M50 50v36M50 50L24.5 75.5M50 50H14M50 50L24.5 24.5"/></g>' +
        '<circle cx="50" cy="50" r="5" fill="#fff" opacity=".8"/></svg>';
    },
    leaf: function (c) {
      return '<svg viewBox="0 0 100 100" aria-hidden="true" focusable="false"><path d="M12 88C10 46 40 12 90 10c2 48-30 80-78 78z" fill="' + c + '"/><path d="M14 86C34 62 56 40 84 16" stroke="#fff" stroke-width="3" stroke-linecap="round" fill="none" opacity=".6"/></svg>';
    },
    berry: function (c) {
      return '<svg viewBox="0 0 100 100" aria-hidden="true" focusable="false"><circle cx="50" cy="56" r="38" fill="' + c + '"/><circle cx="38" cy="44" r="7" fill="#fff" opacity=".35"/><path d="M50 20c-6-10-16-12-22-8 8 2 14 6 22 8zm0 0c6-10 16-12 22-8-8 2-14 6-22 8z" fill="#059669"/></svg>';
    }
  };

  /* ---------- Carte produit ---------- */
  KHN.priceHTML = function (p) {
    var min = KHN.minPrice(p);
    return (p.sizes.length > 1 ? '<small>dès </small>' : '') + KHN.money(min);
  };

  KHN.cardHTML = function (p) {
    var cat = KHN.getCategory(p.category);
    var size = p.sizes[0];
    var badge = p.badge
      ? '<span class="badge' + (p.badge === 'Nouveau' ? ' badge--accent' : '') + '">' + esc(p.badge) + '</span>'
      : '';
    return '' +
      '<article class="product-card" data-product="' + esc(p.id) + '" style="--tint:' + p.colors.liquid + '">' +
        '<div class="product-media">' + badge + KHN.visual(p) + '</div>' +
        '<div class="product-body">' +
          '<p class="product-cat">' + esc(cat ? cat.short : '') + '</p>' +
          '<h3 class="product-title"><a href="produit.html?id=' + encodeURIComponent(p.id) + '">' + esc(p.name) + '</a></h3>' +
          '<p class="product-tagline">' + esc(p.tagline) + '</p>' +
        '</div>' +
        '<div class="product-foot">' +
          '<p class="price">' + KHN.priceHTML(p) + '</p>' +
          '<button type="button" class="btn btn-primary btn-sm add-btn" data-add data-id="' + esc(p.id) + '" data-size="' + esc(size.id) + '"' +
            ' aria-label="Ajouter ' + esc(p.name) + ', ' + esc(size.label) + ', au panier">' +
            '<span class="add-btn-content">' + KHN.icon('plus') + '<span>Ajouter</span></span>' +
          '</button>' +
        '</div>' +
      '</article>';
  };

  /* ---------- Statut accessible & toasts ---------- */
  var statusEl, toastRegion;
  function ensureRegions() {
    if (!statusEl) {
      statusEl = document.createElement('div');
      statusEl.className = 'visually-hidden';
      statusEl.setAttribute('role', 'status');
      statusEl.setAttribute('aria-live', 'polite');
      statusEl.setAttribute('aria-atomic', 'true');
      document.body.appendChild(statusEl);
    }
    if (!toastRegion) {
      toastRegion = document.createElement('div');
      toastRegion.className = 'toast-region';
      toastRegion.setAttribute('aria-live', 'polite');
      toastRegion.setAttribute('aria-atomic', 'false');
      document.body.appendChild(toastRegion);
    }
  }
  KHN.announce = function (msg) {
    ensureRegions();
    statusEl.textContent = '';
    window.setTimeout(function () { statusEl.textContent = msg; }, 60);
  };
  KHN.toast = function (msg, icon) {
    ensureRegions();
    var t = document.createElement('div');
    t.className = 'toast';
    t.innerHTML = KHN.icon(icon || 'check-circle', 'icon--lg') + '<span>' + esc(msg) + '</span>';
    toastRegion.appendChild(t);
    // 3 à 5 s (toast-dismiss) ; le toast ne prend jamais le focus
    while (toastRegion.children.length > 2) toastRegion.removeChild(toastRegion.firstChild);
    window.setTimeout(function () {
      t.classList.add('is-leaving');
      window.setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 220);
    }, 4000);
  };

  /* ---------- En-tête ---------- */
  function initHeader() {
    var header = document.querySelector('.site-header');
    if (!header) return;
    var ticking = false;
    function onScroll() {
      if (ticking) return;
      ticking = true;
      window.requestAnimationFrame(function () {
        header.classList.toggle('is-scrolled', window.scrollY > 8);
        ticking = false;
      });
    }
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();

    var toggle = header.querySelector('.menu-toggle');
    var nav = document.getElementById(toggle ? toggle.getAttribute('aria-controls') : '');
    if (!toggle || !nav) return;
    function setOpen(open, returnFocus) {
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? 'Fermer le menu' : 'Ouvrir le menu');
      toggle.innerHTML = KHN.icon(open ? 'x' : 'list', 'icon--lg');
      nav.classList.toggle('is-open', open);
      if (!open && returnFocus) toggle.focus();
    }
    toggle.addEventListener('click', function () {
      setOpen(toggle.getAttribute('aria-expanded') !== 'true');
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && toggle.getAttribute('aria-expanded') === 'true') setOpen(false, true);
    });
    document.addEventListener('click', function (e) {
      if (toggle.getAttribute('aria-expanded') === 'true' && !nav.contains(e.target) && !toggle.contains(e.target)) setOpen(false);
    });
    nav.addEventListener('click', function (e) { if (e.target.closest('a')) setOpen(false); });
    window.matchMedia('(min-width: 880px)').addEventListener('change', function (mq) { if (mq.matches) setOpen(false); });
  }

  function countLabel(n) { return n + (n > 1 ? ' articles' : ' article'); }

  function updateCartButtons() {
    var n = KHN.cart.count();
    document.querySelectorAll('[data-cart-count]').forEach(function (el) { el.textContent = n; });
    document.querySelectorAll('.cart-btn').forEach(function (btn) {
      btn.setAttribute('aria-label', 'Ouvrir le panier, ' + countLabel(n));
    });
  }

  function bumpCart() {
    document.querySelectorAll('.cart-btn').forEach(function (btn) {
      btn.classList.remove('is-bumping');
      void btn.offsetWidth; // redémarre l'animation si clics rapides
      btn.classList.add('is-bumping');
      window.setTimeout(function () { btn.classList.remove('is-bumping'); }, 500);
    });
  }

  /* ---------- Ajout au panier ---------- */
  function flyToCart(fromEl, color) {
    var target = document.querySelector('.site-header .cart-btn');
    if (!target || !KHN.motion || !KHN.motion.ok()) { bumpCart(); return; }
    var a = fromEl.getBoundingClientRect();
    var b = target.getBoundingClientRect();
    var dot = document.createElement('div');
    dot.className = 'fly-dot';
    dot.style.setProperty('--dot', color);
    document.body.appendChild(dot);
    var sx = a.left + a.width / 2 - 11, sy = a.top + a.height / 2 - 11;
    var tx = b.left + b.width - 22, ty = b.top + 4;
    var gsap = window.gsap;
    gsap.set(dot, { x: sx, y: sy, scale: 0.6 });
    gsap.timeline({ onComplete: function () { dot.remove(); bumpCart(); } })
      .to(dot, { scale: 1.15, duration: 0.14, ease: 'power2.out' })
      .to(dot, { x: tx, duration: 0.55, ease: 'power1.inOut' }, '>-0.02')
      .to(dot, { y: Math.min(sy, ty) - 70, duration: 0.25, ease: 'power2.out' }, '<')
      .to(dot, { y: ty, scale: 0.45, duration: 0.3, ease: 'power2.in' }, '>');
  }

  function buttonFeedback(btn) {
    var content = btn.querySelector('.add-btn-content');
    if (!content) return;
    if (!btn.dataset.idleHtml) btn.dataset.idleHtml = content.innerHTML;
    window.clearTimeout(btn._khnTimer); // clics rapides : on remplace l'état précédent
    btn.classList.add('is-added');
    content.innerHTML = KHN.icon('check') + '<span>Ajouté</span>';
    btn._khnTimer = window.setTimeout(function () {
      btn.classList.remove('is-added');
      content.innerHTML = btn.dataset.idleHtml;
    }, 1600);
  }

  function initAddToCart() {
    document.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-add]');
      if (!btn) return;
      var id = btn.dataset.id;
      var size = btn.dataset.size;
      var qty = 1;
      if (btn.dataset.form) {
        var form = document.querySelector(btn.dataset.form);
        if (form) {
          var checked = form.querySelector('input[name="size"]:checked');
          if (checked) size = checked.value;
          var q = form.querySelector('[name="qty"]');
          if (q) qty = parseInt(q.value, 10) || 1;
        }
      }
      var line = KHN.cart.add(id, size, qty);
      if (!line) return;
      buttonFeedback(btn);
      flyToCart(btn, line.product.colors.liquid);
      KHN.toast(line.product.name + ' (' + line.sizeLabel + ')' + (qty > 1 ? ' × ' + qty : '') +
        ' ajouté au panier — ' + countLabel(KHN.cart.count()) + ' au total');
    });
  }

  /* ---------- Panier (dialog natif : piège du focus + Échap) ---------- */
  var dialog, panel, bodyEl, footEl, opener;
  var removedRows = []; // lignes retirées affichées avec « Annuler » (undo-support)

  function buildDialog() {
    dialog = document.createElement('dialog');
    dialog.className = 'cart-dialog';
    dialog.id = 'cart-dialog';
    dialog.setAttribute('aria-labelledby', 'cart-title');
    dialog.innerHTML =
      '<div class="cart-panel">' +
        '<div class="cart-head"><h2 id="cart-title">Votre panier</h2>' +
        '<button type="button" class="icon-btn" data-cart-close aria-label="Fermer le panier">' + KHN.icon('x', 'icon--lg') + '</button></div>' +
        '<div class="cart-body" id="cart-body"></div>' +
        '<div class="cart-foot" id="cart-foot"></div>' +
      '</div>';
    document.body.appendChild(dialog);
    panel = dialog.querySelector('.cart-panel');
    bodyEl = dialog.querySelector('#cart-body');
    footEl = dialog.querySelector('#cart-foot');

    dialog.addEventListener('cancel', function (e) { e.preventDefault(); closeCart(); });
    dialog.addEventListener('click', function (e) {
      if (e.target === dialog) closeCart(); // clic sur le voile
      var close = e.target.closest('[data-cart-close]');
      if (close) closeCart();
      var act = e.target.closest('[data-line-action]');
      if (act) lineAction(act);
    });
  }

  function lineAction(btn) {
    var key = btn.dataset.key;
    var action = btn.dataset.lineAction;
    var line = KHN.cart.lines().find(function (l) { return l.key === key; });
    if (action === 'inc' && line) {
      KHN.cart.setQty(key, line.qty + 1);
      KHN.announce('Quantité de ' + line.product.name + ' : ' + (line.qty + 1));
    } else if (action === 'dec' && line && line.qty > 1) {
      KHN.cart.setQty(key, line.qty - 1);
      KHN.announce('Quantité de ' + line.product.name + ' : ' + (line.qty - 1));
    } else if (action === 'remove' && line) {
      var lines = KHN.cart.lines();
      var index = lines.findIndex(function (l) { return l.key === key; });
      var row = { key: key, name: line.product.name + ' (' + line.sizeLabel + ')', index: index, item: null };
      removedRows.push(row);
      // la cible du focus doit être connue AVANT le re-rendu déclenché par remove()
      focusAfterRender = '[data-line-action="undo"][data-key="' + key + '"]';
      row.item = KHN.cart.remove(key);
      KHN.announce(line.product.name + ' retiré du panier. Bouton Annuler disponible.');
    } else if (action === 'undo') {
      var undoRow = removedRows.find(function (r) { return r.key === key; });
      if (undoRow) {
        removedRows = removedRows.filter(function (r) { return r !== undoRow; });
        focusAfterRender = '[data-line-action="inc"][data-key="' + key + '"]';
        KHN.cart.restore(undoRow.item, undoRow.index);
        KHN.announce(undoRow.name + ' remis dans le panier.');
      }
    }
  }

  var focusAfterRender = null;

  function renderCart() {
    if (!dialog) return;
    var lines = KHN.cart.lines();
    var active = document.activeElement;
    var keepFocus = !focusAfterRender && active && dialog.contains(active) && active.dataset && active.dataset.lineAction
      ? '[data-line-action="' + active.dataset.lineAction + '"][data-key="' + active.dataset.key + '"]'
      : null;

    if (!lines.length && !removedRows.length) {
      bodyEl.innerHTML =
        '<div class="empty-state" style="box-shadow:none;background:transparent">' +
          '<svg class="empty-illu" viewBox="0 0 200 360" aria-hidden="true"><path d="M76 44H124V70C124 84 166 92 166 130V318C166 334 154 346 138 346H62C46 346 34 334 34 318V130C34 92 76 84 76 70Z" fill="#fff" stroke="#064E3B" stroke-width="6" stroke-dasharray="14 10"/><rect x="72" y="8" width="56" height="40" rx="9" fill="#064E3B"/></svg>' +
          '<h3>Votre panier est vide</h3>' +
          '<p>Nos jus sont pressés chaque matin. Choisissez vos favoris et nous les livrons bien frais.</p>' +
          '<a class="btn btn-primary" href="boutique.html">Découvrir la boutique ' + KHN.icon('arrow-right') + '</a>' +
        '</div>';
      footEl.hidden = true;
      return;
    }
    footEl.hidden = false;

    var sub = KHN.cart.subtotal();
    var threshold = KHN.config.freeShippingThreshold;
    var remaining = Math.max(0, threshold - sub);
    var progress = Math.min(1, sub / threshold);
    var shipMsg = remaining > 0
      ? 'Plus que <span class="tabular">' + KHN.money(remaining) + '</span> pour la livraison offerte'
      : KHN.icon('check-circle') + ' Livraison offerte débloquée';

    var rows = lines.map(function (l) {
      var p = l.product;
      return '<li class="line-item" style="--tint:' + p.colors.liquid + '">' +
        '<div class="line-thumb">' + KHN.visual(p) + '</div>' +
        '<div class="line-info">' +
          '<a class="line-name" href="produit.html?id=' + encodeURIComponent(p.id) + '">' + esc(p.name) + '</a>' +
          '<span class="line-meta">' + esc(l.sizeLabel) + ' · ' + KHN.money(l.unit) + '</span>' +
          '<div class="qty qty--sm" role="group" aria-label="Quantité de ' + esc(p.name) + '">' +
            '<button type="button" data-line-action="dec" data-key="' + l.key + '" aria-label="Retirer une unité de ' + esc(p.name) + '"' + (l.qty <= 1 ? ' disabled' : '') + '>' + KHN.icon('minus') + '</button>' +
            '<output aria-label="Quantité">' + l.qty + '</output>' +
            '<button type="button" data-line-action="inc" data-key="' + l.key + '" aria-label="Ajouter une unité de ' + esc(p.name) + '"' + (l.qty >= KHN.cart.MAX_QTY ? ' disabled' : '') + '>' + KHN.icon('plus') + '</button>' +
          '</div>' +
        '</div>' +
        '<div class="line-actions">' +
          '<span class="line-price">' + KHN.money(l.total) + '</span>' +
          '<button type="button" class="icon-btn remove-btn" data-line-action="remove" data-key="' + l.key + '" aria-label="Supprimer ' + esc(p.name) + ' du panier">' + KHN.icon('trash') + '</button>' +
        '</div>' +
      '</li>';
    });
    removedRows.forEach(function (r) {
      var row = '<li class="line-item line-item--removed"><span>' + esc(r.name) + ' retiré.</span>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-line-action="undo" data-key="' + r.key + '">Annuler</button></li>';
      rows.splice(Math.min(r.index, rows.length), 0, row);
    });

    bodyEl.innerHTML =
      (lines.length ? '<div class="ship-progress"><span>' + shipMsg + '</span>' +
        '<div class="progress" aria-hidden="true"><div class="progress-bar" style="--p:' + progress.toFixed(3) + '"></div></div></div>' : '') +
      '<ul class="line-items" role="list">' + rows.join('') + '</ul>';

    var ship = KHN.cart.shipping('livraison');
    footEl.innerHTML =
      '<dl class="totals">' +
        '<div><dt>Sous-total</dt><dd>' + KHN.money(sub) + '</dd></div>' +
        '<div><dt>Livraison à domicile</dt><dd>' + (lines.length ? (ship === 0 ? 'Offerte' : KHN.money(ship)) : '—') + '</dd></div>' +
        '<div class="total"><dt>Total estimé</dt><dd>' + KHN.money(sub + ship) + '</dd></div>' +
      '</dl>' +
      (lines.length
        ? '<a class="btn btn-primary btn-block" href="commande.html">Passer commande ' + KHN.icon('arrow-right') + '</a>'
        : '') +
      '<button type="button" class="btn btn-ghost btn-block" data-cart-close>Continuer mes achats</button>';

    var sel = focusAfterRender || keepFocus;
    focusAfterRender = null;
    if (sel) {
      var el = dialog.querySelector(sel);
      if (el && !el.disabled) el.focus();
      else { var close = dialog.querySelector('[data-cart-close]'); if (close) close.focus(); }
    }
  }

  function openCart(trigger) {
    if (!dialog) buildDialog();
    opener = trigger || document.activeElement;
    removedRows = [];
    renderCart();
    if (dialog.open) return;
    dialog.showModal();
    document.documentElement.style.overflow = 'hidden';
    var gsap = window.gsap;
    if (gsap) gsap.killTweensOf(panel);
    if (gsap && KHN.motion && KHN.motion.ok()) {
      gsap.fromTo(panel, { xPercent: 100 }, { xPercent: 0, duration: 0.42, ease: 'power3.out' });
    } else if (gsap) {
      gsap.set(panel, { xPercent: 0 });
    }
  }

  function finishClose() {
    if (dialog.open) dialog.close();
    document.documentElement.style.overflow = '';
    removedRows = [];
    if (opener && document.contains(opener)) opener.focus();
  }

  function closeCart() {
    if (!dialog || !dialog.open) return;
    var gsap = window.gsap;
    if (gsap) gsap.killTweensOf(panel);
    if (gsap && KHN.motion && KHN.motion.ok()) {
      // sortie plus rapide que l'entrée (~65 %)
      gsap.to(panel, { xPercent: 100, duration: 0.26, ease: 'power2.in', onComplete: finishClose });
    } else {
      finishClose();
    }
  }
  KHN.openCart = openCart;
  KHN.closeCart = closeCart;

  function initCart() {
    document.addEventListener('click', function (e) {
      var t = e.target.closest('[data-cart-open]');
      if (t) { e.preventDefault(); openCart(t); }
    });
    document.addEventListener('khn:cart', function () {
      updateCartButtons();
      if (dialog && dialog.open) renderCart();
    });
    updateCartButtons();
  }

  /* ---------- Newsletter ---------- */
  var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  function initNewsletter() {
    document.querySelectorAll('form[data-newsletter]').forEach(function (form) {
      var input = form.querySelector('input[type="email"]');
      var error = form.querySelector('.field-error');
      function validate() {
        var v = input.value.trim();
        var msg = '';
        if (!v) msg = 'Saisissez votre adresse e-mail pour vous inscrire.';
        else if (!EMAIL_RE.test(v)) msg = "Cette adresse e-mail semble incomplète. Exemple : prenom@exemple.fr";
        input.setAttribute('aria-invalid', msg ? 'true' : 'false');
        error.innerHTML = msg ? KHN.icon('warning-circle') + '<span>' + esc(msg) + '</span>' : '';
        return !msg;
      }
      input.addEventListener('blur', function () { if (input.value.trim()) validate(); });
      input.addEventListener('input', function () { if (input.getAttribute('aria-invalid') === 'true') validate(); });
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (!validate()) { input.focus(); return; }
        var ok = document.createElement('p');
        ok.className = 'form-success';
        ok.setAttribute('role', 'status');
        ok.tabIndex = -1;
        ok.innerHTML = KHN.icon('check-circle', 'icon--lg') + '<span>Merci ! Vous recevrez nos nouvelles recettes à ' + esc(input.value.trim()) + '.' +
          (KHN.config.demoMode ? ' <em>(Mode démo : aucune adresse n\'est enregistrée.)</em>' : '') + '</span>';
        form.replaceWith(ok);
        ok.focus();
      });
    });
  }

  /* ---------- Pause globale des animations en boucle (WCAG 2.2.2, auto-rotation-controls) ----------
     Un seul état pour tout le site (bandeau, vague et bulles des bouteilles), mémorisé
     par visiteur. Tous les boutons [data-motion-toggle] restent synchronisés. */
  var MOTION_KEY = 'khn-juice-motion-paused';
  KHN.motionPaused = function () { return document.documentElement.classList.contains('motion-paused'); };
  KHN.motionToggleHTML = function () {
    return '<button type="button" class="motion-toggle" data-motion-toggle aria-label="Mettre en pause les animations" aria-pressed="' + KHN.motionPaused() + '">' +
      KHN.icon('pause', 'icon-pause') + KHN.icon('play', 'icon-play') + '</button>';
  };
  function syncMotionToggles() {
    var paused = KHN.motionPaused();
    document.querySelectorAll('[data-motion-toggle]').forEach(function (btn) {
      btn.setAttribute('aria-pressed', String(paused));
    });
  }
  function initMotionToggle() {
    syncMotionToggles();
    document.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-motion-toggle]');
      if (!btn) return;
      var paused = document.documentElement.classList.toggle('motion-paused');
      try { window.localStorage.setItem(MOTION_KEY, paused ? '1' : '0'); } catch (err) { /* préférence non mémorisée */ }
      syncMotionToggles();
      KHN.announce(paused ? 'Animations en pause.' : 'Animations relancées.');
    });
    // Économie : les boucles des visuels hors écran sont suspendues (main-thread-budget)
    if ('IntersectionObserver' in window) {
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (en) { en.target.classList.toggle('is-offscreen', !en.isIntersecting); });
      });
      KHN.observeLoop = function (el) { if (el) io.observe(el); };
      document.querySelectorAll('.hero-visual, .pdp-visual').forEach(KHN.observeLoop);
    } else {
      KHN.observeLoop = function () {};
    }
  }

  function initMisc() {
    document.querySelectorAll('[data-year]').forEach(function (el) { el.textContent = new Date().getFullYear(); });
    document.querySelectorAll('[data-logo-mark]').forEach(function (el) { el.innerHTML = KHN.logoMark(); });
    document.querySelectorAll('[data-icon]').forEach(function (el) { el.outerHTML = KHN.icon(el.dataset.icon, el.dataset.iconClass); });
    if (!KHN.config.demoMode) document.querySelectorAll('[data-demo-only]').forEach(function (el) { el.remove(); });
  }

  function init() {
    ensureRegions();
    initMisc();
    initHeader();
    initCart();
    initAddToCart();
    initNewsletter();
    initMotionToggle();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
