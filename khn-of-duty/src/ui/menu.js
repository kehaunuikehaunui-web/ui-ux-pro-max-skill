// Menus : accueil, préparation de partie, arsenal, opérateur, carrière, paramètres, pause, résultats.
// Rendu par gabarits + délégation d'événements (data-a="action:arg").
import { WEAPONS, PRIMARIES, SECONDARIES, EQUIPMENT, OPERATORS, CAMOS, MODES, DIFFICULTY, VERSION, MAX_LEVEL, TIERS, levelOf, rankOf, tierOf, xpAt, allUnlocks } from '../core/config.js';
import { MAPS, MAP_IDS, expandMap } from '../core/maps.js';
import { saveProfile, resetProfile } from '../core/storage.js';
import { esc, boardHtml } from './hud.js';
import { onlineScreen, onlineAction } from './online.js';

const I = {
  play: '<path class="f" d="M7 4l13 8-13 8z"/>', users: '<circle cx="9" cy="8" r="3.5"/><path d="M2 20c0-4 3-6 7-6s7 2 7 6"/><circle cx="17.5" cy="9" r="2.5"/><path d="M17 14c3 0 5 2 5 5"/>',
  cross: '<circle cx="12" cy="12" r="7"/><path d="M12 2v6M12 16v6M2 12h6M16 12h6"/>', user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-5 3.5-7.5 8-7.5s8 2.5 8 7.5"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>', sliders: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
  lock: '<rect x="5" y="11" width="14" height="9"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>', back: '<path d="M14 5l-7 7 7 7"/>', check: '<path d="M4 12l5 5L20 6"/>',
};
export const ic = (n) => `<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">${I[n]}</svg>`;
const pct = (v) => Math.round(Math.max(0, Math.min(1, v)) * 100);
const hex = (c) => '#' + c.toString(16).padStart(6, '0');
const fmtTime = (s) => { const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60); return h ? `${h} h ${m} min` : `${m} min`; };
const sizeLabel = (mode, n) => (MODES[mode].teams ? `${n} contre ${n}` : `${n * 2} joueurs`);

function progress(xp) {
  const L = levelOf(xp);
  if (L >= MAX_LEVEL) return { L, cur: 0, need: 0, pct: 100 };
  const cur = xp - xpAt(L), need = xpAt(L + 1) - xpAt(L);
  return { L, cur, need, pct: pct(cur / need) };
}

function drawThumb(c, id) {
  const d = MAPS[id], g = c.getContext('2d'), W = c.width, H = c.height, s = Math.min(W / (d.hx * 2 + 4), H / (d.hz * 2 + 4));
  g.fillStyle = hex(d.theme.skyTop); g.fillRect(0, 0, W, H);
  g.fillStyle = hex(d.theme.ground); g.fillRect(W / 2 - d.hx * s, H / 2 - d.hz * s, d.hx * 2 * s, d.hz * 2 * s);
  for (const b of expandMap(d).boxes) { const m = d.mats[b.m]; g.fillStyle = m ? hex(m[1]) : '#888'; g.globalAlpha = b.h > 2 ? 1 : 0.7; g.fillRect(W / 2 + (b.x - b.w / 2) * s, H / 2 + (b.z - b.d / 2) * s, b.w * s, b.d * s); }
  g.globalAlpha = 1; g.fillStyle = 'rgba(8,14,16,.35)'; g.fillRect(0, 0, W, H);
}

export class Menu {
  constructor(root, profile, api) {
    this.p = profile; this.api = api; this.screen = 'home'; this.tab = 'primary'; this.res = null; this.arm = null; this.from = 'home';
    this.el = document.createElement('div'); this.el.className = 'ui'; root.appendChild(this.el);
    this.el.addEventListener('click', (e) => { const b = e.target.closest('[data-a]'); if (b && !b.disabled) { api.click(); this.act(b.dataset.a, b); } });
    this.el.addEventListener('input', (e) => this.onInput(e));
    this.el.addEventListener('submit', (e) => e.preventDefault());
    this.go('home');
  }

