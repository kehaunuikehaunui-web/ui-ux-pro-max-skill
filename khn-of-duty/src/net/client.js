// Client réseau : connexion WebSocket, état du lobby, et synchronisation d'une partie en ligne.
// Le joueur local reste simulé ici ; les autres joueurs sont des personnages « distants » interpolés
// vers le dernier instantané reçu. Santé, éliminations, scores et réapparitions viennent du serveur.
import { WEAPONS } from '../core/config.js';
import { EYE } from '../game/physics.js';
import { angDiff } from '../game/match.js';

const r2 = (v) => Math.round(v * 100) / 100, r3 = (v) => Math.round(v * 1000) / 1000;

export class Net {
  /** @param on (événement, données) => void — 'status' | 'lobby' | 'error' | 'begin' | 'joined' | 'left' | 'closed' */
  constructor(on) { this.on = on; this.ws = null; this.id = 0; this.rooms = []; this.room = null; this.players = []; this.status = 'off'; this.error = ''; this.acc = 0; this.match = null; this.by = new Map(); }

  connect(url, name) {
    this.close(); this.status = 'connecting'; this.error = '';
    let ws;
    try { ws = this.ws = new WebSocket(url); } catch { this.status = 'off'; this.error = 'Adresse de serveur invalide. Exemple : ws://192.168.1.20:8080/ws'; this.on('status'); return; }
    ws.onopen = () => this.send({ t: 'hello', name });
    ws.onmessage = (e) => { let m; try { m = JSON.parse(e.data); } catch { return; } this.handle(m); };
    ws.onerror = () => {};
    ws.onclose = () => {
      if (this.ws !== ws) return;
      const was = this.status; this.ws = null; this.status = 'off'; this.room = null;
      this.error = was === 'connecting' ? 'Connexion impossible. Vérifiez l\'adresse et que le serveur est bien lancé.' : 'Connexion au serveur perdue.';
      this.on('closed');
    };
    this.on('status');
  }

  close() { const ws = this.ws; this.ws = null; this.status = 'off'; this.room = null; this.match = null; if (ws) { try { ws.close(); } catch { /* déjà fermé */ } } }
  send(m) { if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify(m)); }
  leave() { this.send({ t: 'leave' }); this.room = null; this.match = null; }

  /** Associe la partie locale : les messages de jeu lui sont appliqués à partir de maintenant. */
  bind(match) { this.match = match; this.acc = 0; this.by = new Map(); for (const c of match.chars) if (c.nid) this.by.set(c.nid, c); }

  handle(m) {
    switch (m.t) {
      case 'welcome': this.id = m.id; this.name = m.name; this.rooms = m.rooms; this.status = 'on'; this.on('status'); break;
      case 'rooms': this.rooms = m.rooms; this.on('lobby'); break;
      case 'room': this.room = m.room; this.players = m.players; this.on('lobby'); break;
      case 'error': this.error = m.msg; this.on('error', m.msg); break;
      case 'begin': this.room = m.room; this.players = m.players; this.on('begin', m); break;
      default: if (this.match) this.game(m);
    }
  }

  game(m) {
    const M = this.match, by = this.by;
    switch (m.t) {
      case 'snap':
        M.scores[0] = m.s[0]; M.scores[1] = m.s[1]; if (Math.abs(M.timeLeft - m.tl) > 1.5) M.timeLeft = m.tl;
        for (const s of m.p) {
          const c = by.get(s[0]); if (!c) continue;
          if (c.alive) c.hp = s[8];
          if (!c.remote) continue;
          c.tx = s[1]; c.ty = s[2]; c.tz = s[3]; c.tyaw = s[4]; c.tpitch = s[5]; c.wi = s[6]; c.speed = s[7];
          if (s[9] && !c.alive) { c.alive = true; c.hp = s[8]; c.x = c.tx; c.y = c.ty; c.z = c.tz; c.yaw = c.tyaw; M.emit('spawn', { c }); } // joueur déjà en jeu à notre arrivée
        }
        break;
      case 'spawn': {
        const c = by.get(m.id); if (!c) break;
        if (c === M.player) M.spawn(c, [m.x, m.z]);
        else { c.x = c.tx = m.x; c.y = c.ty = 0; c.z = c.tz = m.z; c.hp = 100; c.alive = true; c.speed = 0; M.emit('spawn', { c }); }
        break;
      }
      case 'dmg': { const v = by.get(m.v); if (!v) break; v.hp = m.hp; v.sinceDmg = 0; if (m.d > 0) M.emit('damage', { v, a: by.get(m.a) || null, amt: m.d, head: !!m.h }); break; }
      case 'kill': { const v = by.get(m.v); if (v && v.alive) M.kill(v, by.get(m.a) || null, !!m.h, m.w); break; }
      case 'fire': {
        const c = by.get(m.id), def = WEAPONS[m.w]; if (!c || !def || !c.alive) break;
        c.spotT = 0; M.emit('fire', { c, def });
        const cp = Math.cos(c.pitch), dx = -Math.sin(c.yaw) * cp, dy = Math.sin(c.pitch), dz = -Math.cos(c.yaw) * cp, oy = c.y + EYE;
        const t = M.col.ray(c.x, oy, c.z, dx, dy, dz, 120);
        M.emit('shot', { c, ox: c.x, oy, oz: c.z, x: c.x + dx * t, y: oy + dy * t, z: c.z + dz * t, hit: null, p: 0 }); // traçante le long de la visée distante
        break;
      }
      case 'nade': { const c = by.get(m.id); if (!c) break; const n = { x: m.n[0], y: m.n[1], z: m.n[2], vx: m.n[3], vy: m.n[4], vz: m.n[5], r: 0.07, h: 0.14, onGround: false, t: 2.4, owner: c, ghost: true }; M.nades.push(n); M.emit('nade', { n }); break; }
      case 'joined': { if (by.has(m.p.id)) break; const c = M.addRemote(m.p); by.set(m.p.id, c); this.on('joined', c); break; }
      case 'left': { const c = by.get(m.id); if (!c) break; by.delete(m.id); c.alive = false; const i = M.chars.indexOf(c); if (i >= 0) M.chars.splice(i, 1); this.on('left', c); break; }
      case 'end': {
        M.scores[0] = m.scores[0]; M.scores[1] = m.scores[1];
        const w = M.mode.teams ? m.winner : (by.get(m.winner) ? by.get(m.winner).id : -1); // en mêlée : identifiant serveur → personnage local
        M.finish(w); break;
      }
      default: break;
    }
  }

  /** Appelé à chaque image : envoi de l'état local (15 Hz) et interpolation des joueurs distants. */
  tick(dt, M) {
    const p = M.player; this.acc += dt;
    if (this.acc >= 1 / 15) { this.acc = 0; if (p.alive) this.send({ t: 'st', s: [r2(p.x), r2(p.y), r2(p.z), r3(p.yaw), r3(p.pitch), p.wi, r2(p.speed)] }); }
    if (!p.alive) p.respawnT -= dt;
    const k = Math.min(1, dt * 14);
    for (const c of M.chars) {
      if (!c.remote) continue;
      c.spotT += dt; if (!c.alive) continue;
      c.x += (c.tx - c.x) * k; c.y += (c.ty - c.y) * k; c.z += (c.tz - c.z) * k;
      c.yaw += angDiff(c.tyaw, c.yaw) * k; c.pitch += (c.tpitch - c.pitch) * k;
    }
  }
}
