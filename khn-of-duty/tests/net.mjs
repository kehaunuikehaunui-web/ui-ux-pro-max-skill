// Test multijoueur réel : un vrai serveur + trois clients Chromium connectés en WebSocket.
// Couvre connexion, salons, synchronisation des positions, tirs arbitrés, réapparition, fin de partie,
// arrivée en cours de partie et déconnexion.   Usage : node tests/net.mjs   (après `node build.mjs`)
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const require = createRequire(import.meta.url), { chromium } = require('playwright');
const root = fileURLToPath(new URL('..', import.meta.url)), PORT = 8700 + Math.floor(Math.random() * 200), URL_ = `http://localhost:${PORT}/`;
let fail = 0;
const check = (ok, msg) => { console.log(`${ok ? '  ok  ' : 'ECHEC '} ${msg}`); if (!ok) fail++; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const srv = spawn(process.execPath, [root + 'server/server.mjs'], { env: { ...process.env, PORT: String(PORT) }, stdio: ['ignore', 'pipe', 'inherit'] });
await new Promise((ok) => srv.stdout.on('data', (d) => { if (String(d).includes('prêt')) ok(); }));
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--mute-audio'] });
const errors = [];

async function client(name) {
  const ctx = await browser.newContext({ viewport: { width: 800, height: 450 } });
  await ctx.addInitScript(() => { HTMLCanvasElement.prototype.requestPointerLock = undefined; }); // entrées déterministes (voir smoke.mjs)
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(`${name}: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error' && !/fonts\.g|Failed to load resource|ERR_/.test(m.text())) errors.push(`${name}: ${m.text()}`); });
  await page.goto(URL_); await page.waitForFunction(() => window.__khn && window.__khn.game.world);
  await page.evaluate((n) => { window.__khn.profile.name = n; window.__khn.menu.go('online'); }, name);
  await page.click('[data-a="mp-connect"]');
  await page.waitForFunction(() => window.__khn.menu.net.status === 'on', null, { timeout: 8000 });
  return page;
}
const info = (page) => page.evaluate(() => {
  const { game: g, menu } = window.__khn, net = menu.net, m = g.match, p = m && m.player;
  return { state: g.state, screen: menu.screen, shown: !menu.el.hidden, room: net.room, players: net.players.map((x) => ({ name: x.name, team: x.team })), rooms: net.rooms.length,
    m: m ? { map: g.world.def.id, mode: m.mode.id, scores: [...m.scores], over: m.over, chars: m.chars.map((c) => ({ name: c.name, team: c.team, remote: c.remote, alive: c.alive, hp: Math.round(c.hp), x: c.x, z: c.z, kills: c.kills, deaths: c.deaths })), me: { x: p.x, z: p.z, hp: Math.round(p.hp), alive: p.alive, team: p.team, kills: p.kills, deaths: p.deaths } } : null };
});
const waitFor = (page, fn, arg, timeout = 15000) => page.waitForFunction(fn, arg, { timeout }).then(() => true, () => false);
const playing = (page) => waitFor(page, () => window.__khn.game.state === 'playing' && window.__khn.game.match.player.alive);

/** Le tireur se place à 5 m de la cible (axe dégagé), vise et tire jusqu'à l'élimination arbitrée par le serveur. */
async function eliminate(shooter, victimName) {
  await sleep(1900); // protection de réapparition côté serveur
  await shooter.mouse.move(400, 225);
  const ok = await shooter.evaluate((name) => {
    const m = window.__khn.game.match, p = m.player, t = m.chars.find((c) => c.name === name);
    if (!t || !t.alive) return false;
    for (let a = 0; a < 6.28; a += 0.39) {
      const x = t.x + Math.cos(a) * 5, z = t.z + Math.sin(a) * 5, k = m.nav.idx(x, z);
      if (k >= 0 && !m.nav.blk[k] && m.col.clear(x, 1.62, z, t.x, 1.1, t.z)) { p.x = x; p.z = z; break; }
    }
    p.y = 0; p.vy = 0; p.yaw = Math.atan2(-(t.x - p.x), -(t.z - p.z)); p.pitch = -0.1; window.__t = t; return true;
  }, victimName);
  if (!ok) return false;
  await shooter.mouse.down();
  const dead = await waitFor(shooter, () => !window.__t.alive, null, 8000);
  await shooter.mouse.up();
  return dead;
}

try {
  console.log('\n[1] Connexion et salons');
  check((await fetch(URL_ + 'health').then((r) => r.json())).ok === true, 'le serveur répond et sert le jeu');
  const A = await client('Alpha'), B = await client('Bravo');
  check(true, 'deux clients connectés au serveur (WebSocket)');
  await A.evaluate(() => window.__khn.menu.net.send({ t: 'create', name: 'Salon de test', mode: 'tdm', map: 'sirocco', limit: 2, max: 4 }));
  await waitFor(A, () => !!window.__khn.menu.net.room);
  await waitFor(B, () => window.__khn.menu.net.rooms.length === 1);
  await B.waitForSelector('[data-a^="mp-join"]'); const label = await B.$eval('[data-a^="mp-join"]', (b) => b.textContent);
  check(/Salon de test/.test(label) && /1\/4/.test(label), `le salon apparaît dans la liste de Bravo : « ${label.replace(/\s+/g, ' ').trim()} »`);
  await B.click('[data-a^="mp-join"]');
  await waitFor(A, () => window.__khn.menu.net.players.length === 2); await waitFor(B, () => window.__khn.menu.net.players.length === 2);
  let a = await info(A), b = await info(B);
  check(a.players.length === 2 && b.players.length === 2 && a.players[0].team !== a.players[1].team, `salon partagé : ${a.players.map((p) => `${p.name} (équipe ${p.team})`).join(', ')}`);
  check(await B.$('[data-a="mp-start"]') === null && await A.isEnabled('[data-a="mp-start"]'), 'seul l\'hôte peut lancer la partie');

  console.log('\n[2] Partie synchronisée');
  await A.click('[data-a="mp-start"]');
  check(await playing(A) && await playing(B), 'la partie démarre chez les deux joueurs');
  a = await info(A); b = await info(B);
  check(a.m.map === 'sirocco' && b.m.map === 'sirocco' && a.m.chars.length === 2 && b.m.chars.length === 2, `même carte (${a.m.map}), ${a.m.chars.length} combattants de chaque côté`);
  check(a.m.me.team !== b.m.me.team && Math.hypot(a.m.me.x - b.m.me.x, a.m.me.z - b.m.me.z) > 20, 'apparitions dans deux camps opposés');
  await A.keyboard.down('KeyW'); await sleep(1800); await A.keyboard.up('KeyW'); await sleep(500);
  a = await info(A); b = await info(B);
  const seenA = b.m.chars.find((c) => c.name === 'Alpha'), dA = Math.hypot(seenA.x - a.m.me.x, seenA.z - a.m.me.z);
  check(dA < 1.2, `position d'Alpha vue par Bravo : écart ${dA.toFixed(2)} m`);
  await B.keyboard.down('KeyD'); await sleep(1200); await B.keyboard.up('KeyD'); await sleep(500);
  a = await info(A); b = await info(B);
  const seenB = a.m.chars.find((c) => c.name === 'Bravo'), dB = Math.hypot(seenB.x - b.m.me.x, seenB.z - b.m.me.z);
  check(dB < 1.2, `position de Bravo vue par Alpha : écart ${dB.toFixed(2)} m`);

  console.log('\n[3] Tirs arbitrés par le serveur');
  check(await eliminate(A, 'Bravo'), 'Alpha élimine Bravo (dégâts validés par le serveur)');
  await sleep(300); a = await info(A); b = await info(B);
  check(!b.m.me.alive && b.m.me.deaths === 1 && a.m.me.kills === 1, `élimination vue des deux côtés : Alpha ${a.m.me.kills} É, Bravo ${b.m.me.deaths} M`);
  check(a.m.scores[a.m.me.team] === 1 && b.m.scores[a.m.me.team] === 1, `score d'équipe identique chez les deux : ${a.m.scores} / ${b.m.scores}`);
  check(await B.isVisible('.h-respawn'), 'Bravo voit l\'écran de réapparition');
  check(await waitFor(B, () => { const p = window.__khn.game.match.player; return p.alive && p.hp === 100; }, null, 7000), 'Bravo réapparaît (décision du serveur)');
  await A.screenshot({ path: root + 'tests/out/20-mp-alpha.png' }).catch(() => {});

  console.log('\n[4] Fin de partie');
  check(await eliminate(A, 'Bravo'), 'seconde élimination (objectif = 2)');
  const endA = await waitFor(A, () => window.__khn.menu.screen === 'results' && !window.__khn.menu.el.hidden, null, 12000), endB = await waitFor(B, () => window.__khn.menu.screen === 'results' && !window.__khn.menu.el.hidden, null, 12000);
  const tA = endA ? await A.$eval('.res-title', (e) => e.textContent) : '', tB = endB ? await B.$eval('.res-title', (e) => e.textContent) : '';
  check(tA === 'Victoire' && tB === 'Défaite', `résultats : Alpha « ${tA} », Bravo « ${tB} »`);
  check(await A.evaluate(() => { const h = window.__khn.profile.history[0]; return h && h.online && h.outcome === 1 && h.kills === 2; }), 'partie en ligne enregistrée dans la carrière d\'Alpha');
  await A.click('[data-a="go:online"]'); await sleep(300); a = await info(A);
  check(a.room && a.room.state === 'lobby' && a.players.length === 2, 'retour au salon, prêt pour une nouvelle partie');

  console.log('\n[5] Mêlée générale, arrivée en cours de partie, déconnexion');
  await A.click('[data-a="mp-leave"]'); await B.click('[data-a="go:online"]'); await B.click('[data-a="mp-leave"]'); await sleep(300);
  await A.click('[data-a="mp-set:mode:ffa"]'); await A.click('[data-a="mp-set:map:boree"]'); await A.click('[data-a="mp-set:limit:10"]'); await A.click('[data-a="mp-create"]');
  await waitFor(A, () => !!window.__khn.menu.net.room); a = await info(A);
  check(a.room && a.room.mode === 'ffa' && a.room.map === 'boree' && a.room.limit === 10, `salon créé depuis l'interface : ${a.room.mode} · ${a.room.map} · objectif ${a.room.limit}`);
  await B.waitForSelector('[data-a^="mp-join"]'); await B.click('[data-a^="mp-join"]'); await waitFor(A, () => window.__khn.menu.net.players.length === 2);
  await A.click('[data-a="mp-start"]');
  check(await playing(A) && await playing(B), 'mêlée générale lancée');
  const C = await client('Charlie');
  await C.waitForSelector('[data-a^="mp-join"]'); await C.click('[data-a^="mp-join"]');
  check(await playing(C), 'Charlie rejoint la partie en cours');
  await sleep(600); a = await info(A); const c = await info(C);
  check(a.m.chars.length === 3 && c.m.chars.length === 3 && c.m.chars.filter((x) => x.remote && x.alive).length === 2, `effectif synchronisé : ${a.m.chars.length} combattants chez Alpha, ${c.m.chars.length} chez Charlie`);
  check(await eliminate(C, 'Bravo'), 'Charlie (arrivé en cours) élimine Bravo');
  await sleep(300); a = await info(A);
  check(a.m.chars.find((x) => x.name === 'Charlie').kills === 1, 'l\'élimination de Charlie est visible chez Alpha');
  await B.context().close(); await sleep(700); a = await info(A);
  check(a.m.chars.length === 2 && !a.m.chars.some((x) => x.name === 'Bravo'), 'déconnexion de Bravo : retiré de la partie chez les autres');
  check((await fetch(URL_ + 'health').then((r) => r.json())).players === 2, 'le serveur ne compte plus que deux joueurs');
  await A.keyboard.press('Escape'); await sleep(250); await A.click('[data-a="arm:quit"]'); await A.click('[data-a="quit"]'); await sleep(500);
  check((await info(A)).state === 'menu', 'Alpha quitte la partie depuis le menu pause');
  const closed = await waitFor(C, () => window.__khn.menu.screen === 'results' && !window.__khn.menu.el.hidden, null, 8000);
  check(closed && (await C.$eval('.res-title', (e) => e.textContent)) === 'Victoire', 'plus d\'adversaire : le serveur clôt la partie, Charlie l\'emporte');
  check(errors.length === 0, `aucune erreur JavaScript${errors.length ? ' → ' + errors.slice(0, 4).join(' | ') : ''}`);
} catch (e) { console.log('ECHEC  exception :', e.message.split('\n')[0]); fail++; } finally { await browser.close(); srv.kill(); }
console.log(fail ? `\n${fail} ECHEC(S)` : '\nMULTIJOUEUR : TOUT EST VERT');
process.exit(fail ? 1 : 0);