  hide() { this.el.hidden = true; }
  save() { saveProfile(this.p); }
  level() { return levelOf(this.p.xp); }

  go(screen, keepFrom) {
    if (!keepFrom && (screen === 'settings')) this.from = this.screen === 'pause' ? 'pause' : 'home';
    this.screen = screen; this.arm = null; this.el.hidden = false;
    this.render(true); this.api.onScreen(screen, this);
  }

  back() {
    if (this.el.hidden) return;
    if (this.screen === 'pause') return this.act('resume');
    if (this.screen === 'results') return this.go('home');
    if (this.screen === 'settings' && this.from === 'pause') return this.go('pause', true);
    if (this.screen !== 'home') this.go('home');
  }

  render(enter) {
    const body = this.el.querySelector('.scr-body'), st = body ? body.scrollTop : 0, fk = document.activeElement && document.activeElement.dataset ? document.activeElement.dataset.a : null;
    this.el.innerHTML = this[this.screen]();
    this.el.className = 'ui' + (this.screen === 'pause' || (this.screen === 'settings' && this.from === 'pause') ? ' overlay' : '') + (this.screen === 'arsenal' || this.screen === 'operator' ? ' show' : '');
    if (enter) this.el.firstElementChild.classList.add('enter');
    else { const nb = this.el.querySelector('.scr-body'); if (nb) nb.scrollTop = st; if (fk) { const f = this.el.querySelector(`[data-a="${fk}"]`); if (f) f.focus({ preventScroll: true }); } }
    this.el.querySelectorAll('canvas[data-map]').forEach((c) => drawThumb(c, c.dataset.map));
    if (this.screen === 'results') requestAnimationFrame(() => { const b = this.el.querySelector('.xpfill'); if (b) b.style.width = b.dataset.to + '%'; });
  }

  act(a, btn) {
    const [name, x, y] = a.split(':'), p = this.p, L = this.level();
    switch (name) {
      case 'go': return this.go(x);
      case 'back': return this.back();
      case 'deploy': this.save(); return this.api.deploy();
      case 'set': {
        const s = p.setup; s[x] = (x === 'size' || x === 'limit') ? +y : y;
        if (x === 'mode') s.limit = MODES[y].limit;
        this.save(); this.render(); if (x === 'map') this.api.onScreen(this.screen, this); return;
      }
      case 'tab': this.tab = x; this.render(); return this.api.onScreen(this.screen, this);
      case 'pick': {
        const item = x === 'equip' ? EQUIPMENT[y] : x === 'camo' ? CAMOS.find((c) => c.id === y) : WEAPONS[y];
        if (!item || item.unlock > L) return;
        if (x === 'camo') p.camo = y; else p.loadout[x] = y;
        this.save(); this.render(); return this.api.onScreen(this.screen, this);
      }
      case 'op': { const o = OPERATORS.find((v) => v.id === x); if (!o || o.unlock > L) return; p.operator = x; this.save(); this.render(); return this.api.onScreen(this.screen, this); }
      case 'quality': p.settings.quality = x; this.save(); this.render(); return this.api.applySettings(true);
      case 'toggle': p.settings[x] = !p.settings[x]; this.save(); this.render(); return this.api.applySettings();
      case 'arm': this.arm = x; return this.render();
      case 'disarm': this.arm = null; return this.render();
      case 'reset': resetProfile(p); this.arm = null; this.api.toast('Progression réinitialisée'); return this.render();
      case 'resume': this.hide(); return this.api.resume();
      case 'quit': this.api.quit(); return this.go('home');
      case 'replay': return this.api.deploy();
      case 'home': return this.go('home');
      default: return onlineAction(this, name, x, y, btn);
    }
  }

