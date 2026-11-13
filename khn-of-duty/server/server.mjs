// Serveur de parties KHN OF DUTY : salons, gestion des joueurs, relais d'état et arbitrage
// (points de vie, éliminations, scores, réapparitions, fin de partie). Sert aussi le jeu en HTTP.
//
// Modèle réseau : chaque client simule son propre personnage et envoie sa position (15 Hz) ; les tirs
// sont évalués par le tireur puis validés ici (cible vivante et ennemie, dégâts et cadence plausibles).
// Le serveur fait autorité sur la santé, les scores et le déroulé de la partie.
import http from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import { WEAPONS, MODES } from '../src/core/config.js';
import { MAPS } from '../src/core/maps.js';
import { buildWorldData } from '../src/game/worlddata.js';

const PORT = +(process.env.PORT || 8080), TICK = 1000 / 15, RESPAWN = 3500, PROTECT = 1600;
const dist = fileURLToPath(new URL('../dist/index.html', import.meta.url));
const MP_MODES = ['tdm', 'ffa'];
const clean = (s, n) => String(s ?? '').replace(/[<>&"]/g, '').trim().slice(0, n);
const maxDamage = (w) => (w === 'frag' ? 160 : WEAPONS[w] ? WEAPONS[w].dmg * WEAPONS[w].head * WEAPONS[w].pellets : 0);

const clients = new Map(); // ws → client
const rooms = new Map();   // id → salon
let nextId = 1, nextRoom = 1;

const send = (ws, m) => { if (ws.readyState === 1) ws.send(JSON.stringify(m)); };
const cast = (room, m, except) => { const s = JSON.stringify(m); for (const p of room.players.values()) if (p !== except && p.ws.readyState === 1) p.ws.send(s); };
const roomInfo = (r) => ({ id: r.id, name: r.name, mode: r.mode, map: r.map, limit: r.limit, max: r.max, state: r.state, host: r.host, n: r.players.size });
const roster = (r) => [...r.players.values()].map((p) => ({ id: p.id, name: p.name, team: p.team, kills: p.kills, deaths: p.deaths, score: p.score, alive: p.alive }));
const lobby = () => [...rooms.values()].map(roomInfo);
const pushLobby = () => { const l = lobby(); for (const [ws, c] of clients) if (!c.room) send(ws, { t: 'rooms', rooms: l }); };
const pushRoom = (r) => cast(r, { t: 'room', room: roomInfo(r), players: roster(r) });
const enemy = (r, a, b) => a !== b && (r.mode === 'ffa' || a.team !== b.team);

function pickTeam(r) {
  if (r.mode === 'ffa') return -1;
  let a = 0, b = 0; for (const p of r.players.values()) { if (p.team === 0) a++; else if (p.team === 1) b++; }
  return a <= b ? 0 : 1;
}

function pickSpawn(r, p) {
  const list = r.mode === 'ffa' ? r.world.spawnsAll : r.world.spawns[p.team];
  let best = list[0], bs = -1;
  for (const s of list) {
    let dmin = 60;
    for (const o of r.players.values()) {
      if (o === p || !o.alive) continue;
      const d = Math.hypot(o.x - s[0], o.z - s[1]);
      if (enemy(r, p, o)) { if (d < dmin) dmin = d; } else if (d < 1.4) dmin = 0;
    }
    const sc = dmin + Math.random() * 6; if (sc > bs) { bs = sc; best = s; }
  }
  return best;
}

function spawn(r, p) {
  const s = pickSpawn(r, p);
  Object.assign(p, { x: s[0], y: 0, z: s[1], hp: 100, alive: true, respawnAt: 0, protUntil: Date.now() + PROTECT, lastDmg: 0 });
  cast(r, { t: 'spawn', id: p.id, x: s[0], z: s[1] });
}

function join(c, r) {
  if (r.players.size >= r.max) return send(c.ws, { t: 'error', msg: 'Ce salon est complet.' });
  const p = c.p = { ws: c.ws, id: c.id, name: c.name, team: 0, x: 0, y: 0, z: 0, yaw: 0, pitch: 0, wi: 0, sp: 0, hp: 100, alive: false, respawnAt: 0, protUntil: 0, lastDmg: 0, lastHit: 0, kills: 0, deaths: 0, score: 0 };
  p.team = r.mode === 'ffa' ? 100 + p.id : pickTeam(r);
  r.players.set(p.id, p); c.room = r;
  pushRoom(r); pushLobby();
  if (r.state === 'playing') { // arrivée en cours de partie
    send(c.ws, { t: 'begin', room: roomInfo(r), players: roster(r), you: p.id, time: r.timeLeft, scores: r.scores });
    cast(r, { t: 'joined', p: { id: p.id, name: p.name, team: p.team } }, p);
    spawn(r, p);
  }
}

function leave(c) {
  const r = c.room; if (!r) return;
  r.players.delete(c.id); c.room = null; c.p = null;
  if (!r.players.size) rooms.delete(r.id);
  else {
    if (r.host === c.id) r.host = r.players.keys().next().value; // l'hôte suivant reprend la main
    cast(r, { t: 'left', id: c.id }); pushRoom(r);
    if (r.state === 'playing' && r.players.size < 2) finish(r, 'Adversaires déconnectés');
  }
  pushLobby();
}

function start(r) {
  r.state = 'playing'; r.scores = [0, 0]; r.timeLeft = MODES[r.mode].time; r.world = r.world || buildWorldData(r.map);
  for (const p of r.players.values()) Object.assign(p, { kills: 0, deaths: 0, score: 0, alive: false });
  const players = roster(r), info = roomInfo(r);
  for (const p of r.players.values()) send(p.ws, { t: 'begin', room: info, players, you: p.id, time: r.timeLeft, scores: r.scores });
  for (const p of r.players.values()) spawn(r, p);
  pushLobby();
}

function finish(r, reason) {
  if (r.state !== 'playing') return;
  let winner = -1;
  const list = [...r.players.values()];
  if (r.mode === 'ffa') { list.sort((a, b) => b.kills - a.kills || a.deaths - b.deaths); if (list.length && (list.length < 2 || list[0].kills > list[1].kills)) winner = list[0].id; } else if (r.scores[0] !== r.scores[1]) winner = r.scores[0] > r.scores[1] ? 0 : 1;
  r.state = 'lobby';
  cast(r, { t: 'end', winner, scores: r.scores, reason: reason || '' });
  pushRoom(r); pushLobby();
}

function hurt(r, v, a, dmg, head, w) {
  v.hp -= dmg; v.lastDmg = Date.now();
  cast(r, { t: 'dmg', v: v.id, a: a ? a.id : 0, hp: Math.max(0, Math.round(v.hp)), h: head ? 1 : 0, d: dmg });
  if (v.hp > 0) return;
  v.alive = false; v.deaths++; v.respawnAt = Date.now() + RESPAWN;
  if (a && a !== v) { a.kills++; a.score += 100 + (head ? 25 : 0); if (r.mode === 'tdm') r.scores[a.team]++; }
  cast(r, { t: 'kill', a: a ? a.id : 0, v: v.id, h: head ? 1 : 0, w });
  if (r.mode === 'ffa' ? (a && a.kills >= r.limit) : (r.scores[0] >= r.limit || r.scores[1] >= r.limit)) finish(r);
}

const handlers = {
  hello(c, m) { c.name = clean(m.name, 14) || `Joueur ${c.id}`; send(c.ws, { t: 'welcome', id: c.id, name: c.name, rooms: lobby() }); },
  list(c) { send(c.ws, { t: 'rooms', rooms: lobby() }); },
  create(c, m) {
    if (c.room) return;
    const mode = MP_MODES.includes(m.mode) ? m.mode : 'tdm', map = MAPS[m.map] ? m.map : 'kestrel';
    const r = { id: nextRoom++, name: clean(m.name, 24) || `Salon de ${c.name}`, mode, map, limit: Math.min(100, Math.max(1, m.limit | 0 || MODES[mode].limit)), max: Math.min(8, Math.max(2, m.max | 0 || 8)), state: 'lobby', host: c.id, players: new Map(), scores: [0, 0], timeLeft: 0, world: null };
    rooms.set(r.id, r); join(c, r);
  },
  join(c, m) { if (c.room) return; const r = rooms.get(m.id | 0); if (!r) return send(c.ws, { t: 'error', msg: 'Ce salon n\'existe plus.' }); join(c, r); },
  leave(c) { leave(c); send(c.ws, { t: 'rooms', rooms: lobby() }); },
  start(c) {
    const r = c.room; if (!r || r.host !== c.id || r.state !== 'lobby') return;
    if (r.players.size < 2) return send(c.ws, { t: 'error', msg: 'Il faut au moins deux joueurs pour lancer la partie.' });
    start(r);
  },
  st(c, m) {
    const p = c.p, s = m.s; if (!p || !p.alive || !Array.isArray(s) || s.length < 7 || !s.every(Number.isFinite)) return;
    const d = c.room.world ? c.room.world.def : null; if (!d) return;
    p.x = Math.max(-d.hx, Math.min(d.hx, s[0])); p.y = Math.max(0, Math.min(12, s[1])); p.z = Math.max(-d.hz, Math.min(d.hz, s[2]));
    p.yaw = s[3]; p.pitch = s[4]; p.wi = s[5] ? 1 : 0; p.sp = s[6];
  },
  fire(c, m) { const r = c.room, p = c.p; if (r && p && p.alive && r.state === 'playing' && WEAPONS[m.w]) { p.protUntil = 0; cast(r, { t: 'fire', id: p.id, w: m.w }, p); } },
  nade(c, m) { const r = c.room, p = c.p; if (r && p && p.alive && r.state === 'playing' && Array.isArray(m.n) && m.n.length === 6 && m.n.every(Number.isFinite)) cast(r, { t: 'nade', id: p.id, n: m.n }, p); },
  hit(c, m) {
    const r = c.room, a = c.p; if (!r || !a || r.state !== 'playing' || !a.alive) return;
    const v = r.players.get(m.v | 0), max = maxDamage(m.w), now = Date.now();
    if (!v || !v.alive || !enemy(r, a, v) || !max || now < v.protUntil) return;
    // cadence plausible : au plus un impact par plomb et par intervalle de tir (avec tolérance réseau)
    const def = WEAPONS[m.w];
    if (def) { const gap = 60000 / def.rpm * 0.5; if (now - a.lastHit < gap) { a.burst = (a.burst || 0) + 1; if (a.burst >= def.pellets) return; } else { a.burst = 0; a.lastHit = now; } }
    hurt(r, v, a, Math.max(1, Math.min(max, +m.d || 0)), !!m.h, def ? def.name : 'Grenade Orage');
  },
  self(c, m) { const r = c.room, p = c.p; if (r && p && p.alive && r.state === 'playing') hurt(r, p, null, Math.max(1, Math.min(160, +m.d || 0)), false, 'Grenade Orage'); },
  stim(c) { const r = c.room, p = c.p; if (r && p && p.alive && r.state === 'playing') { p.hp = 100; cast(r, { t: 'dmg', v: p.id, a: 0, hp: 100, h: 0, d: 0 }); } },
};

setInterval(() => {
  const now = Date.now();
  for (const r of rooms.values()) {
    if (r.state !== 'playing') continue;
    r.timeLeft -= TICK / 1000;
    const snap = [];
    for (const p of r.players.values()) {
      if (!p.alive && p.respawnAt && now >= p.respawnAt) spawn(r, p);
      if (p.alive && p.hp < 100 && now - p.lastDmg > 5000) p.hp = Math.min(100, p.hp + 28 * TICK / 1000);
      snap.push([p.id, +p.x.toFixed(2), +p.y.toFixed(2), +p.z.toFixed(2), +p.yaw.toFixed(3), +p.pitch.toFixed(3), p.wi, +p.sp.toFixed(1), Math.round(p.hp), p.alive ? 1 : 0]);
    }
    cast(r, { t: 'snap', p: snap, s: r.scores, tl: Math.max(0, Math.round(r.timeLeft)) });
    if (r.timeLeft <= 0) finish(r);
  }
}, TICK);

// Détection des connexions mortes (réseau mobile coupé sans fermeture propre) : le joueur est retiré de son salon.
setInterval(() => { for (const ws of clients.keys()) { if (!ws.live) { ws.terminate(); continue; } ws.live = false; ws.ping(); } }, 10000);

const server = http.createServer((req, res) => {
  const path = (req.url || '/').split('?')[0];
  if (path === '/health') { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify({ ok: true, rooms: rooms.size, players: clients.size })); }
  if (path === '/' || path === '/index.html') {
    if (!existsSync(dist)) { res.writeHead(503, { 'content-type': 'text/plain; charset=utf-8' }); return res.end('Jeu non construit : lancez « npm run build ».'); }
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache' }); return res.end(readFileSync(dist));
  }
  res.writeHead(404, { 'content-type': 'text/plain' }); res.end('404');
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 4096 });
wss.on('connection', (ws) => {
  const c = { ws, id: nextId++, name: '', room: null, p: null };
  clients.set(ws, c); ws.live = true; ws.on('pong', () => { ws.live = true; });
  ws.on('message', (raw) => {
    let m; try { m = JSON.parse(raw); } catch { return; }
    const h = m && typeof m.t === 'string' && Object.hasOwn(handlers, m.t) ? handlers[m.t] : null;
    if (!h || (m.t !== 'hello' && !c.name)) return;
    try { h(c, m); } catch (e) { console.error('message', m.t, e); }
  });
  ws.on('close', () => { leave(c); clients.delete(ws); });
  ws.on('error', () => {});
});

server.listen(PORT, () => console.log(`KHN OF DUTY — serveur prêt sur http://localhost:${PORT} (WebSocket /ws)`));
