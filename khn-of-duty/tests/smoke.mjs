// Test de bout en bout dans Chromium (WebGL logiciel) : menus → partie → entrées réelles → fin de partie → résultats.
// Usage : node tests/smoke.mjs   (après `node build.mjs`)
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdirSync } from 'node:fs';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = fileURLToPath(new URL('..', import.meta.url)), out = root + 'tests/out/';
mkdirSync(out, { recursive: true });
const url = pathToFileURL(root + 'dist/' + (process.env.KHN_FILE || 'index.html')).href; // KHN_FILE=artifact-preview.html pour tester la variante Artifact

let fail = 0;
const check = (ok, msg) => { console.log(`${ok ? '  ok  ' : 'ECHEC '} ${msg}`); if (!ok) fail++; };
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--mute-audio'] });

async function open(viewport, opts = {}) {
  const { noLock, ...copts } = opts; const ctx = await browser.newContext({ viewport, deviceScaleFactor: 1, ...copts });
  // noLock : simule un navigateur sans verrouillage du pointeur (les déplacements de souris synthétiques ne portent
  // pas de delta exploitable sous verrouillage) — on valide ainsi le chemin de repli, déterministe.
  if (noLock) await ctx.addInitScript(() => { HTMLCanvasElement.prototype.requestPointerLock = undefined; });
  const page = await ctx.newPage(), errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/fonts\.g|Failed to load resource|ERR_/.test(m.text())) errors.push('console: ' + m.text()); });
  await page.goto(url); await page.waitForFunction(() => window.__khn && window.__khn.game.world, null, { timeout: 20000 });
  return { ctx, page, errors };
}
const until = (page, fn, arg) => page.waitForFunction(fn, arg, { timeout: 3000 }).then(() => true, () => false);
const st = (page) => page.evaluate(() => { const { game: g } = window.__khn, m = g.match, p = m && m.player; return { state: g.state, fps: g.fps, n: m ? m.chars.length : 0, scores: m ? [...m.scores] : null, p: p ? { x: p.x, z: p.z, yaw: p.yaw, pitch: p.pitch, hp: p.hp, alive: p.alive, kills: p.kills, shots: p.shots, hits: p.hits, mag: p.weapons[p.wi].mag, wi: p.wi } : null }; });

// ───────── 1. bureau : menus ─────────
console.log('\n[1] Menus (1280×720)');
const d = await open({ width: 1280, height: 720 }, { noLock: true });
await d.page.waitForTimeout(600);
await d.page.screenshot({ path: out + '01-accueil.png' });
check((await st(d.page)).state === 'menu', 'accueil affiché, moteur en mode menu');
const navs = await d.page.$$eval('.rail .nav', (l) => l.map((b) => b.dataset.a));
for (const a of navs) {
  await d.page.click(`[data-a="${a}"]`); await d.page.waitForTimeout(250);
  const title = await d.page.$eval('.scr-head h1', (h) => h.textContent).catch(() => null);
  check(!!title, `« ${a} » ouvre l'écran ${title}`);
  await d.page.screenshot({ path: out + `02-${a.split(':')[1]}.png` });
  await d.page.click('[data-a="back"]'); await d.page.waitForTimeout(120);
}
// chaque bouton de chaque écran déclenche une action connue (aucun bouton mort)
const dead = await d.page.evaluate(() => {
  const { menu } = window.__khn, bad = [];
  for (const s of ['home', 'play', 'arsenal', 'operator', 'career', 'settings', 'online']) { menu.go(s); for (const b of menu.el.querySelectorAll('button')) if (!b.dataset.a) bad.push(`${s}: ${b.textContent.trim().slice(0, 30)}`); }
  menu.go('home'); return bad;
});
check(dead.length === 0, `aucun bouton sans action${dead.length ? ' → ' + dead.join(' | ') : ''}`);