  onInput(e) {
    const t = e.target, s = this.p.settings;
    if (t.id === 'set-name') { this.p.name = t.value.replace(/[<>]/g, '').slice(0, 14).trim() || 'Opérateur'; return this.save(); }
    if (t.id === 'set-server') { s.server = t.value.trim(); return this.save(); }
    const map = { 'set-sens': 'sens', 'set-fov': 'fov', 'set-volume': 'volume' };
    if (map[t.id]) {
      s[map[t.id]] = +t.value; this.save(); this.api.applySettings();
      const o = this.el.querySelector(`output[for="${t.id}"]`); if (o) o.textContent = this.fmtSetting(map[t.id]);
    }
  }
  fmtSetting(k) { const s = this.p.settings; return k === 'sens' ? s.sens.toFixed(2) : k === 'fov' ? `${s.fov}°` : `${Math.round(s.volume * 100)} %`; }

  // ───────────────────────── gabarits ─────────────────────────
  frame(title, sub, content, foot = '') {
    return `<section class="screen sub"><header class="scr-head"><button class="back" data-a="back">${ic('back')}<span>Retour</span></button><div><h1>${title}</h1><p>${sub}</p></div></header><div class="scr-body">${content}</div>${foot ? `<footer class="scr-foot">${foot}</footer>` : ''}</section>`;
  }

  card() {
    const p = this.p, pr = progress(p.xp);
    return `<div class="pcard cut"><div class="pc-lvl"><b>${pr.L}</b><span>Niv.</span></div><div class="pc-main"><strong>${esc(p.name)}</strong><span>${rankOf(pr.L)} · ${tierOf(p.rating)} ${p.rating}</span>
      <div class="bar" role="progressbar" aria-valuenow="${pr.pct}" aria-valuemin="0" aria-valuemax="100" aria-label="Progression vers le niveau suivant"><i style="width:${pr.pct}%"></i></div><small>${pr.need ? `${pr.cur} / ${pr.need} XP` : 'Niveau maximal'}</small></div></div>`;
  }

  home() {
    const s = this.p.setup;
    const nav = (go, icon, label, sub) => `<button class="nav" data-a="go:${go}">${ic(icon)}<span><b>${label}</b><small>${sub}</small></span></button>`;
    return `<section class="screen home">
      <div class="home-l">
        <div class="brand"><h1 class="mark" aria-label="KHN OF DUTY"><span class="mk-k">KHN</span><span class="mk-o">OF DUTY</span></h1><p class="mk-tag">Force d'intervention KHN · Théâtre 07</p></div>
        <nav class="rail" aria-label="Menu principal">
          ${nav('play', 'play', 'Entraînement', 'Affrontez les bots de la Légion Cendre')}
          ${nav('online', 'users', 'Multijoueur', 'Parties en ligne')}
          ${nav('arsenal', 'cross', 'Arsenal', 'Armes, équipements, camouflages')}
          ${nav('operator', 'user', 'Opérateur', 'Indicatif et apparence')}
          ${nav('career', 'chart', 'Carrière', 'Grade, classement, statistiques')}
          ${nav('settings', 'sliders', 'Paramètres', 'Commandes, image, son')}
        </nav>
      </div>
      <div class="home-r">
        ${this.card()}
        <div class="quick cut"><span class="eyebrow">Déploiement rapide</span><h2>${MODES[s.mode].name}</h2><p>${MAPS[s.map].name} · ${DIFFICULTY[s.difficulty].name} · ${sizeLabel(s.mode, s.size)}</p>
          <button class="btn primary" data-a="deploy">${ic('play')}Déployer</button></div>
      </div>
      <footer class="foot">v${VERSION} · prototype · univers et contenus fictifs originaux</footer>
    </section>`;
  }

