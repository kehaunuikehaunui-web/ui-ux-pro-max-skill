// Captures d'écran ciblées : node tests/shots.mjs <écran> [largeur hauteur]   (écrans : home, play, arsenal, operator, career, settings, online)
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdirSync } from 'node:fs';
const require = createRequire(import.meta.url), { chromium } = require('playwright');
const root = fileURLToPath(new URL('..', import.meta.url)); mkdirSync(root + 'tests/out', { recursive: true });
const [screen = 'home', w = 1280, h = 720] = process.argv.slice(2);
const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--mute-audio'] });
const page = await (await b.newContext({ viewport: { width: +w, height: +h }, hasTouch: +w < 700, isMobile: +w < 700 })).newPage();
page.on('pageerror', (e) => console.log('ERREUR', e.message));
await page.goto(pathToFileURL(root + 'dist/index.html').href); await page.waitForFunction(() => window.__khn && window.__khn.game.world);
await page.evaluate((s) => window.__khn.menu.go(s), screen); await page.waitForTimeout(900);
const f = `${root}tests/out/shot-${screen}-${w}.png`; await page.screenshot({ path: f }); console.log(f);
await b.close();
