/* Portfolio : thème clair/sombre, menu mobile, apparition au défilement, section active. */
(() => {
  'use strict';

  const root = document.documentElement;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)');

  const readStoredTheme = () => {
    try {
      return localStorage.getItem('theme');
    } catch {
      return null;
    }
  };

  const storeTheme = (value) => {
    try {
      localStorage.setItem('theme', value);
    } catch {
      /* stockage indisponible (navigation privée) : le choix vaut pour la page en cours */
    }
  };

  /* ---------- Thème ---------- */
  const themeToggle = document.getElementById('theme-toggle');

  const syncThemeToggle = () => {
    if (themeToggle) {
      themeToggle.setAttribute('aria-pressed', String(root.classList.contains('dark')));
    }
  };

  if (themeToggle) {
    themeToggle.addEventListener('click', () => {
      const dark = root.classList.toggle('dark');
      storeTheme(dark ? 'dark' : 'light');
      syncThemeToggle();
    });
  }

  /* Suit le réglage du système tant que la personne n'a rien choisi elle-même. */
  systemDark.addEventListener('change', (event) => {
    if (readStoredTheme()) return;
    root.classList.toggle('dark', event.matches);
    syncThemeToggle();
  });

  syncThemeToggle();

  /* ---------- Menu mobile ---------- */
  const menuToggle = document.getElementById('menu-toggle');
  const menu = document.getElementById('menu-mobile');

  const setMenu = (open) => {
    if (!menu || !menuToggle) return;
    menu.dataset.open = String(open);
    menuToggle.setAttribute('aria-expanded', String(open));
  };

  if (menu && menuToggle) {
    menuToggle.addEventListener('click', () => {
      setMenu(menu.dataset.open !== 'true');
    });

    menu.addEventListener('click', (event) => {
      if (event.target.closest('a')) setMenu(false);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && menu.dataset.open === 'true') {
        setMenu(false);
        menuToggle.focus();
      }
    });

    window.matchMedia('(min-width: 64rem)').addEventListener('change', (event) => {
      if (event.matches) setMenu(false);
    });
  }

  /* ---------- Apparition au défilement ---------- */
  const revealItems = document.querySelectorAll('.reveal');
  const showAll = () => revealItems.forEach((item) => item.classList.add('is-visible'));

  if (reducedMotion.matches || !('IntersectionObserver' in window)) {
    showAll();
  } else {
    const revealObserver = new IntersectionObserver(
      (entries, observer) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.1 }
    );
    revealItems.forEach((item) => revealObserver.observe(item));
    reducedMotion.addEventListener('change', (event) => {
      if (event.matches) showAll();
    });
  }

  /* ---------- Section active dans la navigation ---------- */
  const navLinks = document.querySelectorAll('header nav a[href^="#"]');
  const sections = [...new Set([...navLinks].map((link) => link.getAttribute('href')))]
    .map((hash) => document.querySelector(hash))
    .filter(Boolean);

  if ('IntersectionObserver' in window && sections.length) {
    const navObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          navLinks.forEach((link) => {
            if (link.getAttribute('href') === `#${entry.target.id}`) {
              link.setAttribute('aria-current', 'location');
            } else {
              link.removeAttribute('aria-current');
            }
          });
        });
      },
      { rootMargin: '-40% 0px -55% 0px' }
    );
    sections.forEach((section) => navObserver.observe(section));
  }

  /* ---------- Année courante ---------- */
  const year = String(new Date().getFullYear());
  document.querySelectorAll('[data-year]').forEach((node) => {
    node.textContent = year;
  });
})();