  play() {
    const s = this.p.setup, m = MODES[s.mode];
    const seg = (key, opts, cur) => `<div class="seg" role="group">${opts.map(([v, l]) => `<button data-a="set:${key}:${v}" aria-pressed="${String(v) === String(cur)}">${l}</button>`).join('')}</div>`;
    const modes = Object.values(MODES).map((x) => `<button class="pick cut" data-a="set:mode:${x.id}" aria-pressed="${x.id === s.mode}"><span class="eyebrow">${x.short}</span><b>${x.name}</b><small>${x.desc}</small></button>`).join('');
    const maps = MAP_IDS.map((id) => `<button class="pick map cut" data-a="set:map:${id}" aria-pressed="${id === s.map}"><canvas data-map="${id}" width="240" height="132"></canvas><b>${MAPS[id].name}</b><small>${MAPS[id].sub}</small></button>`).join('');
    return this.frame('Entraînement', 'Configurez l\'opération contre les bots', `
      <div class="grp"><h2>Mode de jeu</h2><div class="cards">${modes}</div></div>
      <div class="grp"><h2>Carte</h2><div class="cards">${maps}</div></div>
      <div class="grp opts">
        <div><h2>Difficulté des bots</h2>${seg('difficulty', Object.values(DIFFICULTY).map((d) => [d.id, d.name]), s.difficulty)}</div>
        <div><h2>Effectif</h2>${seg('size', [3, 4, 5, 6].map((n) => [n, m.teams ? `${n}v${n}` : `${n * 2}`]), s.size)}</div>
        <div><h2>Objectif (${m.unit})</h2>${seg('limit', m.limits.map((n) => [n, n]), s.limit)}</div>
      </div>`,
    `<p>${m.name} · ${MAPS[s.map].name} · ${sizeLabel(s.mode, s.size)} · premier à ${s.limit}</p><button class="btn primary" data-a="deploy">${ic('play')}Déployer</button>`);
  }

  arsenal() {
    const p = this.p, L = this.level(), tab = this.tab;
    const tabs = [['primary', 'Principale'], ['secondary', 'Secondaire'], ['equip', 'Équipement'], ['camo', 'Camouflage']];
    const item = (slot, id, name, cls, unlock, sel, extra = '') => `<button class="item cut" data-a="pick:${slot}:${id}" aria-pressed="${sel}" ${unlock > L ? 'aria-disabled="true"' : ''}>${extra}<span><b>${name}</b><small>${cls}</small></span>${unlock > L ? `<em class="lock">${ic('lock')}Niv. ${unlock}</em>` : sel ? `<em class="eq">${ic('check')}Équipé</em>` : ''}</button>`;
    let list = '', detail = '';
    if (tab === 'primary' || tab === 'secondary') {
      const ids = tab === 'primary' ? PRIMARIES : SECONDARIES, cur = WEAPONS[p.loadout[tab]];
      list = ids.map((id) => { const w = WEAPONS[id]; return item(tab, id, w.name, w.cls, w.unlock, p.loadout[tab] === id); }).join('');
      const bar = (l, v) => `<div class="stat"><span>${l}</span><div class="bar"><i style="width:${pct(v)}%"></i></div></div>`;
      detail = `<div class="detail cut"><span class="eyebrow">${cur.cls}</span><h2>${cur.name}</h2>
        ${bar('Dégâts', Math.pow(Math.min(1, cur.dmg * cur.pellets / 100), 0.6))}${bar('Cadence', cur.rpm / 900)}${bar('Portée', cur.falloff[1] / 100)}${bar('Précision', 1 - cur.ads / 0.05)}${bar('Mobilité', (cur.mobility - 0.85) / 0.25)}
        <p class="meta">Chargeur ${cur.mag} · Réserve ${cur.reserve} · ${cur.auto ? 'Automatique' : 'Coup par coup'}${cur.scope ? ' · Lunette ×4' : ''}</p></div>`;
    } else if (tab === 'equip') {
      list = Object.values(EQUIPMENT).map((e) => item('equip', e.id, e.name, `${e.cls} · ×${e.count}`, e.unlock, p.loadout.equip === e.id)).join('');
      const e = EQUIPMENT[p.loadout.equip]; detail = `<div class="detail cut"><span class="eyebrow">${e.cls}</span><h2>${e.name}</h2><p>${e.desc}</p><p class="meta">${e.count} par déploiement</p></div>`;
    } else {
      list = CAMOS.map((c) => item('camo', c.id, c.name, 'Finition d\'arme', c.unlock, p.camo === c.id, `<i class="sw" style="background:${hex(c.color)}"></i>`)).join('');
      detail = `<div class="detail cut"><span class="eyebrow">Camouflage</span><h2>${CAMOS.find((c) => c.id === p.camo).name}</h2><p>Appliqué à toutes vos armes. Les finitions se débloquent en montant de niveau.</p></div>`;
    }
    return this.frame('Arsenal', 'Composez votre équipement de déploiement', `<div class="show-space"></div>
      <div class="seg tabs" role="tablist">${tabs.map(([id, l]) => `<button role="tab" data-a="tab:${id}" aria-selected="${id === tab}" aria-pressed="${id === tab}">${l}</button>`).join('')}</div>
      <div class="list">${list}</div>${detail}`);
  }

