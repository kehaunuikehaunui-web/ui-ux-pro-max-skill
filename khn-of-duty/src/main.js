// Point d'entrée : assemble profil, audio, entrées, HUD, moteur et menus.
import { loadProfile, settleMatch } from './core/storage.js';
import { Sfx } from './core/audio.js';
import { Input } from './core/input.js';
import { Hud } from './ui/hud.js';
import { Menu } from './ui/menu.js';
import { Game } from './game/engine.js';

const root = document.getElementById('khn'), canvas = document.getElementById('stage');
const profile = loadProfile(), sfx = new Sfx(); sfx.setVolume(profile.settings.volume);
const hud = new Hud(root);
const toastEl = document.createElement('div'); toastEl.className = 'toast'; toastEl.setAttribute('role', 'status'); root.appendChild(toastEl);
let toastT = 0;
const toast = (msg) => { toastEl.textContent = msg; toastEl.classList.add('on'); clearTimeout(toastT); toastT = setTimeout(() => toastEl.classList.remove('on'), 2600); };

let game = null, menu = null;
const input = new Input(root, canvas, {
  onPause: () => { if (game && game.pause()) menu.go('pause'); },
  onBack: () => menu && menu.back(),
  onTouchMode: () => hud.setTouch(true),
});
input.bindTouch(hud.touch);

try {
  game = new Game(root, canvas, profile, sfx, input, hud, {
    onPause: () => { if (game.pause()) menu.go('pause'); },
    onMatchEnd: (sum) => { const rw = settleMatch(profile, sum); menu.showResults(sum, rw); },
  });
} catch (e) {
  console.error(e);
  root.insertAdjacentHTML('beforeend', '<div class="fatal"><h1>Rendu 3D indisponible</h1><p>Ce navigateur ne peut pas initialiser WebGL. Activez l\'accélération matérielle ou essayez un autre navigateur.</p></div>');
}

if (game) {
  menu = new Menu(root, profile, {
    click: () => { sfx.unlock(); sfx.ui(); },
    toast,
    onScreen: (screen, m) => {
      game.loadWorld(profile.setup.map);
      if (screen === 'arsenal') { const t = m.tab; game.setShowcase('weapon', t === 'secondary' ? profile.loadout.secondary : profile.loadout.primary); } else if (screen === 'operator') game.setShowcase('operator', profile.operator); else game.setShowcase(null);
    },
    deploy: (extra) => { sfx.unlock(); sfx.confirm(); menu.hide(); game.start({ ...profile.setup, ...(extra || {}) }); },
    resume: () => game.resume(),
    quit: () => { if (game.match && game.match.net) game.match.net.leave(); game.quit(); },
    applySettings: (reload) => { sfx.setVolume(profile.settings.volume); game.resize(); if (reload && game.state === 'menu') game.loadWorld(profile.setup.map); },
    game, sfx, hud,
  });
  window.__khn = { game, menu, profile, input, hud };
}
