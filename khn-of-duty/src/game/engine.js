// Moteur : rendu Three.js, caméra subjective, effets, liaison entrées ↔ simulation ↔ HUD.
import * as THREE from 'three';
import { buildWorld } from './world.js';
import { Match, angDiff } from './match.js';
import { CharModel, buildViewModel, buildWeapon, disposeModel } from './characters.js';
import { EYE } from './physics.js';
import { WEAPONS, OPERATORS, CAMOS } from '../core/config.js';

const ALLY = { uniform: 0x3d4d50, accent: 0x3fd6c0 }, FOE = { uniform: 0x6a5238, accent: 0xff5a47 };
const NP = 320; // particules
const lerp = (a, b, k) => a + (b - a) * k;

export class Game {
  constructor(root, canvas, profile, sfx, input, hud, hooks) {
    Object.assign(this, { root, profile, sfx, input, hud, hooks });
    const coarse = matchMedia('(pointer: coarse)').matches;
    const r = this.renderer = new THREE.WebGLRenderer({ canvas, antialias: !coarse, powerPreference: 'high-performance' });
    r.autoClear = false; r.shadowMap.type = THREE.PCFSoftShadowMap;
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(75, 1, 0.08, 950); this.camera.rotation.order = 'YXZ';

    // arme en vue subjective : scène dédiée, rendue par-dessus (ne traverse jamais les murs)
    this.vmScene = new THREE.Scene(); this.vmCam = new THREE.PerspectiveCamera(52, 1, 0.01, 10);
    const vl = new THREE.DirectionalLight(0xffffff, 2.2); vl.position.set(0.4, 1, 0.7);
    this.vmScene.add(new THREE.HemisphereLight(0xffffff, 0x4a5458, 2.4), vl);
    this.vmRoot = new THREE.Group(); this.vmScene.add(this.vmRoot);
    this.flash = new THREE.Mesh(new THREE.PlaneGeometry(0.16, 0.16), new THREE.MeshBasicMaterial({ color: 0xffd27a, transparent: true, opacity: 0.9, blending: THREE.AdditiveBlending, depthWrite: false, depthTest: false }));
    this.flash.visible = false; this.vmRoot.add(this.flash);

    // vitrine (arsenal, opérateur)
    this.showScene = new THREE.Scene(); this.showCam = new THREE.PerspectiveCamera(32, 1, 0.1, 50); this.showCam.position.z = 4;
    const sl = new THREE.DirectionalLight(0xfff0d8, 4.5); sl.position.set(1, 1.4, 2);
    const rim = new THREE.DirectionalLight(0x3fd6c0, 1.6); rim.position.set(-2, 0.5, -1.5);
    this.showScene.add(new THREE.HemisphereLight(0xeef4f6, 0x4a5458, 3.2), sl, rim);
    this.showPivot = new THREE.Group(); this.showScene.add(this.showPivot); this.showKind = null;

    // effets : traçantes, particules, éclairs
    this.fx = new THREE.Group(); this.scene.add(this.fx);
    const tg = new THREE.BoxGeometry(0.025, 0.025, 1).translate(0, 0, 0.5);
    this.tracers = Array.from({ length: 40 }, () => { const m = new THREE.Mesh(tg, new THREE.MeshBasicMaterial({ color: 0xffe0a0, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false })); m.visible = false; m.userData.t = 0; this.fx.add(m); return m; });
    this.ti = 0;
    const pg = new THREE.BufferGeometry(); this.pPos = new Float32Array(NP * 3).fill(-999); this.pCol = new Float32Array(NP * 3);
    pg.setAttribute('position', new THREE.BufferAttribute(this.pPos, 3)); pg.setAttribute('color', new THREE.BufferAttribute(this.pCol, 3));
    this.pVel = new Float32Array(NP * 3); this.pLife = new Float32Array(NP); this.pi = 0;
    this.points = new THREE.Points(pg, new THREE.PointsMaterial({ size: 0.11, vertexColors: true, transparent: true, opacity: 0.9, depthWrite: false })); this.points.frustumCulled = false; this.fx.add(this.points);
    this.muzzle = new THREE.PointLight(0xffc878, 0, 14, 2); this.blast = new THREE.PointLight(0xff9a50, 0, 30, 2); this.fx.add(this.muzzle, this.blast);
    this.nadeGeo = new THREE.SphereGeometry(0.07, 8, 6); this.nadeMat = new THREE.MeshLambertMaterial({ color: 0x2c3a2a });
    this.ringGeo = new THREE.RingGeometry(3.82, 4, 48).rotateX(-Math.PI / 2);

    this.state = 'menu'; this.match = null; this.world = null; this.models = new Map(); this.nadeMeshes = new Map(); this.zoneVis = []; this.vms = [];
    this.orbit = 0; this.kick = 0; this.vmKick = 0; this.flashT = 0; this.shake = 0; this.bob = 0; this.swayX = 0; this.swayY = 0; this.sprintK = 0; this.endT = 0; this.fpsT = 0; this.fpsN = 0; this.fps = 60; this.boardOn = false;
    this._c = new THREE.Color(); this._v = new THREE.Vector3();

    new ResizeObserver(() => this.resize()).observe(root); this.resize();
    document.addEventListener('visibilitychange', () => { if (document.hidden && this.state === 'playing') hooks.onPause(); });
    this.last = performance.now();
    const loop = (t) => { requestAnimationFrame(loop); this.frame(t); };
    requestAnimationFrame(loop);
  }