  operator() {
    const p = this.p, L = this.level();
    const ops = OPERATORS.map((o) => `<button class="item cut" data-a="op:${o.id}" aria-pressed="${p.operator === o.id}" ${o.unlock > L ? 'aria-disabled="true"' : ''}><i class="sw" style="background:${hex(o.color)}"></i><span><b>${o.name}</b><small>${o.role} — ${o.bio}</small></span>${o.unlock > L ? `<em class="lock">${ic('lock')}Niv. ${o.unlock}</em>` : p.operator === o.id ? `<em class="eq">${ic('check')}Actif</em>` : ''}</button>`).join('');
    return this.frame('Opérateur', 'Votre identité sur le terrain', `<div class="show-space tall"></div>
      <div class="grp"><label class="field" for="set-name"><span>Indicatif</span><input id="set-name" type="text" maxlength="14" autocomplete="off" spellcheck="false" value="${esc(p.name)}"><small>14 caractères maximum. Visible dans les scores.</small></label></div>
      <div class="grp"><h2>Opérateurs</h2><div class="list">${ops}</div></div>`);
  }

  career() {
    const p = this.p, s = p.stats, pr = progress(p.xp), tier = tierOf(p.rating);
    const next = TIERS.find(([r]) => r > p.rating);
    const st = (l, v) => `<div class="kv"><span>${l}</span><b>${v}</b></div>`;
    const up = allUnlocks().filter((u) => u.level > pr.L).slice(0, 5);
    const O = { 1: ['Victoire', 'ok'], 0: ['Égalité', ''], '-1': ['Défaite', 'bad'] };
    const hist = p.history.length ? `<div class="tblwrap"><table class="tbl"><thead><tr><th>Résultat</th><th>Mode</th><th>Carte</th><th>Score</th><th>É / M</th><th>XP</th></tr></thead><tbody>${p.history.map((h) => `<tr><td class="${O[h.outcome][1]}">${O[h.outcome][0]}</td><td>${MODES[h.mode] ? MODES[h.mode].short : h.mode}${h.online ? ' · en ligne' : ''}</td><td>${MAPS[h.map] ? MAPS[h.map].name : h.map}</td><td>${esc(h.line)}</td><td>${h.kills} / ${h.deaths}</td><td>+${h.xp}</td></tr>`).join('')}</tbody></table></div>` : '<p class="empty">Aucune partie jouée. Lancez un entraînement pour ouvrir votre dossier.</p>';
    return this.frame('Carrière', 'Grade, classement et états de service', `
      <div class="career-top">
        <div class="rankbox cut"><span class="eyebrow">Grade</span><h2>${rankOf(pr.L)}</h2><div class="lvl"><b>${pr.L}</b><div><div class="bar"><i style="width:${pr.pct}%"></i></div><small>${pr.need ? `${pr.cur} / ${pr.need} XP vers le niveau ${pr.L + 1}` : 'Niveau maximal atteint'}</small></div></div></div>
        <div class="rankbox cut"><span class="eyebrow">Classement · cet appareil</span><h2>${tier}</h2><div class="lvl"><b>${p.rating}</b><div><small>${next ? `${next[0] - p.rating} points avant ${next[1]}` : 'Palier le plus élevé'}</small><small>Calculé sur vos résultats, pondéré par la difficulté.</small></div></div></div>
      </div>
      <div class="grp"><h2>États de service</h2><div class="kvs">
        ${st('Parties', s.matches)}${st('Victoires', s.wins)}${st('Taux de victoire', s.matches ? pct(s.wins / s.matches) + ' %' : '—')}${st('Éliminations', s.kills)}${st('Morts', s.deaths)}${st('Ratio É/M', s.deaths ? (s.kills / s.deaths).toFixed(2) : s.kills ? s.kills.toFixed(2) : '—')}
        ${st('Tirs à la tête', s.heads)}${st('Précision', s.shots ? pct(s.hits / s.shots) + ' %' : '—')}${st('Meilleure série', s.best)}${st('Zones capturées', s.captures)}${st('Temps de jeu', fmtTime(s.time))}
      </div></div>
      <div class="grp"><h2>Prochains déblocages</h2>${up.length ? `<ul class="unl">${up.map((u) => `<li><b>Niv. ${u.level}</b><span>${u.name}</span><small>${u.kind}</small></li>`).join('')}</ul>` : '<p class="empty">Tout est débloqué.</p>'}</div>
      <div class="grp"><h2>Dernières parties</h2>${hist}</div>`);
  }

