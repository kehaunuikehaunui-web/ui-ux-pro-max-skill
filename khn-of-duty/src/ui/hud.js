// Interface en jeu : score, chrono, boussole, mini-carte, réticule, santé, munitions, fil des éliminations.
import { TEAM_NAMES } from '../core/config.js';

export const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const el = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
const svg = (d) => `<svg viewBox="0 0 24 24" aria-hidden="true">${d}</svg>`;
const DIRS = { 0: 'N', 45: 'NE', 90: 'E', 135: 'SE', 180: 'S', 225: 'SO', 270: 'O', 315: 'NO' };

export class Hud {
  constructor(root) {
    this.el = el(`<div class="hud" hidden>
      <div class="h-vig" data-k="vig"></div>
      <div class="h-top">
        <div class="h-score">
          <div class="h-team ally"><span class="h-tn" data-k="n0"></span><b data-k="s0">0</b><i class="h-bar"><u data-k="b0"></u></i></div>
          <div class="h-clock"><span data-k="mode"></span><b data-k="clock">0:00</b><span data-k="limit"></span></div>
          <div class="h-team foe"><b data-k="s1">0</b><span class="h-tn" data-k="n1"></span><i class="h-bar"><u data-k="b1"></u></i></div>
        </div>
        <div class="h-zones" data-k="zones" hidden></div>
        <div class="h-compass" aria-hidden="true"><div class="h-tape" data-k="tape"></div><i></i></div>
      </div>
      <canvas class="h-map" width="168" height="168" data-k="map" aria-label="Mini-carte"></canvas>
      <div class="h-feed" data-k="feed" aria-live="polite"></div>
      <div class="h-center"><div class="h-xhair" data-k="xhair"><i></i><i></i><i></i><i></i><b></b></div><div class="h-hit" data-k="hit"></div><div class="h-dmg" data-k="dmg"></div></div>
      <div class="h-scope" data-k="scope" hidden></div>
      <div class="h-notice" data-k="notice"></div>
      <div class="h-hint" data-k="hint"></div>
      <div class="h-vitals"><span class="h-lbl">Santé</span><div class="h-hp"><i data-k="hpbar"></i></div><b data-k="hp">100</b><span class="h-eq" data-k="eq"></span></div>
      <div class="h-ammo"><span class="h-wn" data-k="wname"></span><div class="h-mag"><b data-k="mag">0</b><span data-k="res"></span></div><span class="h-alt" data-k="alt"></span></div>
      <div class="h-respawn" data-k="respawn" hidden><span>Éliminé par</span><b data-k="killer"></b><em data-k="rtime"></em></div>
      <div class="h-banner" data-k="banner" hidden></div>
      <div class="h-board" data-k="board" hidden></div>
      <div class="h-fps" data-k="fps" hidden></div>
    </div>`);
    this.touch = el(`<div class="touch" hidden>
      <div class="t-move"></div><div class="t-look"></div><div class="t-stick"><i class="t-knob"></i></div>
      <button class="t-b t-fire" aria-label="Tirer">${svg('<circle cx="12" cy="12" r="7"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/>')}</button>
      <button class="t-b t-ads" aria-label="Viser">${svg('<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="2.5"/>')}</button>
      <button class="t-b t-jump" aria-label="Sauter">${svg('<path d="M5 14l7-7 7 7M5 20l7-7 7 7"/>')}</button>
      <button class="t-b t-reload" aria-label="Recharger">${svg('<path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5"/>')}</button>
      <button class="t-b t-swap" aria-label="Changer d\'arme">${svg('<path d="M4 8h14l-3-3M20 16H6l3 3"/>')}</button>
      <button class="t-b t-equip" aria-label="Équipement">${svg('<circle cx="12" cy="14" r="6"/><path d="M10 8V5h4v3M14 5h3"/>')}</button>
      <button class="t-b t-pause" aria-label="Pause">${svg('<path d="M8 5v14M16 5v14"/>')}</button>
      <button class="t-b t-score" aria-label="Tableau des scores">${svg('<path d="M4 6h16M4 12h16M4 18h16"/>')}</button>
    </div>`);
    root.append(this.el, this.touch);
    this.k = {}; this.el.querySelectorAll('[data-k]').forEach((n) => { this.k[n.dataset.k] = n; });
    this.cache = {};
    let tape = '';
    for (let d = -360; d <= 720; d += 15) { const n = ((d % 360) + 360) % 360; tape += `<span style="left:${(d + 360) * 2}px" class="${DIRS[n] ? 'card' : ''}">${DIRS[n] || n}</span>`; }
    this.k.tape.innerHTML = tape;
    this.mctx = this.k.map.getContext('2d'); this.mapT = 0; this.noticeQ = []; this.noticeT = 0; this.hitT = 0; this.dmgT = 0;
  }