  resize() {
    const w = Math.max(1, this.root.clientWidth), h = Math.max(1, this.root.clientHeight), q = this.profile.settings.quality;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, q === 'low' ? 1 : q === 'med' ? 1.5 : 2));
    this.renderer.setSize(w, h, false);
    this.aspect = w / h;
    for (const c of [this.camera, this.vmCam, this.showCam]) { c.aspect = this.aspect; c.updateProjectionMatrix(); }
    this.vmCam.fov = THREE.MathUtils.radToDeg(2 * Math.atan(0.488 * Math.max(1, 1.25 / this.aspect))); this.vmCam.updateProjectionMatrix();
    this.layoutShowcase();
  }

  loadWorld(mapId) {
    const q = this.profile.settings.quality;
    if (this.world && this.world.def.id === mapId && this.worldQ === q) return;
    if (this.world) { this.scene.remove(this.world.group); this.world.dispose(); }
    this.renderer.shadowMap.enabled = q !== 'low';
    this.world = buildWorld(mapId, q); this.worldQ = q;
    this.scene.add(this.world.group); this.scene.fog = this.world.fog; this.scene.background = this.world.background;
    this.renderer.shadowMap.autoUpdate = false; this.renderer.shadowMap.needsUpdate = true; // décor statique : ombres calculées une seule fois
    this.resize();
  }

  // ───────────────────────── vitrine ─────────────────────────
  setShowcase(kind, id) {
    for (const o of [...this.showPivot.children]) { this.showPivot.remove(o); disposeModel(o); }
    if (this.showModel && this.showModel.dispose) this.showModel.dispose();
    this.showModel = null; this.showKind = kind;
    if (kind === 'weapon') {
      const def = WEAPONS[id], camo = CAMOS.find((c) => c.id === this.profile.camo) || CAMOS[0];
      const g = buildWeapon(def, camo.color), s = 1.55 / Math.max(0.5, def.len + 0.3);
      g.scale.setScalar(s); g.position.set(0, -0.04 * s, (def.len - 0.3) / 2 * s); // centré sur sa longueur
      this.showPivot.add(g); this.showPivot.rotation.set(0.12, Math.PI / 2, 0);
    } else if (kind === 'operator') {
      const op = OPERATORS.find((o) => o.id === id) || OPERATORS[0];
      const m = this.showModel = new CharModel({ uniform: op.color, accent: ALLY.accent, weapon: WEAPONS[this.profile.loadout.primary] });
      m.update({ alive: true, x: 0, y: 0, z: 0, yaw: Math.PI, pitch: 0, speed: 0 }, 0); m.blob.visible = false;
      m.group.position.y = -1.05; m.group.scale.setScalar(1.15); this.showPivot.add(m.group); this.showPivot.rotation.set(0, 0.5, 0);
    }
    this.layoutShowcase();
  }

  layoutShowcase() {
    // Écran étroit : le modèle se cale dans l'emplacement réservé par le menu (.show-space). Sinon : moitié droite.
    const slot = this.root.querySelector('.show-space'), r = slot && slot.getBoundingClientRect(), R = this.root.getBoundingClientRect();
    const hh = Math.tan(THREE.MathUtils.degToRad(16)) * 4, op = this.showKind === 'operator';
    if (r && r.height > 0) {
      const w = r.width / R.width * 2 * hh * this.aspect, h = r.height / R.height * 2 * hh;
      this.showPivot.position.set(((r.left + r.width / 2 - R.left) / R.width * 2 - 1) * hh * this.aspect, (1 - (r.top + r.height / 2 - R.top) / R.height * 2) * hh, 0);
      this.showPivot.scale.setScalar(op ? h / 2.25 : Math.min(w / 1.75, h / 0.62));
    } else {
      this.showPivot.position.set(0.95 * Math.min(1.5, this.aspect / 1.6), op ? -0.05 : 0.1, 0);
      this.showPivot.scale.setScalar(1);
    }
  }

  // ───────────────────────── partie ─────────────────────────
  start(o) {
    this.cleanupMatch();
    this.loadWorld(o.map); this.setShowcase(null);
    const P = this.profile, op = OPERATORS.find((x) => x.id === P.operator) || OPERATORS[0], camo = CAMOS.find((c) => c.id === P.camo) || CAMOS[0];
    const m = this.match = new Match(this.world, { ...o, loadout: P.loadout, name: P.name }, (t, d) => this.onEvent(t, d));
    for (const c of m.chars) this.addModel(c);
    this.vms = m.player.weapons.map((w) => { const v = buildViewModel(w.def, camo.color, op.color); v.visible = false; this.vmRoot.add(v); return v; });
    for (const z of m.zones) {
      const ring = new THREE.Mesh(this.ringGeo, new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.75, depthWrite: false })); ring.position.set(z.x, 0.06, z.z);
      const c = document.createElement('canvas'); c.width = c.height = 64; const g = c.getContext('2d');
      g.fillStyle = 'rgba(8,14,16,.7)'; g.beginPath(); g.moveTo(32, 2); g.lineTo(62, 32); g.lineTo(32, 62); g.lineTo(2, 32); g.fill();
      g.fillStyle = '#fff'; g.font = '700 30px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(z.id, 32, 34);
      const tex = new THREE.CanvasTexture(c); tex.colorSpace = THREE.SRGBColorSpace;
      const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false, transparent: true, sizeAttenuation: false })); sp.scale.set(0.05, 0.05, 1); sp.position.set(z.x, 3.4, z.z); sp.renderOrder = 9;
      this.fx.add(ring, sp); this.zoneVis.push({ z, ring, sp });
    }
    this.kick = this.vmKick = this.shake = 0; this.boardOn = false;
    if (o.net) o.net.bind(m);
    this.hud.begin(m, this.world); m.begin();
    this.state = 'playing'; this.input.setActive(true);
    return m;
  }

  addModel(c) {
    if (c === this.match.player || this.models.has(c)) return;
    const ally = !this.match.isEnemy(this.match.player, c);
    const mod = new CharModel({ ...(ally ? ALLY : FOE), weapon: c.weapons[0].def, tag: ally ? c.name : null });
    mod.group.visible = false; this.fx.add(mod.group); this.models.set(c, mod);
  }

  removeModel(c) { const m = this.models.get(c); if (m) { this.fx.remove(m.group); m.dispose(); this.models.delete(c); } }

  cleanupMatch() {
    for (const m of this.models.values()) { this.fx.remove(m.group); m.dispose(); } this.models.clear();
    for (const v of this.vms) { this.vmRoot.remove(v); disposeModel(v); } this.vms = [];
    for (const n of this.nadeMeshes.values()) this.fx.remove(n); this.nadeMeshes.clear();
    for (const z of this.zoneVis) { this.fx.remove(z.ring, z.sp); z.ring.material.dispose(); z.sp.material.map.dispose(); z.sp.material.dispose(); } this.zoneVis = [];
    for (const t of this.tracers) t.visible = false;
    this.pPos.fill(-999); this.pLife.fill(0); this.points.geometry.attributes.position.needsUpdate = true;
    this.muzzle.intensity = this.blast.intensity = 0;
    if (this.match && this.match.net && this.match.net.match === this.match) this.match.net.match = null;
    this.match = null;
  }

  pause() { if (this.state !== 'playing') return false; this.state = 'paused'; this.input.setActive(false); return true; }
  resume() { if (this.state !== 'paused') return; this.state = 'playing'; this.input.setActive(true); this.last = performance.now(); }
  quit() { this.input.setActive(false); this.hud.show(false); this.cleanupMatch(); this.state = 'menu'; }

  summary() {
    const m = this.match, p = m.player, board = m.board();
    return {
      mode: m.mode.id, map: this.world.def.id, difficulty: m.o.difficulty, outcome: m.outcome(), kills: p.kills, deaths: p.deaths, heads: p.heads, shots: p.shots, hits: p.hits,
      best: p.best, captures: p.captures, score: p.score, duration: m.t, teams: m.mode.teams, scores: [...m.scores], limit: m.limit, online: !!m.net,
      team: p.team, line: m.mode.teams ? `${m.scores[p.team]} – ${m.scores[1 - p.team]}` : `${board.indexOf(p) + 1}ᵉ sur ${board.length}`,
      board: board.map((c) => ({ name: c.name, team: c.team, kills: c.kills, deaths: c.deaths, score: c.score, me: c === p, ally: c !== p && !m.isEnemy(p, c) })),
    };
  }

  // ───────────────────────── événements de simulation ─────────────────────────
  onEvent(type, d) {
    const m = this.match, p = m.player, hud = this.hud, sfx = this.sfx;
    switch (type) {
      case 'spawn': { const mod = this.models.get(d.c); if (mod) { mod.reset(); mod.group.visible = true; } if (d.c === p) { hud.respawn(false); this.kick = 0; if (this.input.resetTouch) this.input.resetTouch(); } break; }
      case 'fire': {
        if (d.c === p) {
          sfx.shot(d.def.snd, 0); this.vmKick = 1; this.flashT = 0.045;
          const r = d.def.recoil * (1 - 0.45 * p.ads); p.pitch = Math.min(1.5, p.pitch + r); p.yaw += (Math.random() - 0.5) * r * 0.5; this.kick += r;
        } else sfx.shot(d.def.snd, Math.max(1, Math.hypot(d.c.x - p.x, d.c.z - p.z)));
        break;
      }
      case 'shot': {
        if (d.c !== p && d.p > 0) break;
        const t = this.tracers[this.ti = (this.ti + 1) % this.tracers.length];
        if (d.c === p) { const cy = Math.cos(p.yaw), sy = Math.sin(p.yaw); t.position.set(d.ox + cy * 0.16 - sy * 0.5, d.oy - 0.13, d.oz - sy * 0.16 - cy * 0.5); } else t.position.set(d.ox - Math.sin(d.c.yaw) * 0.7, d.oy - 0.12, d.oz - Math.cos(d.c.yaw) * 0.7);
        this._v.set(d.x, d.y, d.z); t.lookAt(this._v); t.scale.z = Math.max(0.5, t.position.distanceTo(this._v)); t.visible = true; t.userData.t = 0.06;
        if (d.hit) this.puff(d.x, d.y, d.z, 5, 0xc23a2a, 2.2, 0.35); else this.puff(d.x, d.y, d.z, 3, 0xb9b3a6, 1.6, 0.4);
        break;
      }
      case 'damage': {
        if (d.v === p) {
          sfx.hurt(); this.shake = Math.min(1, this.shake + d.amt / 70);
          if (d.a && d.a !== p) hud.damageFrom(angDiff(Math.atan2(-(d.a.x - p.x), -(d.a.z - p.z)), p.yaw));
        } else if (d.a === p) { hud.hitmark(false, d.head); if (d.head) sfx.headshot(); else sfx.hit(); }
        break;
      }
      case 'kill': {
        const kind = (c) => (c === p ? 'me' : m.isEnemy(p, c) ? 'foe' : 'ally');
        hud.feed(d.a ? d.a.name : '—', d.a ? kind(d.a) : 'foe', d.v.name, kind(d.v), d.weapon, d.head);
        if (d.a === p && d.v !== p) {
          hud.hitmark(true, d.head); sfx.kill();
          hud.notice(d.head ? 'TIR À LA TÊTE' : 'ÉLIMINATION', `+${d.head ? 125 : 100} · ${d.v.name}`);
          if (p.streak === 3 || p.streak === 5 || p.streak === 8 || p.streak === 12) hud.notice(`SÉRIE ×${p.streak}`, 'Continuez', 'gold');
        }
        if (d.v === p) { hud.respawn(true, d.a && d.a !== p ? d.a.name : 'vous-même', d.v.respawnT); this.input.mFire = false; }
        break;
      }
      case 'reload': if (d.c === p) sfx.reload(); break;
      case 'dry': if (d.c === p) sfx.dry(); break;
      case 'stim': if (d.c === p) { sfx.stim(); hud.notice('STIM', 'Santé restaurée', 'ok'); } break;
      case 'nade': { const me = new THREE.Mesh(this.nadeGeo, this.nadeMat); this.fx.add(me); this.nadeMeshes.set(d.n, me); break; }
      case 'explode': {
        const me = this.nadeMeshes.get(d.n); if (me) { this.fx.remove(me); this.nadeMeshes.delete(d.n); }
        const dist = Math.hypot(d.x - p.x, d.z - p.z);
        sfx.explode(dist); this.shake = Math.min(1.4, this.shake + 10 / (dist + 6));
        this.puff(d.x, d.y + 0.3, d.z, 46, 0xffb060, 9, 0.6); this.puff(d.x, d.y + 0.3, d.z, 30, 0x4a4640, 5, 1.1);
        this.blast.position.set(d.x, d.y + 1, d.z); this.blast.intensity = 500;
        break;
      }
      case 'zone': { const mine = d.team === p.team; sfx.capture(mine); hud.notice(`ZONE ${d.z.id} ${mine ? 'CAPTURÉE' : 'PERDUE'}`, mine ? 'Tenez la position' : 'Reprenez-la', mine ? 'ok' : 'bad'); break; }
      case 'end': { this.state = 'ending'; this.endT = 2.4; const o = m.outcome(); sfx.end(o === 1); hud.banner(o === 1 ? 'VICTOIRE' : o === 0 ? 'ÉGALITÉ' : 'DÉFAITE', o === 1 ? 'ok' : o === 0 ? '' : 'bad'); this.input.setActive(false); break; }
      default: break;
    }
    if (this.hooks.onEvent) this.hooks.onEvent(type, d);
  }

  puff(x, y, z, n, color, speed, life) {
    this._c.setHex(color);
    for (let i = 0; i < n; i++) {
      const k = this.pi = (this.pi + 1) % NP, j = k * 3;
      this.pPos[j] = x; this.pPos[j + 1] = y; this.pPos[j + 2] = z;
      this.pVel[j] = (Math.random() - 0.5) * speed; this.pVel[j + 1] = Math.random() * speed * 0.8; this.pVel[j + 2] = (Math.random() - 0.5) * speed;
      this.pCol[j] = this._c.r; this.pCol[j + 1] = this._c.g; this.pCol[j + 2] = this._c.b; this.pLife[k] = life * (0.6 + Math.random() * 0.6);
    }
    this.points.geometry.attributes.color.needsUpdate = true;
  }

  // ───────────────────────── boucle ─────────────────────────
  frame(now) {
    const dt = Math.min(0.05, (now - this.last) / 1000); this.last = now;
    this.fpsN++; this.fpsT += dt; if (this.fpsT >= 0.5) { this.fps = Math.round(this.fpsN / this.fpsT); this.fpsN = 0; this.fpsT = 0; }
    if (!this.world) return;
    const m = this.match;
    if (m && (this.state === 'playing' || (this.state === 'paused' && m.net))) this.play(dt, this.state === 'playing');
    else if (this.state === 'ending') { this.endT -= dt; this.updateFx(dt * 0.25); if (this.endT <= 0) { this.state = 'menu'; const s = this.summary(); this.hud.show(false); this.hooks.onMatchEnd(s); this.cleanupMatch(); } }
    if (this.state === 'menu') {
      this.orbit += dt * 0.045;
      const d = this.world.def, R = Math.max(d.hx, d.hz) * 0.82;
      this.camera.position.set(Math.cos(this.orbit) * R, 13, Math.sin(this.orbit) * R); this.camera.lookAt(0, 1.5, 0);
      this.camera.fov = 58; this.camera.updateProjectionMatrix();
    }
    const r = this.renderer;
    r.clear(); r.render(this.scene, this.camera);
    if (m && this.state !== 'menu' && m.player.alive && !this.scoped) { r.clearDepth(); r.render(this.vmScene, this.vmCam); }
    if (this.showKind) { this.showPivot.rotation.y += dt * 0.5; r.clearDepth(); r.render(this.showScene, this.showCam); }
  }

  play(dt, control) {
    const m = this.match, p = m.player, S = this.profile.settings, cam = this.camera;
    const inp = control ? this.input.frame() : { f: 0, s: 0, lx: 0, ly: 0 };
    const def = p.weapons[p.wi].def;
    if (p.alive && !m.over) {
      const sens = (inp.touch ? 0.0021 : 0.0022) * S.sens / (1 + (def.zoom - 1) * p.ads);
      p.yaw -= inp.lx * sens; p.pitch = Math.max(-1.5, Math.min(1.5, p.pitch - inp.ly * sens * (S.invertY ? -1 : 1)));
      const rec = this.kick * Math.min(1, dt * 7); p.pitch -= rec * 0.55; this.kick -= rec;
      const sy = Math.sin(p.yaw), cy = Math.cos(p.yaw);
      p.wx = -sy * inp.f + cy * inp.s; p.wz = -cy * inp.f - sy * inp.s;
      p.wantSprint = !!inp.sprint; p.wantJump = !!inp.jump; p.wantAds = !!inp.ads;
      let fire = !!inp.fire;
      if (inp.touch && S.autoFire && !fire) fire = this.onTarget(p, def);
      p.wantFire = fire;
      if (inp.reload) m.reload(p);
      if (inp.swap && !((inp.swap === 'Digit1' && p.wi === 0) || (inp.swap === 'Digit2' && p.wi === 1))) m.swap(p);
      if (inp.equip) m.useEquip(p);
    } else { p.wx = p.wz = 0; p.wantFire = p.wantJump = p.wantAds = false; }
    if (inp.scoreToggle) this.boardOn = !this.boardOn;
    if (m.net) m.net.tick(dt, m);
    m.update(dt);
    if (!this.match) return; // la partie a pu être quittée pendant la mise à jour

    // caméra
    this.shake = Math.max(0, this.shake - dt * 2.6);
    const sh = this.shake * 0.035;
    if (p.alive) {
      cam.position.set(p.x, p.y + EYE, p.z); cam.rotation.set(p.pitch + (Math.random() - 0.5) * sh, p.yaw + (Math.random() - 0.5) * sh, 0);
    } else {
      cam.position.y = lerp(cam.position.y, p.y + 2.3, Math.min(1, dt * 2));
      if (p.killer && p.killer !== p) { const ty = Math.atan2(-(p.killer.x - cam.position.x), -(p.killer.z - cam.position.z)); cam.rotation.y += angDiff(ty, cam.rotation.y) * Math.min(1, dt * 4); cam.rotation.x = lerp(cam.rotation.x, -0.25, Math.min(1, dt * 3)); }
    }
    const base = Math.min(100, S.fov * (this.aspect < 1 ? 1.22 : 1)), fov = base / (1 + (def.zoom - 1) * p.ads);
    if (Math.abs(cam.fov - fov) > 0.05) { cam.fov = fov; cam.updateProjectionMatrix(); }
    this.scoped = !!def.scope && p.ads > 0.82 && p.alive;

    // arme en main
    const sightY = this.vms[p.wi] ? this.vms[p.wi].userData.sightY : 0.1;
    this.vms.forEach((v, i) => { v.visible = i === p.wi; });
    this.bob += dt * p.speed * 1.9; this.vmKick = Math.max(0, this.vmKick - dt * 9);
    this.sprintK = lerp(this.sprintK, p.sprinting ? 1 : 0, Math.min(1, dt * 9));
    this.swayX = lerp(this.swayX, Math.max(-0.03, Math.min(0.03, -inp.lx * 0.0006)), Math.min(1, dt * 10)); this.swayY = lerp(this.swayY, Math.max(-0.03, Math.min(0.03, inp.ly * 0.0006)), Math.min(1, dt * 10));
    const a = p.ads, hipK = 1 - a, mv = Math.min(1, p.speed / 5) * hipK, vr = this.vmRoot;
    let vy = lerp(-0.2, -sightY, a) + Math.abs(Math.cos(this.bob)) * 0.008 * mv + this.swayY, vx = lerp(0.19, 0, a) + Math.sin(this.bob) * 0.007 * mv + this.swayX, vz = lerp(-0.42, -0.3, a) + this.vmKick * 0.045, rx = this.vmKick * 0.07 * (0.4 + 0.6 * hipK), ry = 0, rz = 0;
    if (p.reloadT > 0) { const k = Math.sin(Math.PI * (1 - p.reloadT / def.reload)); vy -= k * 0.17; rx -= k * 0.5; rz += k * 0.45; }
    if (p.swapT > 0) { const k = p.swapT / 0.38; vy -= k * 0.28; rx -= k * 0.6; }
    vy -= this.sprintK * 0.05; vx -= this.sprintK * 0.07; ry += this.sprintK * 0.55; rx -= this.sprintK * 0.12;
    vr.position.set(vx, vy, vz); vr.rotation.set(rx, ry, rz);
    this.flashT -= dt; this.flash.visible = this.flashT > 0;
    if (this.flash.visible && this.vms[p.wi]) { this.flash.position.copy(this.vms[p.wi].userData.muzzle); this.flash.rotation.z = Math.random() * 6.28; this.flash.scale.setScalar(0.7 + Math.random() * 0.7); }
    this.muzzle.intensity = this.flashT > 0 ? 26 : 0; if (this.flashT > 0) this.muzzle.position.set(p.x - Math.sin(p.yaw) * 0.8, p.y + EYE, p.z - Math.cos(p.yaw) * 0.8);

    for (const [c, mod] of this.models) mod.update(c, dt);
    for (const [n, me] of this.nadeMeshes) me.position.set(n.x, n.y + 0.07, n.z);
    for (const { z, ring, sp } of this.zoneVis) {
      const col = z.owner < 0 ? 0xdfe6e2 : z.owner === p.team ? 0x3fd6c0 : 0xff5a47;
      ring.material.color.setHex(col); sp.material.color.setHex(col); ring.material.opacity = z.cap >= 0 ? 0.5 + 0.4 * Math.sin(m.t * 9) : 0.75;
    }
    this.updateFx(dt);
    const w = p.weapons[p.wi];
    const spread = (def.hip + (def.ads - def.hip) * p.ads) * (p.speed > 1.5 ? 1.35 : 1) * (p.onGround ? 1 : 1.8) + p.bloom;
    this.hud.update(m, p, dt, { spread, scoped: this.scoped, fps: S.showFps ? this.fps : 0, board: this.boardOn || !!inp.score, hint: !p.alive ? '' : (w.mag === 0 && w.reserve === 0) ? 'Munitions épuisées — changez d\'arme' : (w.mag <= Math.max(1, def.mag * 0.25) && p.reloadT <= 0 && w.reserve > 0) ? (inp.touch ? 'Rechargez' : 'R — Recharger') : p.reloadT > 0 ? 'Rechargement…' : '' });
  }

  /** Tir automatique tactile : vrai si le réticule est posé sur un ennemi à portée utile. */
  onTarget(p, def) {
    if (def.scope && p.ads < 0.8) return false;
    const m = this.match, cp = Math.cos(p.pitch), dx = -Math.sin(p.yaw) * cp, dy = Math.sin(p.pitch), dz = -Math.cos(p.yaw) * cp, oy = p.y + EYE;
    const t = m.col.ray(p.x, oy, p.z, dx, dy, dz, def.falloff[1] * 1.4);
    return !!m.rayChars(p, p.x, oy, p.z, dx, dy, dz, t);
  }

  updateFx(dt) {
    for (const t of this.tracers) if (t.visible) { t.userData.t -= dt; if (t.userData.t <= 0) t.visible = false; }
    const P = this.pPos, V = this.pVel, L = this.pLife; let any = false;
    for (let k = 0; k < NP; k++) {
      if (L[k] <= 0) continue; any = true;
      const j = k * 3; L[k] -= dt;
      if (L[k] <= 0) { P[j + 1] = -999; continue; }
      V[j + 1] -= 9 * dt; P[j] += V[j] * dt; P[j + 1] += V[j + 1] * dt; P[j + 2] += V[j + 2] * dt;
      if (P[j + 1] < 0.02) { P[j + 1] = 0.02; V[j] *= 0.5; V[j + 1] = 0; V[j + 2] *= 0.5; }
    }
    if (any) this.points.geometry.attributes.position.needsUpdate = true;
    if (this.blast.intensity > 0) this.blast.intensity = Math.max(0, this.blast.intensity - dt * 2600);
  }
}
