/* KHN JUICE — Boutique : recherche, filtres, tri (état dans l'URL) */
(function () {
  'use strict';
  var KHN = window.KHN;
  var esc = KHN.escape;

  var form = document.querySelector('[data-shop-form]');
  var input = document.getElementById('q');
  var sortSel = document.getElementById('sort');
  var chipsEl = document.querySelector('[data-chips]');
  var grid = document.querySelector('[data-results]');
  var countEl = document.querySelector('[data-count]');
  var emptyEl = document.querySelector('[data-empty]');
  var subtitle = document.querySelector('[data-shop-subtitle]');
  if (!form || !grid) return;

  var params = new URLSearchParams(window.location.search);
  var state = {
    q: params.get('q') || '',
    cat: KHN.getCategory(params.get('cat')) ? params.get('cat') : 'tout',
    tri: ['populaires', 'prix-asc', 'prix-desc', 'nom'].indexOf(params.get('tri')) > -1 ? params.get('tri') : 'populaires'
  };

  function norm(s) {
    return String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  function results() {
    var q = norm(state.q);
    var list = KHN.products.filter(function (p) {
      if (state.cat !== 'tout' && p.category !== state.cat) return false;
      if (!q) return true;
      var cat = KHN.getCategory(p.category);
      var hay = norm([p.name, p.tagline, p.ingredients.join(' '), cat ? cat.label : ''].join(' '));
      return q.split(/\s+/).every(function (term) { return hay.indexOf(term) > -1; });
    });
    var sorters = {
      'populaires': function (a, b) { return b.popularity - a.popularity; },
      'prix-asc': function (a, b) { return KHN.minPrice(a) - KHN.minPrice(b); },
      'prix-desc': function (a, b) { return KHN.minPrice(b) - KHN.minPrice(a); },
      'nom': function (a, b) { return a.name.localeCompare(b.name, 'fr'); }
    };
    return list.sort(sorters[state.tri]);
  }

  function syncUrl() {
    var p = new URLSearchParams();
    if (state.q) p.set('q', state.q);
    if (state.cat !== 'tout') p.set('cat', state.cat);
    if (state.tri !== 'populaires') p.set('tri', state.tri);
    var qs = p.toString();
    window.history.replaceState(null, '', window.location.pathname + (qs ? '?' + qs : ''));
  }

  function renderChips() {
    var cats = [{ id: 'tout', label: 'Tout' }].concat(KHN.categories);
    chipsEl.innerHTML = cats.map(function (c) {
      var on = c.id === state.cat;
      return '<li><button type="button" class="chip" data-cat="' + c.id + '" aria-pressed="' + on + '">' +
        KHN.icon('check', 'chip-check') + esc(c.label) + '</button></li>';
    }).join('');
  }

  function updateNavCurrent() {
    document.querySelectorAll('.main-nav .nav-link').forEach(function (a) {
      var href = a.getAttribute('href');
      var isCures = href === 'boutique.html?cat=cures';
      var isShop = href === 'boutique.html';
      var current = (state.cat === 'cures' && isCures) || (state.cat !== 'cures' && isShop);
      if (current) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    });
  }

  function emptyHTML() {
    var catLabel = state.cat !== 'tout' ? ' dans « ' + esc(KHN.getCategory(state.cat).label) + ' »' : '';
    return '<div class="empty-state">' +
      '<svg class="empty-illu" viewBox="0 0 200 360" aria-hidden="true"><path d="M76 44H124V70C124 84 166 92 166 130V318C166 334 154 346 138 346H62C46 346 34 334 34 318V130C34 92 76 84 76 70Z" fill="#fff" stroke="#064E3B" stroke-width="6" stroke-dasharray="14 10"/><rect x="72" y="8" width="56" height="40" rx="9" fill="#064E3B"/></svg>' +
      '<h3>Aucune boisson ne correspond à « ' + esc(state.q) + ' »' + catLabel + '</h3>' +
      '<p>Vérifiez l\'orthographe ou essayez un fruit, un légume ou une épice :</p>' +
      '<div class="suggestions">' + ['orange', 'gingembre', 'mangue', 'fraise'].map(function (s) {
        return '<button type="button" class="chip" data-suggest="' + s + '">' + s + '</button>';
      }).join('') + '</div>' +
      '<button type="button" class="btn btn-secondary" data-reset>Réinitialiser les filtres</button>' +
    '</div>';
  }

  var renderToken = 0;
  function render(animate) {
    var list = results();
    var token = ++renderToken;
    var n = list.length;
    countEl.textContent = n === 0 ? 'Aucune boisson trouvée'
      : n + (n > 1 ? ' boissons' : ' boisson') + (state.cat !== 'tout' ? ' · ' + KHN.getCategory(state.cat).label : '');
    if (subtitle) {
      subtitle.textContent = state.cat !== 'tout'
        ? KHN.getCategory(state.cat).label + ' : préparés le matin de l\'envoi, livrés bien frais.'
        : 'Jus pressés à froid, smoothies, shots, limonades et cures : tout est préparé le matin de l\'envoi.';
    }

    function paint() {
      if (token !== renderToken) return; // un rendu plus récent a été demandé
      grid.innerHTML = list.map(function (p) { return '<li>' + KHN.cardHTML(p) + '</li>'; }).join('');
      grid.hidden = n === 0;
      emptyEl.hidden = n !== 0;
      emptyEl.innerHTML = n === 0 ? emptyHTML() : '';
      if (animate && KHN.motion && KHN.motion.staggerIn) KHN.motion.staggerIn(grid, grid.children);
    }
    if (animate && KHN.motion && KHN.motion.fadeOut && grid.children.length) KHN.motion.fadeOut(grid, paint);
    else paint();
  }

  function update(animate) {
    syncUrl();
    updateNavCurrent();
    render(animate);
  }

  // --- Initialisation (synchrone : la grille existe avant l'init des animations)
  input.value = state.q;
  sortSel.value = state.tri;
  renderChips();
  updateNavCurrent();
  render(false);
  if (KHN.motion && KHN.motion.staggerIn) KHN.motion.staggerIn(grid, grid.children);

  // --- Évènements
  form.addEventListener('submit', function (e) { e.preventDefault(); });
  var debounce;
  input.addEventListener('input', function () {
    window.clearTimeout(debounce);
    debounce = window.setTimeout(function () {
      state.q = input.value.trim();
      update(true);
    }, 220);
  });
  sortSel.addEventListener('change', function () { state.tri = sortSel.value; update(true); });
  chipsEl.addEventListener('click', function (e) {
    var b = e.target.closest('[data-cat]');
    if (!b || b.dataset.cat === state.cat) return;
    state.cat = b.dataset.cat;
    chipsEl.querySelectorAll('[data-cat]').forEach(function (x) {
      x.setAttribute('aria-pressed', String(x.dataset.cat === state.cat));
    });
    update(true);
  });
  emptyEl.addEventListener('click', function (e) {
    var s = e.target.closest('[data-suggest]');
    if (s) {
      state.q = s.dataset.suggest;
      state.cat = 'tout';
      input.value = state.q;
      renderChips();
      update(true);
      input.focus();
      return;
    }
    if (e.target.closest('[data-reset]')) {
      state = { q: '', cat: 'tout', tri: 'populaires' };
      input.value = '';
      sortSel.value = state.tri;
      renderChips();
      update(true);
      input.focus();
    }
  });
})();
