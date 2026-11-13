// Écran multijoueur : connexion au serveur, liste et création de salons, salon d'attente.
import { MODES, TEAM_NAMES } from '../core/config.js';
import { MAPS, MAP_IDS } from '../core/maps.js';
import { Net } from '../net/client.js';
import { esc } from './hud.js';

const MP_MODES = ['tdm', 'ffa'];
// La variante publiée en Artifact tourne dans un cadre qui interdit toute connexion réseau.
const SANDBOX = typeof __ARTIFACT__ !== 'undefined' && __ARTIFACT__;
const defaultUrl = () => (/^https?:$/.test(location.protocol) ? `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws` : '');

function ensure(menu) {
  if (menu.net) return menu.net;
  menu.mp = { mode: 'tdm', map: 'kestrel', limit: 20, max: 8 };
  const { api } = menu;
  menu.net = new Net((ev, d) => {
    const net = menu.net, inMatch = api.game.match && api.game.match.net === net;
    if (ev === 'begin') return api.deploy({ mode: d.room.mode, map: d.room.map, limit: d.room.limit, time: d.time, net, roster: d.players, you: d.you });
    if (ev === 'joined') { api.game.addModel(d); return api.hud.notice('RENFORT', `${d.name} rejoint la partie`); }
    if (ev === 'left') { api.game.removeModel(d); return api.hud.notice('DÉPART', `${d.name} a quitté la partie`); }
    if (ev === 'closed' && inMatch) { api.game.quit(); menu.go('online'); return api.toast(net.error); }
    if (ev === 'error') api.toast(d);
    if (menu.screen === 'online' && !menu.el.hidden) menu.render();
  });
  return menu.net;
}

export function onlineScreen(menu) {
  const net = ensure(menu), mp = menu.mp, s = menu.p.settings;
  const seg = (key, opts, cur) => `<div class="seg" role="group">${opts.map(([v, l]) => `<button data-a="mp-set:${key}:${v}" aria-pressed="${String(v) === String(cur)}">${l}</button>`).join('')}</div>`;
  let sub = 'Parties en ligne', body;
  if (SANDBOX) {
    body = `<div class="grp"><div class="detail cut"><span class="eyebrow">Indisponible dans cet aperçu</span><h2>Version hébergée requise</h2>
      <p>Cette page d'aperçu ne peut pas ouvrir de connexion réseau. Le multijoueur fonctionne lorsque le jeu est servi par son serveur de parties (commande <b>npm start</b>), installé sur une machine que vos joueurs peuvent joindre.</p>
      <p>L'entraînement contre les bots reste entièrement jouable ici.</p></div><div><button class="btn primary" data-a="go:play">Jouer contre les bots</button></div></div>`;
  } else if (net.status !== 'on') {
    const busy = net.status === 'connecting';
    body = `<form class="grp"><label class="field" for="set-server"><span>Adresse du serveur</span><input id="set-server" type="text" inputmode="url" autocomplete="off" spellcheck="false" placeholder="ws://adresse:8080/ws" value="${esc(s.server || defaultUrl())}"><small>Si vous jouez depuis la page servie par le serveur, gardez l'adresse proposée.</small></label>
      ${net.error ? `<p class="note bad" role="alert">${esc(net.error)}</p>` : ''}
      <div><button class="btn primary" data-a="mp-connect" ${busy ? 'disabled' : ''}>${busy ? 'Connexion…' : 'Se connecter'}</button></div></form>`;
  } else if (!net.room) {
    sub = `Connecté · ${esc(net.name || '')}`;
    const rows = net.rooms.map((r) => { const full = r.n >= r.max; return `<button class="item cut" data-a="mp-join:${r.id}" ${full ? 'aria-disabled="true"' : ''}><span><b>${esc(r.name)}</b><small>${MODES[r.mode].short} · ${MAPS[r.map].name} · premier à ${r.limit} · ${r.state === 'playing' ? 'en cours' : 'en attente'}</small></span><em class="${full ? 'lock' : 'eq'}">${r.n}/${r.max} · ${full ? 'Complet' : 'Rejoindre'}</em></button>`; }).join('');
    body = `<div class="grp"><h2>Parties ouvertes</h2>${rows ? `<div class="list">${rows}</div>` : '<p class="empty">Aucune partie ouverte pour le moment. Créez la vôtre ci-dessous.</p>'}<div><button class="btn" data-a="mp-refresh">Actualiser</button></div></div>
      <div class="grp"><h2>Créer une partie</h2>
        <div class="opts"><div><h2>Mode</h2>${seg('mode', MP_MODES.map((m) => [m, MODES[m].name]), mp.mode)}</div><div><h2>Carte</h2>${seg('map', MAP_IDS.map((m) => [m, MAPS[m].name]), mp.map)}</div>
        <div><h2>Objectif (éliminations)</h2>${seg('limit', [10, 20, 30].map((n) => [n, n]), mp.limit)}</div><div><h2>Joueurs maximum</h2>${seg('max', [2, 4, 6, 8].map((n) => [n, n]), mp.max)}</div></div>
        <div><button class="btn primary" data-a="mp-create">Créer la partie</button></div></div>
      <div><button class="btn" data-a="mp-disconnect">Se déconnecter</button></div>`;
  } else {
    const r = net.room, host = r.host === net.id, n = net.players.length;
    sub = `Salon · ${esc(r.name)}`;
    const who = net.players.map((p) => `<div class="item"><i class="sw" style="background:${r.mode === 'ffa' ? 'var(--amber)' : p.team === 0 ? 'var(--ally)' : 'var(--foe)'}"></i><span><b>${esc(p.name)}${p.id === net.id ? ' (vous)' : ''}</b><small>${r.mode === 'ffa' ? 'Chacun pour soi' : TEAM_NAMES[p.team]}${p.id === r.host ? ' · hôte' : ''}</small></span></div>`).join('');
    body = `<div class="detail cut"><span class="eyebrow">${MODES[r.mode].name}</span><h2>${MAPS[r.map].name}</h2><p>Premier à ${r.limit} éliminations · ${n}/${r.max} joueurs</p></div>
      <div class="grp"><h2>Joueurs</h2><div class="list">${who}</div></div>
      <div class="grp">${host ? `<div><button class="btn primary" data-a="mp-start" ${n < 2 ? 'disabled' : ''}>Lancer la partie</button></div>${n < 2 ? '<p class="empty">En attente d\'un second joueur…</p>' : ''}` : '<p class="empty">En attente du lancement par l\'hôte…</p>'}
      <div><button class="btn" data-a="mp-leave">Quitter le salon</button></div></div>`;
  }
  return menu.frame('Multijoueur', sub, body);
}

export function onlineAction(menu, name, x, y) {
  const net = ensure(menu), mp = menu.mp;
  switch (name) {
    case 'mp-connect': { const url = (menu.p.settings.server || defaultUrl()).trim(); if (!url) { net.error = 'Indiquez l\'adresse du serveur, par exemple ws://192.168.1.20:8080/ws'; return menu.render(); } return net.connect(url, menu.p.name); }
    case 'mp-disconnect': net.close(); net.error = ''; return menu.render();
    case 'mp-refresh': return net.send({ t: 'list' });
    case 'mp-set': mp[x] = (x === 'limit' || x === 'max') ? +y : y; return menu.render();
    case 'mp-create': return net.send({ t: 'create', name: `Salon de ${menu.p.name}`, ...mp });
    case 'mp-join': return net.send({ t: 'join', id: +x });
    case 'mp-leave': net.leave(); return menu.render();
    case 'mp-start': return net.send({ t: 'start' });
    default: return undefined;
  }
}