// ───────── 2. partie : entrées réelles ─────────
console.log('\n[2] Partie contre les bots');
await d.page.click('[data-a="go:play"]'); await d.page.click('[data-a="set:mode:tdm"]'); await d.page.click('[data-a="set:map:kestrel"]'); await d.page.click('[data-a="set:size:4"]');
await d.page.click('.scr-foot [data-a="deploy"]'); await d.page.waitForTimeout(700);
let s = await st(d.page);
check(s.state === 'playing' && s.n === 8, `partie lancée : état=${s.state}, ${s.n} combattants`);
const p0 = s.p;
const simWait = (page, sec) => page.evaluate((x) => new Promise((ok) => { const m = window.__khn.game.match, t0 = m.t; const id = setInterval(() => { if (m.t - t0 >= x || m.over) { clearInterval(id); ok(); } }, 30); }), sec);
await d.page.evaluate(() => { const m = window.__khn.game.match; window.__d = m.diff; m.diff = { ...m.diff, dmg: 0 }; }); // joueur invulnérable pendant les vérifications déterministes
await d.page.keyboard.down('KeyW'); await simWait(d.page, 1.5); await d.page.keyboard.up('KeyW');
s = await st(d.page);
check(Math.hypot(s.p.x - p0.x, s.p.z - p0.z) > 2, `déplacement clavier : ${Math.hypot(s.p.x - p0.x, s.p.z - p0.z).toFixed(1)} m parcourus`);
await d.page.mouse.move(640, 360); await d.page.mouse.move(760, 330, { steps: 6 }); await d.page.waitForTimeout(150);
const s2 = await st(d.page);
check(Math.abs(s2.p.yaw - s.p.yaw) > 0.05, `visée souris : lacet ${s.p.yaw.toFixed(2)} → ${s2.p.yaw.toFixed(2)}`);
await d.page.keyboard.press('Space');
check(await until(d.page, () => window.__khn.game.match.player.y > 0.2), 'saut (Espace)');
await d.page.screenshot({ path: out + '03-jeu.png' });
// tir réel à la souris, après avoir orienté le joueur vers l'ennemi visible le plus proche
const aimed = await d.page.evaluate(() => {
  const { game } = window.__khn, m = game.match, p = m.player; let best = null, bd = 1e9;
  for (const c of m.chars) { if (!c.alive || !m.isEnemy(p, c)) continue; const dd = Math.hypot(c.x - p.x, c.z - p.z); if (dd < bd) { bd = dd; best = c; } }
  // on place le joueur à 5 m de la cible, sur un axe dégagé, pour un test déterministe du tir
  for (let a = 0; a < 6.28; a += 0.39) {
    const x = best.x + Math.cos(a) * 5, z = best.z + Math.sin(a) * 5, k = m.nav.idx(x, z);
    if (k >= 0 && !m.nav.blk[k] && m.col.clear(x, 1.62, z, best.x, 1.1, best.z)) { p.x = x; p.z = z; break; }
  }
  p.y = 0; p.vy = 0; p.yaw = Math.atan2(-(best.x - p.x), -(best.z - p.z)); p.pitch = -0.1; p.hp = 100; best.protT = 0; best.hp = 100;
  best.isBot = false; best.wx = best.wz = best.vx = best.vz = 0; best.wantFire = false; window.__t = best; return { dist: bd, name: best.name, hp: best.hp };
});
await d.page.mouse.down(); await d.page.waitForTimeout(900); await d.page.mouse.up();
s = await st(d.page);
const thp = await d.page.evaluate(() => ({ hp: window.__t.hp, alive: window.__t.alive }));
check(s.p.shots > 0, `tir à la souris : ${s.p.shots} coups, chargeur ${s.p.mag}`);
check(s.p.hits > 0 && (thp.hp < aimed.hp || !thp.alive), `impact sur ${aimed.name} : ${s.p.hits} touches, PV cible ${Math.round(thp.hp)}${thp.alive ? '' : ' (éliminé)'}`);
await d.page.keyboard.press('KeyR');
check(await until(d.page, () => window.__khn.game.match.player.reloadT > 0), 'rechargement (R)');
await d.page.keyboard.press('Digit2');
check(await until(d.page, () => window.__khn.game.match.player.wi === 1), 'changement d\'arme (2)');
const nb = await d.page.evaluate(() => window.__khn.game.match.player.equip.count);
await d.page.keyboard.press('KeyG');
check(await until(d.page, (n) => { const m = window.__khn.game.match; return m.player.equip.count === n - 1 && m.nades.length === 1; }, nb), 'grenade lancée (G)');
await d.page.keyboard.down('Tab'); await d.page.waitForTimeout(500);
check(await d.page.$eval('.h-board', (b) => !b.hidden && b.querySelectorAll('tbody tr').length === 8), 'tableau des scores (Tab) : 8 lignes');
await d.page.screenshot({ path: out + '04-scores.png' }); await d.page.keyboard.up('Tab');
await d.page.evaluate(() => { const m = window.__khn.game.match; m.diff = window.__d; window.__t.isBot = true; });
// pause / reprise
await d.page.keyboard.press('Escape'); await d.page.waitForTimeout(250);
check((await st(d.page)).state === 'paused' && await d.page.isVisible('.pbox'), 'pause (Échap)');
await d.page.screenshot({ path: out + '05-pause.png' });
await d.page.click('[data-a="resume"]'); await d.page.waitForTimeout(250);
check((await st(d.page)).state === 'playing', 'reprise de la partie');
// les bots jouent : on laisse tourner et on vérifie que le score évolue
const sc0 = (await st(d.page)).scores;
await d.page.waitForTimeout(9000);
s = await st(d.page);
check(s.scores[0] + s.scores[1] > sc0[0] + sc0[1] || s.p.kills > 0, `les bots combattent : score ${sc0} → ${s.scores}`);
console.log(`       images/s mesurées (rendu logiciel) : ${s.fps}`);
await d.page.screenshot({ path: out + '06-combat.png' });
// condition de victoire : objectif abaissé pour le test, la partie doit se clore seule
await d.page.evaluate(() => { const m = window.__khn.game.match; m.limit = Math.max(m.scores[0], m.scores[1]) + 2; });
await d.page.waitForFunction(() => window.__khn.menu.screen === 'results' && !window.__khn.menu.el.hidden, null, { timeout: 120000 });
await d.page.waitForTimeout(1200);
const res = await d.page.evaluate(() => { const { profile: p, game: g, menu } = window.__khn; return { title: document.querySelector('.res-title').textContent, line: document.querySelector('.res-line').textContent, xp: p.xp, matches: p.stats.matches, hist: p.history.length, state: g.state, rows: menu.el.querySelectorAll('.board tbody tr').length }; });
check(res.state === 'menu' && !!res.title, `fin de partie : ${res.title} (${res.line})`);
check(res.xp > 0 && res.matches === 1 && res.hist === 1 && res.rows === 8, `progression enregistrée : ${res.xp} XP, ${res.matches} partie, tableau ${res.rows} lignes`);
await d.page.screenshot({ path: out + '07-resultats.png' });
await d.page.reload(); await d.page.waitForFunction(() => window.__khn);
check(await d.page.evaluate((xp) => window.__khn.profile.xp === xp, res.xp), 'profil persistant après rechargement de la page');
check(d.errors.length === 0, `aucune erreur JavaScript${d.errors.length ? ' → ' + d.errors.slice(0, 4).join(' | ') : ''}`);
await d.ctx.close();