  settings() {
    const s = this.p.settings;
    const range = (id, label, key, min, max, step) => `<label class="field range" for="${id}"><span>${label}<output for="${id}">${this.fmtSetting(key)}</output></span><input id="${id}" type="range" min="${min}" max="${max}" step="${step}" value="${s[key]}"></label>`;
    const tog = (key, label, hint) => `<button class="toggle" data-a="toggle:${key}" role="switch" aria-checked="${!!s[key]}"><span><b>${label}</b><small>${hint}</small></span><i></i></button>`;
    const ctl = [['Se déplacer', 'ZQSD / WASD', 'Joystick gauche'], ['Viser', 'Souris', 'Glisser à droite'], ['Tirer', 'Clic gauche', 'Bouton de tir'], ['Viser à l\'épaule', 'Clic droit', 'Bouton de visée'], ['Sprinter', 'Maj', 'Joystick à fond'], ['Sauter', 'Espace', 'Bouton saut'], ['Recharger', 'R', 'Bouton recharge'], ['Changer d\'arme', '1 · 2 · molette', 'Bouton arme'], ['Équipement', 'G', 'Bouton équipement'], ['Scores', 'Tab', 'Bouton scores'], ['Pause', 'Échap · P', 'Bouton pause']];
    return this.frame('Paramètres', 'Commandes, image et son', `
      <div class="grp"><h2>Commandes</h2>${range('set-sens', 'Sensibilité de visée', 'sens', 0.2, 3, 0.05)}${tog('invertY', 'Inverser l\'axe vertical', 'Pousser vers le haut fait regarder vers le bas')}${tog('autoFire', 'Tir automatique (tactile)', 'Tire dès que le réticule est sur un ennemi')}</div>
      <div class="grp"><h2>Image</h2>${range('set-fov', 'Champ de vision', 'fov', 60, 100, 1)}
        <div class="field"><span>Qualité graphique</span><div class="seg">${[['low', 'Basse'], ['med', 'Moyenne'], ['high', 'Haute']].map(([v, l]) => `<button data-a="quality:${v}" aria-pressed="${s.quality === v}">${l}</button>`).join('')}</div><small>Basse : sans ombres, idéale sur téléphone. Haute : ombres fines et rendu net.</small></div>
        ${tog('showFps', 'Afficher les images par seconde', 'Compteur discret en haut de l\'écran')}</div>
      <div class="grp"><h2>Son</h2>${range('set-volume', 'Volume général', 'volume', 0, 1, 0.05)}</div>
      <div class="grp"><h2>Référence des commandes</h2><div class="tblwrap"><table class="tbl"><thead><tr><th>Action</th><th>Clavier / souris</th><th>Tactile</th></tr></thead><tbody>${ctl.map((r) => `<tr><td>${r[0]}</td><td>${r[1]}</td><td>${r[2]}</td></tr>`).join('')}</tbody></table></div></div>
      ${this.from === 'pause' ? '' : `<div class="grp"><h2>Données</h2>${this.arm === 'reset' ? `<div class="confirm"><p>Effacer niveau, classement, statistiques et historique ? Cette action est définitive.</p><button class="btn danger" data-a="reset">Tout effacer</button><button class="btn" data-a="disarm">Annuler</button></div>` : '<button class="btn" data-a="arm:reset">Réinitialiser la progression</button>'}</div>`}`);
  }