  set(key, v) { if (this.cache[key] !== v) { this.cache[key] = v; this.k[key].textContent = v; } }
  show(v) { this.el.hidden = !v; this.touch.hidden = !v || !this.touchMode; }
  setTouch(v) { this.touchMode = v; this.touch.hidden = this.el.hidden || !v; this.el.classList.toggle('is-touch', v); }

  begin(m, world) {
    this.cache = {}; this.k.feed.innerHTML = ''; this.noticeQ = []; this.k.notice.innerHTML = ''; this.k.banner.hidden = true; this.k.respawn.hidden = true; this.k.board.hidden = true;
    const teams = m.mode.teams;
    const mt = m.player.team; // le camp du joueur est toujours affiché à gauche
    this.set('n0', teams ? TEAM_NAMES[mt] : 'VOUS'); this.set('n1', teams ? TEAM_NAMES[1 - mt] : 'MENEUR');
    this.set('mode', m.mode.short); this.set('limit', `Objectif ${m.limit}`);
    this.k.zones.hidden = !m.zones.length;
    this.k.zones.innerHTML = m.zones.map((z) => `<span data-z="${z.id}"><b>${z.id}</b><i></i></span>`).join('');
    // fond statique de la mini-carte (3 px par mètre)
    const d = world.def, S = 3, c = this.base = document.createElement('canvas'); c.width = (d.hx * 2 + 2) * S; c.height = (d.hz * 2 + 2) * S;
    const g = c.getContext('2d'); g.fillStyle = 'rgba(20,32,36,.92)'; g.fillRect(0, 0, c.width, c.height);
    for (const b of world.ex.boxes) { g.fillStyle = b.h > 2 ? '#5f757c' : '#3d5057'; g.fillRect((b.x - b.w / 2 + d.hx + 1) * S, (b.z - b.d / 2 + d.hz + 1) * S, b.w * S, b.d * S); }
    this.def = d; this.show(true);
    this.notice(m.mode.name.toUpperCase(), `${d.name} · objectif ${m.limit} ${m.mode.unit}`, 'gold');
  }

