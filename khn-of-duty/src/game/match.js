// Simulation d'une partie : personnages, armes, IA des bots, modes de jeu, score et fin de partie.
// Indépendant du rendu : communique avec le moteur par événements (emit).
import { WEAPONS, MODES, DIFFICULTY, BOT_NAMES, EQUIPMENT } from '../core/config.js';
import { EYE, rayBox, raySphere } from './physics.js';

const G = 18, JUMP = 6.1, WALK = 5, SPRINT = 7.4, TAU = Math.PI * 2;
const R = Math.random;
const gauss = () => (R() + R() + R() - 1.5) * 1.15;
export const angDiff = (a, b) => { let d = (a - b) % TAU; if (d > Math.PI) d -= TAU; else if (d < -Math.PI) d += TAU; return d; };
const BOT_GUNS = ['mistral', 'mistral', 'fennec', 'fennec', 'kodiak', 'mistral', 'baliste'];
const PREF = { mistral: 20, fennec: 11, kodiak: 6, baliste: 38, argos: 12, vesper: 9 };
const _hb = { x0: 0, x1: 0, y0: 0, y1: 0, z0: 0, z1: 0 };

export class Match {
  /**
   * @param world  monde construit (colliders, nav, spawns, zones)
   * @param o      { mode, limit, size, difficulty, loadout, name, time?, net? }
   * @param emit   (type, data) => void
   */
  constructor(world, o, emit) {
    this.world = world; this.col = world.colliders; this.nav = world.nav; this.emit = emit; this.o = o;
    this.mode = MODES[o.mode]; this.limit = o.limit || this.mode.limit; this.timeLeft = o.time || this.mode.time;
    this.diff = DIFFICULTY[o.difficulty] || DIFFICULTY.regulier;
    this.net = o.net || null;
    this.chars = []; this.scores = [0, 0]; this.over = false; this.winner = -1; this.t = 0; this.nades = []; this.domT = 0;
    this.zones = this.mode.id === 'dom' ? world.zones.map((z, i) => ({ id: 'ABC'[i], x: z[0], z: z[1], owner: -1, prog: 0, cap: -1 })) : [];
    const lo = o.loadout;
    this.player = this.addChar(o.name || 'Opérateur', 0, false, lo.primary, lo.secondary, lo.equip);
    this.player.isPlayer = true;
    if (!this.net) {
      const names = [...BOT_NAMES].sort(() => R() - 0.5), n = o.size || 4;
      const total = n * 2 - 1;
      for (let i = 0; i < total; i++) {
        const team = this.mode.teams ? (i < n - 1 ? 0 : 1) : i + 1;
        this.addChar(names[i % names.length], team, true, BOT_GUNS[(R() * BOT_GUNS.length) | 0], 'argos', 'frag');
      }
    }
  }

  addChar(name, team, isBot, primary, secondary, equip) {
    const mk = (id) => { const def = WEAPONS[id] || WEAPONS.mistral; return { def, mag: def.mag, reserve: isBot ? 9999 : def.reserve }; };
    const c = {
      id: this.chars.length, name, team, isBot, isPlayer: false, remote: false,
      x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0, r: 0.35, h: 1.8, onGround: true, yaw: 0, pitch: 0, speed: 0,
      hp: 100, alive: false, respawnT: 0, protT: 0, sinceDmg: 99, spotT: 99,
      weapons: [mk(primary), mk(secondary)], wi: 0, fireCd: 0, reloadT: 0, swapT: 0, ads: 0, bloom: 0, aimErr: 0,
      equip: { id: equip, count: (EQUIPMENT[equip] || EQUIPMENT.frag).count },
      wx: 0, wz: 0, wantFire: false, trigHeld: false, wantAds: false, wantJump: false, wantSprint: false, sprinting: false,
      kills: 0, deaths: 0, score: 0, streak: 0, best: 0, heads: 0, shots: 0, hits: 0, captures: 0, killer: null,
      ai: isBot ? { target: null, visible: false, react: 0, burst: 0, pause: 0.2, strafe: 0, strafeT: 0, senseT: R() * 0.2, path: null, pi: 0, repathT: 0, lastSeen: null, lastSeenT: 99, attacker: null, stuckT: 0, sx: 0, sz: 0 } : null,
    };
    this.chars.push(c);
    return c;
  }

  begin() { for (const c of this.chars) if (!c.remote) this.spawn(c); this.emit('begin', {}); }