  online() { return onlineScreen(this); }

  pause() {
    return `<section class="screen pause"><div class="pbox cut"><span class="eyebrow">Opération en cours</span><h1>Pause</h1>
      <button class="btn primary" data-a="resume">${ic('play')}Reprendre</button><button class="btn" data-a="go:settings">${ic('sliders')}Paramètres</button>
      ${this.arm === 'quit' ? '<div class="confirm"><p>Quitter la partie ? Elle ne sera pas comptabilisée.</p><button class="btn danger" data-a="quit">Quitter</button><button class="btn" data-a="disarm">Rester</button></div>' : '<button class="btn" data-a="arm:quit">Quitter la partie</button>'}
    </div></section>`;
  }

  showResults(sum, rw) { this.res = { sum, rw }; this.go('results'); }

  results() {
    const { sum: s, rw } = this.res, p = this.p;
    const T = { 1: ['Victoire', 'ok'], 0: ['Égalité', ''], '-1': ['Défaite', 'bad'] }[s.outcome];
    const b0 = progress(rw.xpBefore), b1 = progress(p.xp), up = rw.after > rw.before;
    const kv = (l, v) => `<div class="kv"><span>${l}</span><b>${v}</b></div>`;
    return `<section class="screen results"><header class="res-head"><span class="eyebrow">${MODES[s.mode].name} · ${MAPS[s.map].name}${s.online ? ' · en ligne' : ''}</span><h1 class="res-title ${T[1]}">${T[0]}</h1><p class="res-line">${esc(s.line)}</p></header>
      <div class="scr-body"><div class="res-grid">
        <div class="res-col">
          <div class="kvs">${kv('Éliminations', s.kills)}${kv('Morts', s.deaths)}${kv('Ratio É/M', (s.kills / Math.max(1, s.deaths)).toFixed(2))}${kv('Précision', s.shots ? pct(s.hits / s.shots) + ' %' : '—')}${kv('Tirs à la tête', s.heads)}${kv('Meilleure série', s.best)}</div>
          <div class="xpbox cut"><span class="eyebrow">Expérience</span>
            <ul class="xpl">${rw.parts.map(([l, v]) => `<li><span>${l}</span><b>+${v}</b></li>`).join('')}${rw.mult !== 1 ? `<li><span>Coefficient ${s.online ? 'en ligne' : 'de difficulté'}</span><b>×${rw.mult}</b></li>` : ''}<li class="tot"><span>Total</span><b>+${rw.xp} XP</b></li></ul>
            <div class="lvl"><b>${b1.L}</b><div><div class="bar"><i class="xpfill" data-to="${b1.pct}" style="width:${up ? 0 : b0.pct}%"></i></div><small>${up ? `Niveau ${b1.L} atteint — ${rankOf(b1.L)}` : b1.need ? `${b1.cur} / ${b1.need} XP` : 'Niveau maximal'}</small></div></div>
            ${rw.unlocks.length ? `<ul class="unl">${rw.unlocks.map((u) => `<li><b>Débloqué</b><span>${u.name}</span><small>${u.kind}</small></li>`).join('')}</ul>` : ''}
            <p class="rating ${rw.dr > 0 ? 'ok' : rw.dr < 0 ? 'bad' : ''}">Classement ${rw.dr > 0 ? '+' : ''}${rw.dr} → ${p.rating} · ${tierOf(p.rating)}</p></div>
        </div>
        <div class="res-col">${boardHtml(s.board, s.teams, s.team || 0)}</div>
      </div></div>
      <footer class="scr-foot">${s.online ? '<button class="btn primary" data-a="go:online">Retour au salon</button>' : `<button class="btn primary" data-a="replay">${ic('play')}Rejouer</button>`}<button class="btn" data-a="home">Menu principal</button></footer></section>`;
  }
}