  update(m, p, dt, o) {
    const k = this.k, w = p.weapons[p.wi], alt = p.weapons[1 - p.wi];
    const tl = Math.max(0, m.timeLeft), mm = Math.floor(tl / 60), ss = Math.floor(tl % 60);
    this.set('clock', `${mm}:${ss < 10 ? '0' : ''}${ss}`);
    let a, b;
    if (m.mode.teams) { a = m.scores[p.team]; b = m.scores[1 - p.team]; } else { a = p.kills; b = 0; for (const c of m.chars) if (c !== p && c.kills > b) b = c.kills; }
    this.set('s0', a); this.set('s1', b);
    const ba = Math.min(100, a / m.limit * 100) | 0, bb = Math.min(100, b / m.limit * 100) | 0;
    if (this.cache.ba !== ba) { this.cache.ba = ba; k.b0.style.width = ba + '%'; }
    if (this.cache.bb !== bb) { this.cache.bb = bb; k.b1.style.width = bb + '%'; }
    const hp = Math.max(0, Math.ceil(p.hp));
    if (this.cache.hpv !== hp) { this.cache.hpv = hp; k.hp.textContent = hp; k.hpbar.style.width = hp + '%'; k.hpbar.classList.toggle('low', hp <= 35); k.vig.style.opacity = hp >= 100 ? 0 : Math.min(0.85, (100 - hp) / 90); }
    this.set('wname', w.def.name); this.set('mag', w.mag); this.set('res', `/ ${w.reserve}`); this.set('alt', `${alt.def.name} · ${alt.mag}`);
    k.mag.classList.toggle('low', w.mag <= w.def.mag * 0.25);
    this.set('eq', p.equip.count > 0 ? `${p.equip.id === 'frag' ? 'Grenade' : 'Stim'} ×${p.equip.count}` : '');
    this.set('hint', o.hint || '');
    // réticule : l'écart suit la dispersion réelle de l'arme
    const gap = Math.round(4 + o.spread * 520), hideX = !p.alive || o.scoped || (p.ads > 0.6 && w.def.id !== 'kodiak');
    if (this.cache.gap !== gap) { this.cache.gap = gap; k.xhair.style.setProperty('--gap', gap + 'px'); }
    if (this.cache.hx !== hideX) { this.cache.hx = hideX; k.xhair.classList.toggle('ads', hideX); }
    if (this.cache.sc !== o.scoped) { this.cache.sc = o.scoped; k.scope.hidden = !o.scoped; }
    k.tape.style.transform = `translateX(${-(((-p.yaw * 180 / Math.PI) % 360 + 360) % 360 + 360) * 2}px)`;
    if (this.hitT > 0) { this.hitT -= dt; if (this.hitT <= 0) k.hit.className = 'h-hit'; }
    if (this.dmgT > 0) { this.dmgT -= dt; k.dmg.style.opacity = Math.max(0, this.dmgT / 1.1); }
    if (this.noticeT > 0) { this.noticeT -= dt; if (this.noticeT <= 0) this.nextNotice(); }
    if (!p.alive && !k.respawn.hidden) this.set('rtime', m.over ? '' : `Redéploiement dans ${Math.max(0, p.respawnT).toFixed(1)} s`);
    for (const z of m.zones) {
      const n = k.zones.querySelector(`[data-z="${z.id}"]`), cls = (z.owner < 0 ? 'neu' : z.owner === p.team ? 'ally' : 'foe') + (z.contested ? ' con' : '');
      if (n.className !== cls) n.className = cls;
      n.lastElementChild.style.width = (z.prog * 100 | 0) + '%';
    }
    if (this.cache.bd !== o.board) { this.cache.bd = o.board; k.board.hidden = !o.board; }
    if (o.board) { this.boardT = (this.boardT || 0) - dt; if (this.boardT <= 0) { this.boardT = 0.4; k.board.innerHTML = boardHtml(m.board().map((c) => ({ name: c.name, kills: c.kills, deaths: c.deaths, score: c.score, me: c === p, ally: c !== p && !m.isEnemy(p, c) })), m.mode.teams, p.team); } }
    if (this.cache.fps !== o.fps) { this.cache.fps = o.fps; k.fps.hidden = !o.fps; k.fps.textContent = o.fps + ' IPS'; }
    this.mapT -= dt; if (this.mapT <= 0) { this.mapT = 0.07; this.drawMap(m, p); }
  }