  isEnemy(a, b) { return a !== b && (!this.mode.teams || a.team !== b.team); }

  pickSpawn(c) {
    const list = this.mode.teams ? this.world.spawns[c.team] : this.world.spawnsAll;
    let best = list[0], bs = -1;
    for (const s of list) {
      let dmin = 60;
      for (const o of this.chars) {
        if (o === c || !o.alive) continue;
        const d = Math.hypot(o.x - s[0], o.z - s[1]);
        if (this.isEnemy(c, o)) { if (d < dmin) dmin = d; } else if (d < 1.4) dmin = 0;
      }
      const sc = dmin + R() * 6;
      if (sc > bs) { bs = sc; best = s; }
    }
    return best;
  }

  spawn(c, at) {
    const s = at || this.pickSpawn(c);
    c.x = s[0]; c.z = s[1]; c.y = 0; c.vx = c.vy = c.vz = 0; c.onGround = true;
    c.yaw = Math.atan2(c.x, c.z); c.pitch = 0; // regarde vers le centre de la carte
    c.hp = 100; c.alive = true; c.protT = 1.6; c.sinceDmg = 99; c.spotT = 99; c.fireCd = 0.2; c.reloadT = 0; c.swapT = 0; c.ads = 0; c.bloom = 0; c.wi = 0; c.killer = null;
    for (const w of c.weapons) { w.mag = w.def.mag; w.reserve = c.isBot ? 9999 : w.def.reserve; }
    c.equip.count = (EQUIPMENT[c.equip.id] || EQUIPMENT.frag).count;
    if (c.ai) Object.assign(c.ai, { target: null, visible: false, path: null, lastSeen: null, lastSeenT: 99, attacker: null, stuckT: 0, sx: c.x, sz: c.z });
    this.emit('spawn', { c });
  }

  // ───────────────────────── boucle ─────────────────────────
  update(dt) {
    if (this.over) return;
    this.t += dt; this.timeLeft -= dt;
    for (const c of this.chars) {
      if (c.remote) continue;
      if (!c.alive) { if (!this.net) { c.respawnT -= dt; if (c.respawnT <= 0) this.spawn(c); } continue; }
      if (c.isBot) this.think(c, dt);
      this.step(c, dt);
    }
    this.updateNades(dt);
    if (this.zones.length) this.updateZones(dt);
    if (this.timeLeft <= 0 && !this.net) this.finish();
  }

  step(c, dt) {
    const w = c.weapons[c.wi], def = w.def;
    c.fireCd -= dt; c.spotT += dt; c.sinceDmg += dt;
    if (c.protT > 0) c.protT -= dt;
    if (c.swapT > 0) c.swapT -= dt;
    if (c.reloadT > 0) {
      c.reloadT -= dt;
      if (c.reloadT <= 0) { const take = Math.min(def.mag - w.mag, w.reserve); w.mag += take; w.reserve -= take; this.emit('reloaded', { c }); }
    }
    if (!this.net && c.sinceDmg > 5 && c.hp < 100) c.hp = Math.min(100, c.hp + 28 * dt);
    let wx = c.wx, wz = c.wz; const wl = Math.hypot(wx, wz);
    if (wl > 1) { wx /= wl; wz /= wl; }
    c.sprinting = c.wantSprint && wl > 0.5 && c.onGround && !c.wantFire && !c.wantAds;
    c.ads += (((c.wantAds && c.reloadT <= 0 && c.swapT <= 0 && !c.sprinting) ? 1 : 0) - c.ads) * Math.min(1, dt * 13);
    c.bloom = Math.max(0, c.bloom - dt * 0.07);
    const sp = (c.sprinting ? SPRINT : WALK) * def.mobility * (1 - 0.42 * c.ads);
    const k = Math.min(1, (c.onGround ? 14 : 3) * dt);
    c.vx += (wx * sp - c.vx) * k; c.vz += (wz * sp - c.vz) * k;
    if (c.wantJump && c.onGround) { c.vy = JUMP; c.onGround = false; this.emit('jump', { c }); }
    c.vy -= G * dt;
    this.col.move(c, c.vx * dt, c.vy * dt, c.vz * dt);
    c.speed = Math.hypot(c.vx, c.vz);
    if (c.wantFire && (def.auto || !c.trigHeld)) this.tryFire(c);
    c.trigHeld = c.wantFire;
  }

