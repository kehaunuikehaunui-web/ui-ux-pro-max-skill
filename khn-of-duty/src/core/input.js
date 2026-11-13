// Entrées : clavier (positions physiques → ZQSD et WASD), souris (verrouillage du pointeur
// facultatif) et commandes tactiles (joystick, zone de visée, boutons).
export class Input {
  constructor(root, canvas, hooks) {
    this.root = root; this.canvas = canvas; this.hooks = hooks;
    this.keys = new Set(); this.active = false; this.locked = false; this.touchMode = false;
    this.lx = 0; this.ly = 0; this.mFire = false; this.mAds = false; this.edge = {};
    this.t = { mx: 0, my: 0, fire: false, ads: false, jump: false };
    const on = (el, ev, fn, o) => el.addEventListener(ev, fn, o);
    on(window, 'keydown', (e) => this.key(e, true)); on(window, 'keyup', (e) => this.key(e, false));
    on(window, 'blur', () => { this.keys.clear(); this.mFire = this.mAds = false; });
    on(document, 'pointerlockchange', () => {
      const was = this.locked; this.locked = document.pointerLockElement === canvas;
      if (was && !this.locked && this.active) hooks.onPause();
    });
    on(document, 'pointerlockerror', () => { this.locked = false; });
    // Pointeur verrouillé : deltas natifs. Sinon (verrouillage refusé ou indisponible) : écart entre positions successives.
    let px = null, py = 0;
    on(root, 'mousemove', (e) => {
      if (!this.active || this.touchMode) { px = null; return; }
      if (this.locked) { this.lx += e.movementX || 0; this.ly += e.movementY || 0; px = null; return; }
      if (px !== null) { this.lx += e.clientX - px; this.ly += e.clientY - py; }
      px = e.clientX; py = e.clientY;
    });
    on(root, 'mouseleave', () => { px = null; });
    on(canvas, 'mousedown', (e) => {
      if (!this.active || this.touchMode) return;
      if (!this.locked) this.lock();
      if (e.button === 0) { this.mFire = true; this.edge.fire = true; } else if (e.button === 2) this.mAds = true;
    });
    on(window, 'mouseup', (e) => { if (e.button === 0) this.mFire = false; else if (e.button === 2) this.mAds = false; });
    on(root, 'contextmenu', (e) => e.preventDefault());
    on(root, 'wheel', (e) => { if (this.active) { this.edge.swap = true; e.preventDefault(); } }, { passive: false });
    on(window, 'pointerdown', (e) => { if (e.pointerType === 'touch' && !this.touchMode) { this.touchMode = true; hooks.onTouchMode(); } }, true);
  }

  key(e, down) {
    if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT')) return;
    if (!this.active) { if (down && e.code === 'Escape') this.hooks.onBack(); return; }
    if (['Tab', 'Space', 'ArrowUp', 'ArrowDown'].includes(e.code)) e.preventDefault();
    if (down) {
      if (e.repeat) return;
      this.keys.add(e.code);
      if (e.code === 'Space') this.edge.jump = true; // appui bref mémorisé jusqu'à l'image suivante
      if (e.code === 'KeyR') this.edge.reload = true;
      else if (e.code === 'KeyG' || e.code === 'KeyF') this.edge.equip = true;
      else if (e.code === 'Digit1' || e.code === 'Digit2' || e.code === 'KeyX') this.edge.swap = e.code === 'KeyX' ? true : e.code;
      else if (e.code === 'Escape' || e.code === 'KeyP') this.hooks.onPause();
    } else this.keys.delete(e.code);
  }

  lock() { try { const p = this.canvas.requestPointerLock && this.canvas.requestPointerLock(); if (p && p.catch) p.catch(() => {}); } catch { /* facultatif */ } }
  unlock() { try { if (document.pointerLockElement) document.exitPointerLock(); } catch { /* ignoré */ } }

  setActive(v) {
    this.active = v; this.keys.clear(); this.mFire = this.mAds = false; this.lx = this.ly = 0; this.edge = {};
    this.t.mx = this.t.my = 0; this.t.fire = false; this.t.jump = false;
    if (v && !this.touchMode) this.lock(); else if (!v) this.unlock();
  }