  drawMap(m, p) {
    const g = this.mctx, W = 168, S = 3, d = this.def, sc = 0.62;
    g.clearRect(0, 0, W, W); g.save();
    g.beginPath(); g.arc(W / 2, W / 2, W / 2 - 1, 0, 7); g.clip();
    g.fillStyle = 'rgba(8,14,16,.72)'; g.fillRect(0, 0, W, W);
    g.translate(W / 2, W / 2); g.rotate(p.yaw); g.scale(sc, sc); g.translate(-p.x * S, -p.z * S);
    g.drawImage(this.base, (-d.hx - 1) * S, (-d.hz - 1) * S);
    for (const z of m.zones) {
      g.fillStyle = z.owner < 0 ? 'rgba(230,236,232,.3)' : z.owner === p.team ? 'rgba(63,214,192,.38)' : 'rgba(255,90,71,.38)';
      g.beginPath(); g.arc(z.x * S, z.z * S, 4 * S, 0, 7); g.fill();
    }
    for (const c of m.chars) {
      if (c === p || !c.alive) continue;
      const foe = m.isEnemy(p, c);
      if (foe && c.spotT > 2) continue; // les ennemis n'apparaissent que lorsqu'ils tirent
      g.fillStyle = foe ? '#ff5a47' : '#3fd6c0'; g.beginPath(); g.arc(c.x * S, c.z * S, 5.5, 0, 7); g.fill();
    }
    g.restore();
    g.fillStyle = '#ffb224'; g.beginPath(); g.moveTo(W / 2, W / 2 - 8); g.lineTo(W / 2 + 6, W / 2 + 6); g.lineTo(W / 2, W / 2 + 2); g.lineTo(W / 2 - 6, W / 2 + 6); g.fill();
  }

  hitmark(kill, head) { this.k.hit.className = 'h-hit on' + (kill ? ' kill' : head ? ' head' : ''); this.hitT = kill ? 0.3 : 0.14; }
  damageFrom(rel) { this.k.dmg.style.transform = `rotate(${-rel}rad)`; this.k.dmg.style.opacity = 1; this.dmgT = 1.1; }

  notice(title, sub, cls = '') { this.noticeQ.push([title, sub, cls]); if (this.noticeQ.length > 3) this.noticeQ.shift(); if (this.noticeT <= 0) this.nextNotice(); }
  nextNotice() {
    const n = this.noticeQ.shift();
    if (!n) { this.k.notice.innerHTML = ''; return; }
    this.k.notice.innerHTML = `<div class="h-n ${n[2]}"><b>${esc(n[0])}</b><span>${esc(n[1] || '')}</span></div>`; this.noticeT = 1.5;
  }

  feed(a, ak, v, vk, weapon, head) {
    const row = el(`<div class="h-row"><span class="${ak}">${esc(a)}</span><em>${esc(weapon || '')}${head ? ' · tête' : ''}</em><span class="${vk}">${esc(v)}</span></div>`);
    this.k.feed.prepend(row); while (this.k.feed.children.length > 5) this.k.feed.lastElementChild.remove();
    setTimeout(() => row.remove(), 5200);
  }

  respawn(on, killer) { this.k.respawn.hidden = !on; if (on) { this.cache.killer = null; this.set('killer', killer || ''); } }
  banner(text, cls) { this.k.banner.hidden = false; this.k.banner.className = 'h-banner ' + cls; this.k.banner.textContent = text; }
}

/** Tableau des scores (HUD et écran de résultats). */
export function boardHtml(rows, teams, mine = 0) {
  const tr = (r, i) => `<tr class="${r.me ? 'me' : r.ally ? 'ally' : 'foe'}"><td>${i + 1}</td><td>${esc(r.name)}</td><td>${r.kills}</td><td>${r.deaths}</td><td>${r.score}</td></tr>`;
  const table = (list, cap) => `<table class="board"><caption>${cap}</caption><thead><tr><th>#</th><th>Indicatif</th><th>É</th><th>M</th><th>Score</th></tr></thead><tbody>${list.map(tr).join('')}</tbody></table>`;
  if (!teams) return table(rows, 'Classement');
  return `<div class="boards">${table(rows.filter((r) => r.me || r.ally), TEAM_NAMES[mine])}${table(rows.filter((r) => !r.me && !r.ally), TEAM_NAMES[1 - mine])}</div>`;
}