  // ───────────────────────── armes ─────────────────────────
  reload(c) {
    const w = c.weapons[c.wi];
    if (c.reloadT > 0 || c.swapT > 0 || w.mag >= w.def.mag || w.reserve <= 0) return;
    c.reloadT = w.def.reload; this.emit('reload', { c });
  }

  swap(c) {
    if (c.swapT > 0 || !c.alive) return;
    c.wi = 1 - c.wi; c.swapT = 0.38; c.reloadT = 0; c.fireCd = Math.max(c.fireCd, 0.38); this.emit('swap', { c });
  }

  useEquip(c) {
    if (!c.alive || c.equip.count <= 0) return;
    if (c.equip.id === 'stim') {
      if (c.hp >= 100) return;
      c.equip.count--; c.hp = 100; this.emit('stim', { c });
      if (this.net) this.net.send({ t: 'stim' });
      return;
    }
    c.equip.count--;
    const p = c.pitch + 0.14, cp = Math.cos(p);
    const n = { x: c.x - Math.sin(c.yaw) * 0.4, y: c.y + EYE - 0.1, z: c.z - Math.cos(c.yaw) * 0.4, vx: -Math.sin(c.yaw) * cp * 17 + c.vx, vy: Math.sin(p) * 17 + 2, vz: -Math.cos(c.yaw) * cp * 17 + c.vz, r: 0.07, h: 0.14, onGround: false, t: 2.4, owner: c };
    this.nades.push(n); this.emit('nade', { n });
    if (this.net) this.net.send({ t: 'nade', n: [n.x, n.y, n.z, n.vx, n.vy, n.vz] });
  }

  tryFire(c) {
    const w = c.weapons[c.wi], def = w.def;
    if (c.fireCd > 0 || c.reloadT > 0 || c.swapT > 0) return;
    if (w.mag <= 0) { if (w.reserve > 0) this.reload(c); else if (!c.trigHeld) this.emit('dry', { c }); return; }
    w.mag--; c.fireCd = 60 / def.rpm; c.protT = 0; c.spotT = 0; c.shots++;
    const spread = (def.hip + (def.ads - def.hip) * c.ads) * (c.speed > 1.5 ? 1.35 : 1) * (c.onGround ? 1 : 1.8) + c.bloom;
    const ox = c.x, oy = c.y + EYE, oz = c.z;
    const ey = c.isBot ? gauss() * c.aimErr : 0, ep = c.isBot ? gauss() * c.aimErr : 0;
    let any = false;
    for (let p = 0; p < def.pellets; p++) {
      const yaw = c.yaw + (R() + R() - 1) * spread + ey, pitch = c.pitch + (R() + R() - 1) * spread + ep;
      const cp = Math.cos(pitch), dx = -Math.sin(yaw) * cp, dy = Math.sin(pitch), dz = -Math.cos(yaw) * cp;
      let t = this.col.ray(ox, oy, oz, dx, dy, dz, 300);
      const h = this.rayChars(c, ox, oy, oz, dx, dy, dz, t);
      if (h) {
        t = h.t; any = true;
        const [f0, f1, fm] = def.falloff, fo = t <= f0 ? 1 : t >= f1 ? fm : 1 - (1 - fm) * (t - f0) / (f1 - f0);
        let dmg = def.dmg * fo * (h.head ? def.head : 1);
        if (c.isBot && h.c.isPlayer) dmg *= this.diff.dmg;
        if (this.net) { if (c.isPlayer) this.net.send({ t: 'hit', v: h.c.nid, d: Math.round(dmg), h: h.head ? 1 : 0, w: def.id }); }
        else this.damage(h.c, dmg, c, h.head, def.name);
      }
      this.emit('shot', { c, ox, oy, oz, x: ox + dx * t, y: oy + dy * t, z: oz + dz * t, hit: h ? h.c : null, p });
    }
    if (any) c.hits++;
    c.bloom = Math.min(0.05, c.bloom + def.recoil * 0.45);
    this.emit('fire', { c, def });
    if (this.net && c.isPlayer) this.net.send({ t: 'fire', w: def.id });
    if (w.mag === 0 && c.isBot) this.reload(c);
  }