  /** État des commandes pour l'image courante (les deltas et fronts sont consommés). */
  frame() {
    const k = this.keys, has = (c) => k.has(c);
    let f = (has('KeyW') || has('ArrowUp') ? 1 : 0) - (has('KeyS') || has('ArrowDown') ? 1 : 0);
    let s = (has('KeyD') || has('ArrowRight') ? 1 : 0) - (has('KeyA') || has('ArrowLeft') ? 1 : 0);
    let sprint = has('ShiftLeft') || has('ShiftRight');
    if (this.touchMode) { f = -this.t.my; s = this.t.mx; sprint = Math.hypot(f, s) > 0.92 && f > 0.6; }
    const out = { f, s, sprint, lx: this.lx, ly: this.ly, touch: this.touchMode, fire: this.mFire || this.t.fire, ads: this.mAds || this.t.ads, jump: has('Space') || this.t.jump, score: has('Tab'), ...this.edge };
    this.lx = this.ly = 0; this.edge = {}; this.t.jump = false;
    return out;
  }

  /** Branche les commandes tactiles sur la couche DOM fournie. */
  bindTouch(layer) {
    const T = this.t, q = (s) => layer.querySelector(s);
    const zone = q('.t-move'), base = q('.t-stick'), knob = q('.t-knob'), look = q('.t-look');
    let mid = -1, ox = 0, oy = 0;
    const R = 52;
    zone.addEventListener('pointerdown', (e) => {
      if (mid !== -1) return; mid = e.pointerId; zone.setPointerCapture(mid);
      const r = layer.getBoundingClientRect(); ox = e.clientX; oy = e.clientY;
      base.style.left = (ox - r.left) + 'px'; base.style.top = (oy - r.top) + 'px'; base.classList.add('on'); knob.style.transform = 'translate(-50%,-50%)';
    });
    zone.addEventListener('pointermove', (e) => {
      if (e.pointerId !== mid) return;
      let dx = e.clientX - ox, dy = e.clientY - oy; const d = Math.hypot(dx, dy);
      if (d > R) { dx *= R / d; dy *= R / d; }
      T.mx = dx / R; T.my = dy / R; knob.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;
    });
    const endMove = (e) => { if (e.pointerId !== mid) return; mid = -1; T.mx = T.my = 0; base.classList.remove('on'); };
    zone.addEventListener('pointerup', endMove); zone.addEventListener('pointercancel', endMove);

    // visée : la zone de droite et le bouton de tir pilotent tous deux la caméra
    const drag = (el, onDown, onUp) => {
      const last = new Map();
      el.addEventListener('pointerdown', (e) => { el.setPointerCapture(e.pointerId); last.set(e.pointerId, [e.clientX, e.clientY]); if (onDown) onDown(); e.preventDefault(); });
      el.addEventListener('pointermove', (e) => { const p = last.get(e.pointerId); if (!p) return; this.lx += (e.clientX - p[0]) * 2.4; this.ly += (e.clientY - p[1]) * 2.4; p[0] = e.clientX; p[1] = e.clientY; });
      const up = (e) => { if (!last.delete(e.pointerId)) return; if (onUp && last.size === 0) onUp(); };
      el.addEventListener('pointerup', up); el.addEventListener('pointercancel', up);
    };
    drag(look);
    drag(q('.t-fire'), () => { T.fire = true; }, () => { T.fire = false; });
    const tap = (sel, fn) => q(sel).addEventListener('pointerdown', (e) => { e.preventDefault(); e.stopPropagation(); fn(e.currentTarget); });
    tap('.t-ads', (b) => { T.ads = !T.ads; b.classList.toggle('on', T.ads); });
    tap('.t-jump', () => { T.jump = true; });
    tap('.t-reload', () => { this.edge.reload = true; });
    tap('.t-swap', () => { this.edge.swap = true; });
    tap('.t-equip', () => { this.edge.equip = true; });
    tap('.t-pause', () => this.hooks.onPause());
    tap('.t-score', () => { this.edge.scoreToggle = true; });
    this.resetTouch = () => { T.ads = false; q('.t-ads').classList.remove('on'); T.fire = false; };
  }
}
