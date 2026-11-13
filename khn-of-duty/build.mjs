// Build : regroupe le jeu en un seul fichier HTML autonome (dist/index.html) et produit la
// variante sans squelette de document utilisée pour la publication en Artifact (dist/artifact.html).
import { build } from 'esbuild';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('.', import.meta.url));
const css = readFileSync(root + 'src/styles.css', 'utf8');
const FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Chakra+Petch:wght@400;500;600;700&family=Russo+One&display=swap">';
const BODY = '<div id="khn" class="khn"><canvas id="stage" aria-label="Vue du jeu"></canvas></div>';

async function bundle(artifact) {
  const r = await build({ entryPoints: [root + 'src/main.js'], bundle: true, minify: true, format: 'iife', write: false, target: 'es2020', legalComments: 'none', define: { __ARTIFACT__: String(artifact) } });
  return r.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');
}

mkdirSync(root + 'dist', { recursive: true });
const js = await bundle(false), jsA = await bundle(true);
writeFileSync(root + 'dist/index.html', `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><title>KHN OF DUTY</title>${FONTS}<style>${css}</style></head><body>${BODY}<script>${js}</script></body></html>`);
writeFileSync(root + 'dist/artifact.html', `<title>KHN OF DUTY</title>\n<meta name="claude:viewport-fit-sides" content="cover">\n${FONTS}\n<style>${css}</style>\n${BODY}\n<script>${jsA}</script>\n`);
// Aperçu local de la variante Artifact, enveloppée dans un squelette équivalent à celui de la publication (tests).
writeFileSync(root + 'dist/artifact-preview.html', `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style></head><body>${readFileSync(root + 'dist/artifact.html', 'utf8')}</body></html>`);
console.log(`build ok — index.html ${(js.length / 1024).toFixed(0)} Ko de JS`);