  /** Premier personnage ennemi touché par le rayon avant la distance max. */
  rayChars(c, ox, oy, oz, dx, dy, dz, max) {
    let best = null, bt = max, bh = false;
    for (const o of this.chars) {
      if (o === c || !o.alive || !this.isEnemy(c, o)) continue;
      const rx = o.x - ox, ry = o.y + 0.9 - oy, rz = o.z - oz, proj = rx * dx + ry * dy + rz * dz;
      if (proj < 0 || proj > bt + 1) continue;
      if (rx * rx + ry * ry + rz * rz - proj * proj > 1.3) continue;
      const th = raySphere(ox, oy, oz, dx, dy, dz, o.x, o.y + 1.62, o.z, 0.18);
      if (th >= 0 && th < bt) { bt = th; best = o; bh = true; continue; }
      _hb.x0 = o.x - 0.3; _hb.x1 = o.x + 0.3; _hb.y0 = o.y; _hb.y1 = o.y + 1.48; _hb.z0 = o.z - 0.3; _hb.z1 = o.z + 0.3;
      const tb = rayBox(ox, oy, oz, dx, dy, dz, _hb, bt);
      if (tb >= 0 && tb < bt) { bt = tb; best = o; bh = false; }
    }
    return best ? { c: best, t: bt, head: bh } : null;
  }

  damage(v, amt, a, head, wname) {
    if (!v.alive || v.protT > 0 || this.over) return;
    v.hp -= amt; v.sinceDmg = 0;
    if (v.ai && a && a !== v) v.ai.attacker = a;
    this.emit('damage', { v, a, amt, head });
    if (v.hp <= 0) this.kill(v, a, head, wname);
  }

  kill(v, a, head, wname) {
    v.alive = false; v.hp = 0; v.deaths++; v.streak = 0; v.respawnT = this.mode.id === 'ffa' ? 2.6 : 3.6; v.killer = a;
    v.wantFire = false; v.wantAds = false;
    if (a && a !== v) {
      a.kills++; a.streak++; if (a.streak > a.best) a.best = a.streak; if (head) a.heads++;
      a.score += 100 + (head ? 25 : 0);
      if (this.mode.id === 'tdm') this.scores[a.team]++;
    }
    this.emit('kill', { a, v, head, weapon: wname });
    this.checkEnd();
  }

  checkEnd() {
    if (this.net || this.over) return;
    if (this.mode.id === 'ffa') { for (const c of this.chars) if (c.kills >= this.limit) return this.finish(); } else if (this.scores[0] >= this.limit || this.scores[1] >= this.limit) this.finish();
  }

  /** Clôture la partie et détermine le vainqueur (-1 = égalité ; en mêlée, id du personnage). */
  finish(winner) {
    if (this.over) return;
    this.over = true;
    if (winner !== undefined) this.winner = winner;
    else if (this.mode.id === 'ffa') {
      const top = [...this.chars].sort((a, b) => b.kills - a.kills || a.deaths - b.deaths);
      this.winner = top.length > 1 && top[0].kills === top[1].kills ? -1 : top[0].id;
    } else this.winner = this.scores[0] === this.scores[1] ? -1 : this.scores[0] > this.scores[1] ? 0 : 1;
    this.emit('end', { winner: this.winner });
  }

  /** Résultat du joueur : 1 victoire, 0 égalité, -1 défaite. */
  outcome() {
    if (this.winner === -1) return 0;
    return (this.mode.id === 'ffa' ? this.winner === this.player.id : this.winner === this.player.team) ? 1 : -1;
  }

  // ───────────────────────── grenades ─────────────────────────
  updateNades(dt) {
    for (let i = this.nades.length - 1; i >= 0; i--) {
      const n = this.nades[i];
      n.vy -= G * dt;
      const px = n.vx, py = n.vy, pz = n.vz;
      this.col.move(n, n.vx * dt, n.vy * dt, n.vz * dt);
      if (n.vx === 0 && px !== 0) n.vx = -px * 0.42;
      if (n.vz === 0 && pz !== 0) n.vz = -pz * 0.42;
      if (n.vy === 0 && py !== 0) { n.vy = Math.abs(py) > 2.5 ? -py * 0.36 : 0; const fr = Math.pow(0.04, dt); n.vx *= fr; n.vz *= fr; }
      n.t -= dt;
      if (n.t <= 0) { this.nades.splice(i, 1); this.explode(n); }
    }
  }

