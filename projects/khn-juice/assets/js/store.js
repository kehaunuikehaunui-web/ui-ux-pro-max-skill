/* ==========================================================================
   KHN JUICE — Panier (état + persistance localStorage)
   Émet l'évènement `khn:cart` sur document à chaque changement.
   ========================================================================== */
(function () {
  'use strict';
  var KHN = window.KHN;
  var STORAGE_KEY = 'khn-juice-cart-v1';
  var MAX_QTY = 99;

  var moneyFmt = new Intl.NumberFormat(KHN.config.locale, {
    style: 'currency',
    currency: KHN.config.currency
  });
  KHN.money = function (n) { return moneyFmt.format(n); };

  KHN.escape = function (str) {
    return String(str).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  function safeRead() {
    try {
      var raw = window.localStorage.getItem(STORAGE_KEY);
      var parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }
  function safeWrite(items) {
    try { window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items)); } catch (e) { /* stockage indisponible : le panier reste en mémoire */ }
  }

  // Ne garde que les lignes valides (produit et format existants)
  function sanitize(items) {
    return items.filter(function (it) {
      var p = KHN.getProduct(it.id);
      return p && p.sizes.some(function (s) { return s.id === it.size; }) && it.qty > 0;
    }).map(function (it) {
      return { id: it.id, size: it.size, qty: Math.min(MAX_QTY, Math.max(1, parseInt(it.qty, 10) || 1)) };
    });
  }

  var items = sanitize(safeRead());

  function emit(detail) {
    safeWrite(items);
    document.dispatchEvent(new CustomEvent('khn:cart', { detail: detail || {} }));
  }

  function keyOf(id, size) { return id + '|' + size; }

  function lineFor(it) {
    var p = KHN.getProduct(it.id);
    var s = p.sizes.find(function (x) { return x.id === it.size; });
    return {
      key: keyOf(it.id, it.size),
      id: it.id,
      size: it.size,
      sizeLabel: s.label,
      qty: it.qty,
      unit: s.price,
      total: s.price * it.qty,
      product: p
    };
  }

  KHN.cart = {
    MAX_QTY: MAX_QTY,
    lines: function () { return items.map(lineFor); },
    count: function () { return items.reduce(function (n, it) { return n + it.qty; }, 0); },
    subtotal: function () { return items.reduce(function (sum, it) { return sum + lineFor(it).total; }, 0); },
    shipping: function (mode) {
      if (mode === 'retrait') return 0;
      var sub = this.subtotal();
      if (sub === 0) return 0;
      return sub >= KHN.config.freeShippingThreshold ? 0 : KHN.config.shippingFee;
    },
    add: function (id, size, qty) {
      var p = KHN.getProduct(id);
      if (!p) return null;
      size = size || p.sizes[0].id;
      qty = Math.max(1, parseInt(qty, 10) || 1);
      var existing = items.find(function (it) { return it.id === id && it.size === size; });
      if (existing) existing.qty = Math.min(MAX_QTY, existing.qty + qty);
      else items.push({ id: id, size: size, qty: Math.min(MAX_QTY, qty) });
      emit({ type: 'add', key: keyOf(id, size), qty: qty });
      return lineFor(existing || items[items.length - 1]);
    },
    setQty: function (key, qty) {
      var it = items.find(function (x) { return keyOf(x.id, x.size) === key; });
      if (!it) return;
      qty = parseInt(qty, 10);
      if (!qty || qty < 1) return this.remove(key);
      it.qty = Math.min(MAX_QTY, qty);
      emit({ type: 'qty', key: key });
    },
    remove: function (key) {
      var idx = items.findIndex(function (x) { return keyOf(x.id, x.size) === key; });
      if (idx === -1) return null;
      var removed = items.splice(idx, 1)[0];
      emit({ type: 'remove', key: key, item: removed, index: idx });
      return removed;
    },
    restore: function (item, index) {
      if (!item) return;
      var existing = items.find(function (x) { return x.id === item.id && x.size === item.size; });
      if (existing) existing.qty = Math.min(MAX_QTY, existing.qty + item.qty);
      else items.splice(Math.min(index, items.length), 0, item);
      emit({ type: 'restore', key: keyOf(item.id, item.size) });
    },
    clear: function () {
      items = [];
      emit({ type: 'clear' });
    }
  };

  // Synchronise plusieurs onglets ouverts
  window.addEventListener('storage', function (e) {
    if (e.key !== STORAGE_KEY) return;
    items = sanitize(safeRead());
    document.dispatchEvent(new CustomEvent('khn:cart', { detail: { type: 'sync' } }));
  });
})();
