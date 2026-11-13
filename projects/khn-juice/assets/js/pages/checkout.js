/* KHN JUICE — Commande : formulaire, validation accessible, confirmation
   Règles UI UX Pro Max appliquées : input-labels, required-indicators,
   inline-validation (au blur), error-placement (aria-describedby),
   error-summary + focus-management, submit-feedback, autofill-support,
   input-type-keyboard, progressive-disclosure, multi-step-progress, form-autosave. */
(function () {
  'use strict';
  var KHN = window.KHN;
  var esc = KHN.escape;

  var mount = document.querySelector('[data-checkout]');
  var stepper = document.querySelector('[data-stepper]');
  var title = document.querySelector('[data-checkout-title]');
  if (!mount) return;

  var DRAFT_KEY = 'khn-juice-checkout-draft';
  var ORDER_KEY = 'khn-juice-last-order';
  var EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  function store(key, val) { try { window.sessionStorage.setItem(key, JSON.stringify(val)); } catch (e) { /* ignore */ } }
  function load(key) { try { return JSON.parse(window.sessionStorage.getItem(key) || 'null'); } catch (e) { return null; } }

  function setStep(n) {
    var steps = ['Panier', 'Informations', 'Confirmation'];
    stepper.innerHTML = steps.map(function (s, i) {
      var idx = i + 1;
      var cls = idx < n ? 'is-done' : '';
      var cur = idx === n ? ' aria-current="step"' : '';
      var dot = idx < n ? KHN.icon('check') : idx;
      var sr = idx < n ? '<span class="visually-hidden"> (terminée)</span>' : '';
      return '<li class="' + cls + '"' + cur + '><span class="dot" aria-hidden="true">' + dot + '</span>' +
        '<span><span class="visually-hidden">Étape ' + idx + ' sur 3 : </span>' + s + sr + '</span></li>';
    }).join('');
  }

  /* ---------- Champs ---------- */
  var FIELDS = [
    { id: 'prenom', label: 'Prénom', type: 'text', ac: 'given-name', required: true,
      check: function (v) { return v ? '' : 'Indiquez votre prénom.'; } },
    { id: 'nom', label: 'Nom', type: 'text', ac: 'family-name', required: true,
      check: function (v) { return v ? '' : 'Indiquez votre nom.'; } },
    { id: 'email', label: 'E-mail', type: 'email', ac: 'email', required: true, hint: 'Pour recevoir la confirmation de commande.',
      check: function (v) { return !v ? 'Indiquez votre adresse e-mail.' : (EMAIL_RE.test(v) ? '' : 'Adresse e-mail incomplète. Exemple : prenom@exemple.fr'); } },
    { id: 'tel', label: 'Téléphone', type: 'tel', ac: 'tel', required: true, hint: 'Le livreur vous appelle en cas de besoin.',
      check: function (v) {
        if (!v) return 'Indiquez votre numéro de téléphone.';
        var digits = v.replace(/[^\d]/g, '');
        return /^[+\d][\d\s.()-]*$/.test(v) && digits.length >= 8 && digits.length <= 15 ? '' : 'Numéro invalide : saisissez au moins 8 chiffres, par exemple 06 12 34 56 78.';
      } },
    { id: 'adresse', label: 'Adresse', type: 'text', ac: 'address-line1', required: true, delivery: true,
      check: function (v) { return v ? '' : 'Indiquez votre adresse de livraison (numéro et rue).'; } },
    { id: 'complement', label: 'Complément d\'adresse', type: 'text', ac: 'address-line2', required: false, delivery: true, hint: 'Bâtiment, étage, digicode…',
      check: function () { return ''; } },
    { id: 'cp', label: 'Code postal', type: 'text', ac: 'postal-code', inputmode: 'numeric', required: true, delivery: true,
      check: function (v) { return !v ? 'Indiquez votre code postal.' : (/^\d{5}$/.test(v) ? '' : 'Le code postal doit contenir 5 chiffres, par exemple 75011.'); } },
    { id: 'ville', label: 'Ville', type: 'text', ac: 'address-level2', required: true, delivery: true,
      check: function (v) { return v ? '' : 'Indiquez votre ville.'; } },
    { id: 'creneau', label: 'Créneau souhaité', type: 'select', required: true,
      options: ['Demain, 8 h – 12 h', 'Demain, 14 h – 18 h', 'Après-demain, 8 h – 12 h', 'Après-demain, 14 h – 18 h'],
      check: function (v) { return v ? '' : 'Choisissez un créneau de livraison ou de retrait.'; } },
    { id: 'cgv', label: 'J\'ai lu et j\'accepte les conditions générales de vente', type: 'checkbox', required: true,
      check: function (v) { return v ? '' : 'Cochez cette case pour accepter les conditions de vente.'; } }
  ];
  function field(id) { return FIELDS.find(function (f) { return f.id === id; }); }

  function fieldHTML(f) {
    var req = f.required ? ' <span class="req" aria-hidden="true">*</span>' : ' <span class="field-hint">(facultatif)</span>';
    var describedBy = (f.hint ? f.id + '-hint ' : '') + f.id + '-error';
    var hint = f.hint ? '<p class="field-hint" id="' + f.id + '-hint">' + esc(f.hint) + '</p>' : '';
    var error = '<p class="field-error" id="' + f.id + '-error"></p>';
    var reqAttr = f.required ? ' required aria-required="true"' : '';
    if (f.type === 'checkbox') {
      return '<div class="field"><label class="checkbox"><input type="checkbox" id="' + f.id + '" name="' + f.id + '"' + reqAttr +
        ' aria-describedby="' + describedBy + '"><span>' + esc(f.label) + req + '</span></label>' + error + '</div>';
    }
    if (f.type === 'select') {
      return '<div class="field"><label class="label" for="' + f.id + '">' + esc(f.label) + req + '</label>' + hint +
        '<select class="select" id="' + f.id + '" name="' + f.id + '"' + reqAttr + ' aria-describedby="' + describedBy + '">' +
        '<option value="">Choisissez un créneau</option>' +
        f.options.map(function (o) { return '<option>' + esc(o) + '</option>'; }).join('') + '</select>' + error + '</div>';
    }
    return '<div class="field"><label class="label" for="' + f.id + '">' + esc(f.label) + req + '</label>' + hint +
      '<input class="input" id="' + f.id + '" name="' + f.id + '" type="' + f.type + '" autocomplete="' + f.ac + '"' +
      (f.inputmode ? ' inputmode="' + f.inputmode + '"' : '') + reqAttr + ' aria-describedby="' + describedBy + '">' + error + '</div>';
  }

  /* ---------- Récapitulatif ---------- */
  function mode() {
    var r = mount.querySelector('input[name="mode"]:checked');
    return r ? r.value : 'livraison';
  }

  function summaryHTML() {
    var lines = KHN.cart.lines();
    var sub = KHN.cart.subtotal();
    var ship = KHN.cart.shipping(mode());
    return '<h2 id="summary-title">Récapitulatif</h2>' +
      '<ul class="line-items" role="list">' + lines.map(function (l) {
        return '<li class="line-item" style="--tint:' + l.product.colors.liquid + '">' +
          '<div class="line-thumb">' + KHN.visual(l.product) + '</div>' +
          '<div class="line-info"><span class="line-name">' + esc(l.product.name) + '</span>' +
          '<span class="line-meta">' + esc(l.sizeLabel) + ' · ' + l.qty + ' × ' + KHN.money(l.unit) + '</span></div>' +
          '<span class="line-price">' + KHN.money(l.total) + '</span></li>';
      }).join('') + '</ul>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-cart-open>' + KHN.icon('shopping-bag') + 'Modifier le panier</button>' +
      '<dl class="totals">' +
        '<div><dt>Sous-total</dt><dd>' + KHN.money(sub) + '</dd></div>' +
        '<div><dt>' + (mode() === 'retrait' ? 'Retrait en boutique' : 'Livraison réfrigérée') + '</dt><dd>' + (ship === 0 ? 'Offerte' : KHN.money(ship)) + '</dd></div>' +
        '<div class="total"><dt>Total TTC</dt><dd>' + KHN.money(sub + ship) + '</dd></div>' +
      '</dl>' +
      (mode() === 'livraison' && ship > 0
        ? '<p class="notice">' + KHN.icon('truck') + '<span>Plus que <strong class="tabular">' + KHN.money(KHN.config.freeShippingThreshold - sub) + '</strong> pour profiter de la livraison offerte.</span></p>'
        : '');
  }

  function renderSummary() {
    var el = mount.querySelector('[data-summary]');
    if (el) el.innerHTML = summaryHTML();
    var ship = KHN.cart.shipping('livraison');
    var shipPrice = mount.querySelector('[data-ship-price]');
    if (shipPrice) shipPrice.textContent = ship === 0 ? 'Offerte' : KHN.money(ship);
  }

  /* ---------- États de la page ---------- */
  function renderEmpty() {
    setStep(1);
    title.textContent = 'Finaliser ma commande';
    mount.innerHTML = '<div class="empty-state">' +
      '<h2>Votre panier est vide</h2>' +
      '<p>Ajoutez quelques bouteilles avant de passer commande : nos jus sont pressés chaque matin.</p>' +
      '<a class="btn btn-primary" href="boutique.html">Découvrir la boutique ' + KHN.icon('arrow-right') + '</a></div>';
  }

  function renderConfirmation(order) {
    setStep(3);
    title.textContent = 'Commande confirmée';
    document.title = 'Commande confirmée — KHN JUICE';
    mount.innerHTML = '<section class="confirmation" aria-labelledby="confirm-title">' +
      '<svg class="check-anim" viewBox="0 0 96 96" aria-hidden="true"><circle cx="48" cy="48" r="46"/><path d="M28 50l13 13 27-29"/></svg>' +
      '<h2 id="confirm-title" tabindex="-1">Merci ' + esc(order.prenom) + ' !</h2>' +
      '<p>Votre commande <span class="order-num">' + esc(order.number) + '</span> est enregistrée pour le créneau « ' + esc(order.creneau) + ' » (' +
        (order.mode === 'retrait' ? 'retrait en boutique' : 'livraison à domicile') + ').</p>' +
      '<p>Total : <strong class="tabular">' + KHN.money(order.total) + '</strong> · ' + order.count + (order.count > 1 ? ' articles' : ' article') +
        ' · paiement ' + (order.mode === 'retrait' ? 'au retrait' : 'à la livraison') + '.</p>' +
      (KHN.config.demoMode ? '<p class="notice">' + KHN.icon('info') + '<span>Mode démonstration : cette commande est simulée. Aucune donnée n\'a été envoyée et aucun paiement n\'est demandé.</span></p>' : '') +
      '<a class="btn btn-primary" href="boutique.html">Continuer mes achats ' + KHN.icon('arrow-right') + '</a>' +
      '</section>';
    var h = document.getElementById('confirm-title');
    window.scrollTo({ top: 0, behavior: KHN.motion && KHN.motion.ok() ? 'smooth' : 'auto' });
    h.focus({ preventScroll: true });
    if (KHN.motion && KHN.motion.ok()) {
      window.gsap.from(mount.querySelectorAll('.confirmation > *'), { y: 18, opacity: 0, duration: 0.45, stagger: 0.07, ease: 'power2.out', clearProps: 'opacity,transform' });
    }
  }

  function renderForm() {
    setStep(2);
    title.textContent = 'Finaliser ma commande';
    var byId = function (id) { return fieldHTML(field(id)); };
    mount.innerHTML =
      '<div class="checkout">' +
        '<form class="checkout-form" novalidate data-checkout-form aria-labelledby="checkout-title">' +
          '<div data-error-summary></div>' +
          '<p class="field-hint">Les champs marqués d\'un astérisque (<span class="req" aria-hidden="true">*</span>) sont obligatoires.</p>' +
          '<fieldset class="form-section"><legend><span class="legend-num" aria-hidden="true">1</span>Vos coordonnées</legend>' +
            '<div class="grid-2">' + byId('prenom') + byId('nom') + '</div>' +
            '<div class="grid-2">' + byId('email') + byId('tel') + '</div>' +
          '</fieldset>' +
          '<fieldset class="form-section"><legend><span class="legend-num" aria-hidden="true">2</span>Réception</legend>' +
            '<fieldset class="field"><legend class="label">Mode de réception</legend><div class="choice-group choice-group--row">' +
              '<label class="choice"><input type="radio" name="mode" value="livraison" checked>' +
                '<span class="choice-text"><span class="choice-title">Livraison à domicile</span><span class="choice-desc">Réfrigérée, offerte dès ' + KHN.money(KHN.config.freeShippingThreshold) + '</span></span>' +
                '<span class="choice-price" data-ship-price></span></label>' +
              '<label class="choice"><input type="radio" name="mode" value="retrait">' +
                '<span class="choice-text"><span class="choice-title">Retrait en boutique</span><span class="choice-desc">Prêt au créneau choisi</span></span>' +
                '<span class="choice-price">Gratuit</span></label>' +
            '</div></fieldset>' +
            '<div class="stack-lg" data-delivery-fields style="gap:var(--space-md)">' +
              byId('adresse') + byId('complement') +
              '<div class="grid-2">' + byId('cp') + byId('ville') + '</div>' +
            '</div>' +
            byId('creneau') +
          '</fieldset>' +
          '<fieldset class="form-section"><legend><span class="legend-num" aria-hidden="true">3</span>Paiement</legend>' +
            '<p class="notice">' + KHN.icon('info') + '<span>Paiement à la livraison ou au retrait, par carte bancaire ou en espèces.' +
              (KHN.config.demoMode ? ' <strong>Mode démonstration : la commande est simulée, aucun paiement n\'est encaissé.</strong>' : '') + '</span></p>' +
            byId('cgv') +
          '</fieldset>' +
          '<button type="submit" class="btn btn-primary btn-block" data-submit>' +
            '<span data-submit-label>Valider la commande</span></button>' +
        '</form>' +
        '<aside class="summary" aria-labelledby="summary-title" data-summary></aside>' +
      '</div>';
    renderSummary();
    restoreDraft();
    toggleDelivery();
    bindForm();
  }

  /* ---------- Brouillon (form-autosave) ---------- */
  function saveDraft() {
    var form = mount.querySelector('[data-checkout-form]');
    if (!form) return;
    var data = { mode: mode() };
    FIELDS.forEach(function (f) { if (f.type !== 'checkbox') data[f.id] = form.elements[f.id].value; });
    store(DRAFT_KEY, data);
  }
  function restoreDraft() {
    var data = load(DRAFT_KEY);
    var form = mount.querySelector('[data-checkout-form]');
    if (!data || !form) return;
    FIELDS.forEach(function (f) { if (f.type !== 'checkbox' && data[f.id] != null) form.elements[f.id].value = data[f.id]; });
    var r = form.querySelector('input[name="mode"][value="' + data.mode + '"]');
    if (r) r.checked = true;
    renderSummary();
  }

  /* ---------- Validation ---------- */
  function valueOf(f) {
    var el = document.getElementById(f.id);
    return f.type === 'checkbox' ? el.checked : el.value.trim();
  }
  function isActive(f) { return !f.delivery || mode() === 'livraison'; }

  function validateField(f) {
    var el = document.getElementById(f.id);
    var errEl = document.getElementById(f.id + '-error');
    var msg = isActive(f) ? f.check(valueOf(f)) : '';
    el.setAttribute('aria-invalid', msg ? 'true' : 'false');
    errEl.innerHTML = msg ? KHN.icon('warning-circle') + '<span>' + esc(msg) + '</span>' : '';
    return msg;
  }

  function showSummary(errors) {
    var box = mount.querySelector('[data-error-summary]');
    if (!errors.length) { box.innerHTML = ''; return; }
    box.innerHTML = '<div class="error-summary" tabindex="-1" role="group" aria-labelledby="err-title">' +
      '<h2 id="err-title">' + KHN.icon('warning-circle', 'icon--lg') +
        (errors.length > 1 ? errors.length + ' champs sont à corriger' : '1 champ est à corriger') + '</h2>' +
      '<ul>' + errors.map(function (e) { return '<li><a href="#' + e.id + '" data-fix="' + e.id + '">' + esc(e.msg) + '</a></li>'; }).join('') + '</ul></div>';
    var el = box.firstChild;
    el.focus();
    if (KHN.motion && KHN.motion.ok()) {
      window.gsap.fromTo(el, { x: -10 }, { x: 0, duration: 0.5, ease: 'elastic.out(1, 0.35)', clearProps: 'transform' });
    }
  }

  function toggleDelivery() {
    var box = mount.querySelector('[data-delivery-fields]');
    var on = mode() === 'livraison';
    box.hidden = !on;
    FIELDS.filter(function (f) { return f.delivery; }).forEach(function (f) {
      var el = document.getElementById(f.id);
      el.disabled = !on; // champs masqués exclus de la validation et de la saisie
      if (!on) validateField(f);
    });
    if (mount.querySelector('#creneau')) mount.querySelector('label[for="creneau"]').firstChild.nodeValue = on ? 'Créneau de livraison ' : 'Créneau de retrait ';
  }

  function bindForm() {
    var form = mount.querySelector('[data-checkout-form]');
    var submitted = false;

    form.addEventListener('focusout', function (e) {
      var f = field(e.target.id);
      if (!f) return;
      // inline-validation : au blur, seulement si l'utilisateur a saisi quelque chose (ou après une tentative d'envoi)
      if (submitted || (f.type !== 'checkbox' && valueOf(f))) validateField(f);
    });
    form.addEventListener('input', function (e) {
      var f = field(e.target.id);
      if (f && e.target.getAttribute('aria-invalid') === 'true') validateField(f);
      saveDraft();
    });
    form.addEventListener('change', function (e) {
      if (e.target.name === 'mode') { toggleDelivery(); renderSummary(); }
      var f = field(e.target.id);
      if (f && (f.type === 'checkbox' || f.type === 'select') && (submitted || e.target.getAttribute('aria-invalid') === 'true')) validateField(f);
      saveDraft();
    });
    mount.addEventListener('click', function (e) {
      var a = e.target.closest('[data-fix]');
      if (!a) return;
      e.preventDefault();
      var el = document.getElementById(a.dataset.fix);
      if (el) { el.focus(); el.scrollIntoView({ block: 'center', behavior: KHN.motion && KHN.motion.ok() ? 'smooth' : 'auto' }); }
    });

    var busy = false;
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (busy) return;
      submitted = true;
      var errors = [];
      FIELDS.forEach(function (f) {
        var msg = validateField(f);
        if (msg) errors.push({ id: f.id, msg: msg });
      });
      showSummary(errors);
      if (errors.length) return;
      if (!KHN.cart.count()) { renderEmpty(); return; }

      // submit-feedback : chargement puis succès
      busy = true;
      var btn = form.querySelector('[data-submit]');
      btn.setAttribute('aria-busy', 'true');
      btn.setAttribute('aria-disabled', 'true');
      btn.innerHTML = '<span class="spinner" aria-hidden="true"></span><span>Validation en cours…</span>';
      KHN.announce('Validation de la commande en cours.');

      window.setTimeout(function () {
        var d = new Date();
        var order = {
          number: 'KHN-' + String(d.getFullYear()).slice(2) + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0') + '-' + Math.floor(1000 + Math.random() * 9000),
          prenom: valueOf(field('prenom')),
          creneau: valueOf(field('creneau')),
          mode: mode(),
          count: KHN.cart.count(),
          total: KHN.cart.subtotal() + KHN.cart.shipping(mode())
        };
        store(ORDER_KEY, order);
        try { window.sessionStorage.removeItem(DRAFT_KEY); } catch (err) { /* ignore */ }
        confirmed = true;
        KHN.cart.clear();
        window.history.replaceState(null, '', window.location.pathname + '?confirmation=1');
        renderConfirmation(order);
      }, 900);
    });
  }

  /* ---------- Démarrage ---------- */
  var confirmed = false;
  var params = new URLSearchParams(window.location.search);
  var last = load(ORDER_KEY);
  if (params.get('confirmation') === '1' && last) {
    confirmed = true;
    renderConfirmation(last);
  } else if (!KHN.cart.count()) {
    renderEmpty();
  } else {
    renderForm();
  }

  // Le panier peut être modifié depuis le tiroir : on garde le récapitulatif à jour
  document.addEventListener('khn:cart', function () {
    if (confirmed) return;
    if (!KHN.cart.count()) { renderEmpty(); return; }
    if (!mount.querySelector('[data-checkout-form]')) renderForm();
    else renderSummary();
  });
})();