  explode(n) {
    this.emit('explode', { x: n.x, y: n.y, z: n.z, n });
    if (n.ghost) return; // grenade distante : effets seulement
    for (const c of this.chars) {
      if (!c.alive) continue;
      if (c !== n.owner && !this.isEnemy(n.owner, c)) continue;
      const d = Math.hypot(c.x - n.x, c.y + 0.9 - n.y, c.z - n.z);
      if (d > 8 || !this.col.clear(n.x, n.y + 0.25, n.z, c.x, c.y + 1.1, c.z)) continue;
      const dmg = 160 * (1 - d / 8) * (c === n.owner ? 0.6 : 1);
      if (this.net) { if (n.owner.isPlayer && c !== n.owner) this.net.send({ t: 'hit', v: c.nid, d: Math.round(dmg), h: 0, w: 'frag' }); else if (c === n.owner && c.isPlayer) this.net.send({ t: 'self', d: Math.round(dmg) }); } else this.damage(c, dmg, n.owner, false, 'Grenade Orage');
    }
  }

  // ───────────────────────── domination ─────────────────────────
  updateZones(dt) {
    for (const z of this.zones) {
      const n = [0, 0];
      for (const c of this.chars) if (c.alive && c.team < 2 && Math.hypot(c.x - z.x, c.z - z.z) < 4 && c.y < 2.5) n[c.team]++;
      const t = n[0] && !n[1] ? 0 : n[1] && !n[0] ? 1 : -1;
      z.contested = n[0] > 0 && n[1] > 0;
      if (t >= 0 && t !== z.owner) {
        if (z.cap !== t) { z.cap = t; z.prog = 0; }
        z.prog += dt * Math.min(n[t], 3) / 4.5;
        if (z.prog >= 1) {
          z.owner = t; z.prog = 0; z.cap = -1;
          for (const c of this.chars) if (c.alive && c.team === t && Math.hypot(c.x - z.x, c.z - z.z) < 4) { c.captures++; c.score += 150; }
          this.emit('zone', { z, team: t });
        }
      } else if (!z.contested) { z.prog = Math.max(0, z.prog - dt * 0.5); if (z.prog === 0) z.cap = -1; }
    }
    this.domT += dt;
    if (this.domT >= 3) {
      this.domT -= 3;
      for (const z of this.zones) if (z.owner >= 0) this.scores[z.owner]++;
      this.checkEnd();
    }
  }

  // ───────────────────────── IA des bots ─────────────────────────
  sense(c) {
    const ai = c.ai, ex = c.x, ey = c.y + EYE, ez = c.z;
    let best = null, bd = 1e9;
    for (const o of this.chars) {
      if (!o.alive || !this.isEnemy(c, o)) continue;
      const dx = o.x - ex, dz = o.z - ez, d = Math.hypot(dx, dz);
      if (d > 75) continue;
      const inFov = Math.abs(angDiff(Math.atan2(-dx, -dz), c.yaw)) < 1.25;
      const aware = inFov || d < 5 || ai.attacker === o || ai.target === o || (o.spotT < 0.6 && d < 28);
      if (!aware) continue;
      if (!this.col.clear(ex, ey, ez, o.x, o.y + 1.2, o.z)) { if ((ai.attacker === o || o.spotT < 0.6) && !ai.lastSeen) { ai.lastSeen = [o.x, o.z]; ai.lastSeenT = 0; ai.path = null; } continue; }
      const cost = d * (o === ai.target ? 0.75 : 1) * (o === ai.attacker ? 0.7 : 1);
      if (cost < bd) { bd = cost; best = o; }
    }
    if (best) {
      if (ai.target !== best || !ai.visible) ai.react = this.diff.react * (0.7 + R() * 0.6);
      ai.target = best; ai.visible = true;
    } else { ai.visible = false; if (ai.target && !ai.target.alive) { ai.target = null; ai.lastSeen = null; } }
  }

  pickGoal(c) {
    const ai = c.ai;
    if (ai.lastSeen && ai.lastSeenT < 5) return ai.lastSeen;
    if (this.zones.length) {
      // déjà dans une zone à prendre : on tient la position jusqu'à la capture
      for (const z of this.zones) if (z.owner !== c.team && Math.hypot(z.x - c.x, z.z - c.z) < 3.6) { const a = R() * TAU; return [z.x + Math.cos(a) * 2, z.z + Math.sin(a) * 2]; }
      let best = this.zones[0], bs = 1e9;
      for (const z of this.zones) { const cost = Math.hypot(z.x - c.x, z.z - c.z) * (z.owner === c.team ? 2.4 : 1) + R() * 14; if (cost < bs) { bs = cost; best = z; } }
      const a = R() * TAU, r = R() * 2.6;
      return [best.x + Math.cos(a) * r, best.z + Math.sin(a) * r];
    }
    const en = this.chars.filter((o) => o.alive && this.isEnemy(c, o));
    if (en.length && R() < 0.7) { const e = en[(R() * en.length) | 0]; return [e.x + (R() - 0.5) * 12, e.z + (R() - 0.5) * 12]; }
    return this.nav.randomPoint();
  }

