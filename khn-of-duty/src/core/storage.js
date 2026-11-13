// Profil joueur : progression, classement, statistiques, réglages. Persisté en localStorage
// (toujours sous try/catch : le stockage peut être indisponible selon le contexte d'affichage).
import { DIFFICULTY, levelOf, allUnlocks, MAX_LEVEL, xpAt } from './config.js';

const KEY = 'khn-of-duty/v1';
const coarse = () => typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;

export const defaults = () => ({
  name: 'Opérateur', xp: 0, rating: 1000, operator: 'nyx', camo: 'acier',
  loadout: { primary: 'mistral', secondary: 'argos', equip: 'frag' },
  setup: { mode: 'tdm', map: 'kestrel', difficulty: 'regulier', size: 4, limit: 30 },
  settings: { sens: 1, fov: 75, quality: coarse() ? 'med' : 'high', volume: 0.7, invertY: false, autoFire: true, showFps: false, server: '' },
  stats: { matches: 0, wins: 0, losses: 0, draws: 0, kills: 0, deaths: 0, heads: 0, shots: 0, hits: 0, best: 0, captures: 0, time: 0 },
  history: [],
});

export function loadProfile() {
  const d = defaults();
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) || 'null');
    if (raw && typeof raw === 'object') {
      for (const k of Object.keys(d)) {
        if (raw[k] === undefined) continue;
        d[k] = (d[k] && typeof d[k] === 'object' && !Array.isArray(d[k])) ? { ...d[k], ...raw[k] } : raw[k];
      }
    }
  } catch { /* stockage indisponible : profil par défaut */ }
  return d;
}

export function saveProfile(p) { try { localStorage.setItem(KEY, JSON.stringify(p)); } catch { /* ignoré */ } }
export function resetProfile(p) { const d = defaults(); d.settings = p.settings; Object.assign(p, d); saveProfile(p); }

/**
 * Applique le résultat d'une partie au profil et renvoie le détail des gains.
 * m : { mode, map, difficulty, outcome(1|0|-1), kills, deaths, heads, shots, hits, best, captures, score, duration, line, online }
 */
export function settleMatch(p, m) {
  const mult = m.online ? 1.2 : (DIFFICULTY[m.difficulty] || DIFFICULTY.regulier).xp;
  const parts = [['Éliminations', m.kills * 100], ['Tirs à la tête', m.heads * 25], ['Captures de zone', m.captures * 150], ['Partie terminée', 200],
    [m.outcome === 1 ? 'Victoire' : m.outcome === 0 ? 'Égalité' : 'Défaite', m.outcome === 1 ? 500 : m.outcome === 0 ? 200 : 75]].filter((x) => x[1] > 0);
  const xp = Math.round(parts.reduce((s, x) => s + x[1], 0) * mult);
  const before = levelOf(p.xp), xpBefore = p.xp;
  p.xp = Math.min(p.xp + xp, xpAt(MAX_LEVEL));
  const after = levelOf(p.xp);
  const unlocks = allUnlocks().filter((u) => u.level > before && u.level <= after);
  const kd = m.kills / Math.max(1, m.deaths);
  const dr = m.outcome === 1 ? Math.round(18 * mult + Math.min(2, Math.max(-0.5, kd - 1)) * 6) : m.outcome === 0 ? 0 : -Math.round(Math.max(6, 16 / mult - Math.min(1.5, kd) * 4));
  p.rating = Math.max(0, p.rating + dr);
  const s = p.stats;
  s.matches++; if (m.outcome === 1) s.wins++; else if (m.outcome === 0) s.draws++; else s.losses++;
  s.kills += m.kills; s.deaths += m.deaths; s.heads += m.heads; s.shots += m.shots; s.hits += m.hits; s.captures += m.captures;
  s.best = Math.max(s.best, m.best); s.time += Math.round(m.duration);
  p.history.unshift({ at: Date.now(), mode: m.mode, map: m.map, outcome: m.outcome, kills: m.kills, deaths: m.deaths, line: m.line, xp, online: !!m.online });
  p.history.length = Math.min(p.history.length, 12);
  saveProfile(p);
  return { parts, mult, xp, xpBefore, before, after, unlocks, dr };
}
