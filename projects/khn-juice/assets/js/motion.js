/* ==========================================================================
   KHN JUICE — Animations (GSAP 3.13 + ScrollTrigger)
   Presets issus de UI UX Pro Max (--domain gsap / MASTER.md, tier « Standard ») :
   - Stagger List      : opacity 0, scale .92, y 16, 0.4 s, each .06, grid auto, back.out(1.4)
   - Scroll Reveal     : enfants y 24, 0.5 s, stagger .08, power2.out, start « top 85% »
   - Parallax Scroll   : calques décoratifs uniquement, yPercent (i+1) × -8, scrub .5
   - Page Transition   : voile yPercent, sortie plus rapide que l'entrée
   - Hover             : géré en CSS (transform/opacity, 200-300 ms)
   Tout est désactivé si l'utilisateur demande moins d'animations
   (prefers-reduced-motion) : l'état final est affiché immédiatement.
   ========================================================================== */
(function () {
  'use strict';
  var KHN = window.KHN;
  var gsap = window.gsap;
  var reduceMQ = window.matchMedia('(prefers-reduced-motion: reduce)');

  KHN.motion = {
    ok: function () { return !!gsap && !reduceMQ.matches; }
  };

  if (!gsap) return; // sans GSAP, tout le contenu reste visible et statique
  if (window.ScrollTrigger) gsap.registerPlugin(window.ScrollTrigger);

  /* ---------- Stagger List (MASTER.md) — annulable ---------- */
  KHN.motion.staggerIn = function (container, items, opts) {
    opts = opts || {};
    if (!container) return;
    items = items || container.children;
    if (container._khnTween) {
      container._khnTween.kill();
      if (items.length) gsap.set(items, { clearProps: 'opacity,transform' });
    }
    if (!KHN.motion.ok() || !items.length) return;
    var vars = {
      opacity: 0,
      scale: 0.92,
      y: 16,
      duration: 0.4,
      stagger: { each: 0.06, from: 'start', grid: 'auto' },
      ease: 'back.out(1.4)',
      clearProps: 'opacity,transform'
    };
    if (opts.scroll && window.ScrollTrigger) vars.scrollTrigger = { trigger: container, start: 'top 85%', once: true };
    container._khnTween = gsap.from(items, vars);
  };

  /* Sortie rapide (≈ 60 % de l'entrée) avant remplacement d'une grille */
  KHN.motion.fadeOut = function (container, done) {
    if (container._khnTween) container._khnTween.kill();
    if (!KHN.motion.ok() || !container.children.length) { done(); return; }
    container._khnTween = gsap.to(container.children, {
      opacity: 0, y: -8, duration: 0.15, stagger: 0.015, ease: 'power1.in', onComplete: done
    });
  };

  /* ---------- Transition de page ---------- */
  var overlay = document.querySelector('.page-transition');
  var root = document.documentElement;

  function hideOverlay() {
    root.classList.remove('pt-enter');
    if (overlay) gsap.set(overlay, { yPercent: 100, visibility: 'hidden' });
  }

  if (overlay && root.classList.contains('pt-enter')) {
    if (KHN.motion.ok()) {
      gsap.set(overlay, { yPercent: 0, visibility: 'visible' });
      root.classList.remove('pt-enter');
      gsap.to(overlay, {
        yPercent: -100, duration: 0.45, ease: 'power2.out', delay: 0.05,
        onComplete: function () { gsap.set(overlay, { visibility: 'hidden' }); }
      });
    } else {
      hideOverlay();
    }
  }
  // Retour arrière (bfcache) : le voile ne doit jamais rester affiché
  window.addEventListener('pageshow', function (e) { if (e.persisted) hideOverlay(); });

  function isInternalPageLink(a, e) {
    if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return false;
    if (a.target && a.target !== '_self') return false;
    if (a.hasAttribute('download') || a.hasAttribute('data-no-transition')) return false;
    var href = a.getAttribute('href');
    if (!href || href.charAt(0) === '#' || /^(mailto|tel|javascript):/i.test(href)) return false;
    var url = new URL(a.href, window.location.href);
    if (url.origin !== window.location.origin) return false;
    if (url.pathname === window.location.pathname && url.search === window.location.search && url.hash) return false;
    return true;
  }

  document.addEventListener('click', function (e) {
    var a = e.target.closest('a[href]');
    if (!overlay || !isInternalPageLink(a, e) || !KHN.motion.ok()) return;
    e.preventDefault();
    var url = a.href;
    var gone = false;
    function go() { if (gone) return; gone = true; window.location.href = url; }
    try { window.sessionStorage.setItem('khn-pt', '1'); } catch (err) { /* ignore */ }
    gsap.killTweensOf(overlay);
    gsap.fromTo(overlay,
      { yPercent: 100, visibility: 'visible' },
      { yPercent: 0, duration: 0.25, ease: 'power2.in', onComplete: go });
    window.setTimeout(go, 400); // ne jamais bloquer la navigation
  });

  /* ---------- Animations au défilement ---------- */
  function initScroll() {
    var mm = gsap.matchMedia();
    mm.add('(prefers-reduced-motion: no-preference)', function () {
      // Scroll Reveal (Standard)
      gsap.utils.toArray('[data-reveal]').forEach(function (el) {
        var targets = el.hasAttribute('data-reveal-self') ? [el] : gsap.utils.toArray(el.children).slice(0, 8);
        gsap.from(targets, {
          opacity: 0, y: 24, duration: 0.5, stagger: 0.08, ease: 'power2.out',
          clearProps: 'opacity,transform',
          scrollTrigger: { trigger: el, start: 'top 85%', once: true }
        });
      });

      // Stagger List sur les grilles produits marquées
      gsap.utils.toArray('[data-stagger]').forEach(function (grid) {
        KHN.motion.staggerIn(grid, grid.children, { scroll: true });
      });

      // Parallax Scroll (Standard) : calques décoratifs seulement
      gsap.utils.toArray('[data-parallax-group]').forEach(function (group) {
        gsap.utils.toArray(group.querySelectorAll('[data-parallax]')).forEach(function (layer, i) {
          var depth = parseFloat(layer.dataset.parallax) || i + 1;
          gsap.to(layer, {
            yPercent: depth * -8,
            ease: 'none',
            scrollTrigger: { trigger: group, start: 'top top', end: 'bottom top', scrub: 0.5 }
          });
        });
      });

      // Barre de progression des étapes (« comment ça marche »)
      gsap.utils.toArray('[data-step-line]').forEach(function (line) {
        gsap.fromTo(line, { scaleX: 0 }, {
          scaleX: 1, ease: 'none', transformOrigin: 'left center',
          scrollTrigger: { trigger: line.parentElement, start: 'top 80%', end: 'bottom 60%', scrub: 0.5 }
        });
      });
    });
  }

  /* ---------- Intro du hero (1 à 2 éléments clés par vue) ---------- */
  function heroIntro() {
    var hero = document.querySelector('[data-hero]');
    if (!hero || !KHN.motion.ok()) return;
    var tl = gsap.timeline({ defaults: { ease: 'power3.out' } });
    tl.from(hero.querySelectorAll('[data-hero-line]'), { y: 28, opacity: 0, duration: 0.6, stagger: 0.08, clearProps: 'opacity,transform' })
      .from(hero.querySelector('.hero-bottle'), { y: 60, rotation: -8, scale: 0.9, opacity: 0, duration: 0.8, ease: 'back.out(1.4)', clearProps: 'opacity,transform' }, 0.1)
      .from(hero.querySelectorAll('.deco'), { scale: 0, opacity: 0, duration: 0.5, stagger: 0.07, ease: 'back.out(2)', clearProps: 'opacity' }, 0.35); // transform conservé : partagé avec la parallaxe
  }

  // Les scripts de page (exécutés après celui-ci) génèrent les grilles :
  // on attend DOMContentLoaded, qui survient après tous les scripts « defer ».
  var started = false;
  function init() {
    if (started) return;
    started = true;
    initScroll();
    heroIntro();
  }
  if (document.readyState === 'complete') init();
  else {
    document.addEventListener('DOMContentLoaded', init);
    window.addEventListener('load', init);
  }
})();