  think(c, dt) {
    const ai = c.ai, D = this.diff, def = c.weapons[c.wi].def;
    ai.senseT -= dt; ai.lastSeenT += dt; ai.repathT -= dt;
    if (ai.senseT <= 0) { ai.senseT = 0.12 + R() * 0.08; this.sense(c); }
    c.wantFire = false; c.wantJump = false; c.wantSprint = false; c.wantAds = false;
    let ty = c.yaw, tp = 0;
    const tgt = ai.target;
    if (tgt && tgt.alive && ai.visible) {
      const ex = tgt.x - c.x, ez = tgt.z - c.z, dist = Math.hypot(ex, ez) || 0.01;
      ty = Math.atan2(-ex, -ez); tp = Math.atan2(tgt.y + 1.15 - (c.y + EYE), dist);
      ai.lastSeen = [tgt.x, tgt.z]; ai.lastSeenT = 0; ai.path = null;
      ai.react -= dt; ai.strafeT -= dt;
      if (ai.strafeT <= 0) { ai.strafe = R() < 0.2 ? 0 : R() < 0.5 ? -1 : 1; ai.strafeT = 0.5 + R() * 0.9; }
      const pref = PREF[def.id] || 15, fwd = dist > pref * 1.3 ? 1 : dist < pref * 0.5 ? -1 : 0;
      const fx = ex / dist, fz = ez / dist;
      c.wx = fx * fwd - fz * ai.strafe; c.wz = fz * fwd + fx * ai.strafe;
      c.wantAds = dist > 14;
      c.aimErr = D.aim * (tgt.speed > 3 ? 1.5 : 1) * (c.speed > 2 ? 1.2 : 1);
      if (ai.react <= 0 && Math.abs(angDiff(ty, c.yaw)) < 0.14) {
        ai.burst -= dt;
        if (ai.burst > 0) { c.wantFire = true; if (!def.auto) c.trigHeld = false; } else if (ai.burst < -ai.pause) { ai.burst = 0.35 + R() * 0.6; ai.pause = 0.15 + R() * 0.35; }
      }
    } else {
      const done = !ai.path || ai.pi >= ai.path.length;
      if (done || ai.repathT <= 0) {
        if (done && ai.lastSeen && ai.lastSeenT > 1.5 && Math.hypot(ai.lastSeen[0] - c.x, ai.lastSeen[1] - c.z) < 2.5) ai.lastSeen = null;
        const g = this.pickGoal(c);
        ai.path = this.nav.path(c.x, c.z, g[0], g[1]); ai.pi = 0; ai.repathT = 3.5 + R() * 2.5;
      }
      c.wx = 0; c.wz = 0;
      if (ai.path && ai.pi < ai.path.length) {
        const wp = ai.path[ai.pi], dx = wp[0] - c.x, dz = wp[1] - c.z, d = Math.hypot(dx, dz);
        if (d < 0.7) ai.pi++; else { c.wx = dx / d; c.wz = dz / d; ty = Math.atan2(-dx, -dz); c.wantSprint = ai.path.length - ai.pi > 2; }
      }
      const w = c.weapons[c.wi];
      if (w.mag < w.def.mag * 0.4) this.reload(c);
      // anti-blocage
      ai.stuckT += dt;
      if (ai.stuckT > 0.9) {
        if (Math.hypot(c.x - ai.sx, c.z - ai.sz) < 0.35 && (c.wx || c.wz)) { c.wantJump = true; ai.path = null; ai.lastSeen = null; }
        ai.stuckT = 0; ai.sx = c.x; ai.sz = c.z;
      }
    }
    const turn = D.turn * dt, dy = angDiff(ty, c.yaw), dp = tp - c.pitch;
    c.yaw += Math.max(-turn, Math.min(turn, dy));
    c.pitch += Math.max(-turn, Math.min(turn, dp));
  }

  /** Classement trié (score décroissant). */
  board() { return [...this.chars].sort((a, b) => b.score - a.score || b.kills - a.kills || a.deaths - b.deaths); }
}