// ───────── 2 bis. verrouillage du pointeur ─────────
console.log('\n[2b] Verrouillage du pointeur (navigateur standard)');
const l = await open({ width: 1024, height: 640 });
await l.page.click('.quick [data-a="deploy"]'); await l.page.waitForTimeout(700);
check(await l.page.evaluate(() => window.__khn.input.locked && document.pointerLockElement === document.getElementById('stage')), 'le pointeur est capturé au déploiement');
await l.page.evaluate(() => document.exitPointerLock()); await l.page.waitForTimeout(300);
check((await st(l.page)).state === 'paused', 'perte du verrouillage (Échap navigateur) → pause automatique');
check(l.errors.length === 0, `aucune erreur JavaScript${l.errors.length ? ' → ' + l.errors.slice(0, 4).join(' | ') : ''}`);
await l.ctx.close();

// ───────── 3. téléphone : tactile ─────────
console.log('\n[3] Téléphone (390×844, tactile)');
const m = await open({ width: 390, height: 844 }, { hasTouch: true, isMobile: true });
await m.page.waitForTimeout(500);
await m.page.screenshot({ path: out + '08-mobile-accueil.png' });
const over = await m.page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
check(over <= 0, `pas de défilement horizontal (débordement ${over}px)`);
const small = await m.page.evaluate(() => [...document.querySelectorAll('.ui button')].filter((b) => { const r = b.getBoundingClientRect(); return r.height < 44; }).length);
check(small === 0, `cibles tactiles ≥ 44 px (${small} trop petites)`);
await m.page.tap('[data-a="go:play"]'); await m.page.waitForTimeout(300); await m.page.screenshot({ path: out + '09-mobile-entrainement.png' });
await m.page.tap('.scr-foot [data-a="deploy"]'); await m.page.waitForTimeout(800);
s = await st(m.page);
check(s.state === 'playing' && await m.page.isVisible('.touch'), 'partie lancée, commandes tactiles affichées');
const cdp = await m.ctx.newCDPSession(m.page);
const touch = (type, pts) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: pts });
const a0 = s.p;
await touch('touchStart', [{ x: 90, y: 700, id: 1 }]); await touch('touchMove', [{ x: 90, y: 640, id: 1 }]); await m.page.waitForTimeout(1000);
await touch('touchMove', [{ x: 90, y: 640, id: 1 }, { x: 300, y: 300, id: 2 }]); await touch('touchMove', [{ x: 90, y: 640, id: 1 }, { x: 360, y: 290, id: 2 }]); await m.page.waitForTimeout(200);
s = await st(m.page);
check(Math.hypot(s.p.x - a0.x, s.p.z - a0.z) > 1, `joystick : ${Math.hypot(s.p.x - a0.x, s.p.z - a0.z).toFixed(1)} m parcourus`);
check(Math.abs(s.p.yaw - a0.yaw) > 0.05, `visée tactile : lacet ${a0.yaw.toFixed(2)} → ${s.p.yaw.toFixed(2)}`);
await touch('touchEnd', []);
await m.page.screenshot({ path: out + '10-mobile-jeu.png' });
const fb = await m.page.$eval('.t-fire', (b) => { const r = b.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; });
const sh0 = s.p.shots;
await touch('touchStart', [{ x: fb.x, y: fb.y, id: 3 }]); await m.page.waitForTimeout(500); await touch('touchEnd', []);
check((await st(m.page)).p.shots > sh0, 'bouton de tir tactile');
await m.page.tap('.t-pause'); await m.page.waitForTimeout(250);
check((await st(m.page)).state === 'paused', 'bouton pause tactile');
await m.page.screenshot({ path: out + '11-mobile-pause.png' });
check(m.errors.length === 0, `aucune erreur JavaScript${m.errors.length ? ' → ' + m.errors.slice(0, 4).join(' | ') : ''}`);
await m.ctx.close();

await browser.close();
console.log(fail ? `\n${fail} ECHEC(S)` : '\nTOUT EST VERT');
process.exit(fail ? 1 : 0);
