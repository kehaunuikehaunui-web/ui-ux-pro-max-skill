// Données de jeu : arsenal, opérateurs, modes, difficulté, progression.
// Tout le contenu (noms, factions, armes) est fictif et original.
export const VERSION = '0.1.0';
export const TEAM_NAMES = ['FORCE KHN', 'LÉGION CENDRE'];

export const WEAPONS = {
  mistral: { id: 'mistral', name: 'VK-9 Mistral', cls: "Fusil d'assaut", slot: 'primary', auto: true, rpm: 660, dmg: 26, head: 1.5, mag: 30, reserve: 150, reload: 2.0, hip: 0.022, ads: 0.004, recoil: 0.011, falloff: [28, 70, 0.65], zoom: 1.3, pellets: 1, mobility: 0.95, unlock: 1, len: 0.62, snd: 'ar' },
  fennec: { id: 'fennec', name: 'Fennec-45', cls: 'Mitraillette', slot: 'primary', auto: true, rpm: 900, dmg: 19, head: 1.4, mag: 32, reserve: 192, reload: 1.6, hip: 0.018, ads: 0.008, recoil: 0.008, falloff: [14, 40, 0.5], zoom: 1.2, pellets: 1, mobility: 1.06, unlock: 2, len: 0.44, snd: 'smg' },
  kodiak: { id: 'kodiak', name: 'Kodiak 12', cls: 'Fusil à pompe', slot: 'primary', auto: false, rpm: 78, dmg: 17, head: 1.2, mag: 6, reserve: 36, reload: 2.6, hip: 0.06, ads: 0.045, recoil: 0.05, falloff: [8, 24, 0.25], zoom: 1.15, pellets: 8, mobility: 0.98, unlock: 4, len: 0.7, snd: 'sg' },
  baliste: { id: 'baliste', name: 'Baliste R7', cls: 'Fusil de précision', slot: 'primary', auto: false, rpm: 48, dmg: 92, head: 2.2, mag: 5, reserve: 30, reload: 2.9, hip: 0.07, ads: 0.0006, recoil: 0.06, falloff: [200, 300, 1], zoom: 4, pellets: 1, mobility: 0.9, unlock: 6, len: 0.86, scope: true, snd: 'sr' },
  argos: { id: 'argos', name: 'P-11 Argos', cls: 'Pistolet', slot: 'secondary', auto: false, rpm: 420, dmg: 32, head: 1.5, mag: 12, reserve: 60, reload: 1.3, hip: 0.016, ads: 0.006, recoil: 0.02, falloff: [16, 40, 0.5], zoom: 1.15, pellets: 1, mobility: 1.08, unlock: 1, len: 0.22, snd: 'pistol' },
  vesper: { id: 'vesper', name: 'Vesper MP', cls: 'Pistolet-mitrailleur', slot: 'secondary', auto: true, rpm: 840, dmg: 15, head: 1.4, mag: 20, reserve: 100, reload: 1.5, hip: 0.03, ads: 0.014, recoil: 0.012, falloff: [10, 28, 0.5], zoom: 1.15, pellets: 1, mobility: 1.08, unlock: 3, len: 0.3, snd: 'smg' },
};
export const PRIMARIES = ['mistral', 'fennec', 'kodiak', 'baliste'];
export const SECONDARIES = ['argos', 'vesper'];

export const EQUIPMENT = {
  frag: { id: 'frag', name: 'Grenade Orage', cls: 'Létal', desc: 'Fragmentation, mèche de 2,4 s. Rebondit sur les obstacles.', count: 2, unlock: 1 },
  stim: { id: 'stim', name: 'Stim Adrénaline', cls: 'Tactique', desc: 'Restaure la santé instantanément.', count: 2, unlock: 3 },
};

export const OPERATORS = [
  { id: 'nyx', name: 'NYX', role: 'Éclaireuse', bio: 'Reconnaissance avancée. Première entrée, dernière sortie.', color: 0x3b4b4d, unlock: 1 },
  { id: 'atlas', name: 'ATLAS', role: 'Appui lourd', bio: 'Tient la ligne quand tout le reste recule.', color: 0x55513c, unlock: 1 },
  { id: 'sirius', name: 'SIRIUS', role: 'Tireur désigné', bio: 'Un souffle, une cible. Rien de plus.', color: 0x303d52, unlock: 3 },
  { id: 'brasa', name: 'BRASA', role: 'Sapeuse', bio: 'Ouvre les portes que les autres contournent.', color: 0x5e3d31, unlock: 5 },
];

export const CAMOS = [
  { id: 'acier', name: 'Acier nu', color: 0x4a5258, unlock: 1 },
  { id: 'sable', name: 'Sable', color: 0xa48a5e, unlock: 2 },
  { id: 'toundra', name: 'Toundra', color: 0x8fa3ad, unlock: 5 },
  { id: 'braise', name: 'Braise', color: 0x9c4a2e, unlock: 8 },
  { id: 'signal', name: 'Signal', color: 0xd99a1c, unlock: 12 },
];

export const MODES = {
  tdm: { id: 'tdm', name: 'Match à mort par équipe', short: 'MME', desc: "Deux escouades. La première à atteindre le score l'emporte.", teams: true, limits: [20, 30, 50], limit: 30, time: 600, unit: 'éliminations' },
  ffa: { id: 'ffa', name: 'Mêlée générale', short: 'MG', desc: 'Chacun pour soi. Aucune alliance, aucun répit.', teams: false, limits: [10, 20, 30], limit: 20, time: 600, unit: 'éliminations' },
  dom: { id: 'dom', name: 'Domination', short: 'DOM', desc: 'Capturez et tenez les zones A, B et C pour marquer.', teams: true, limits: [50, 75, 100], limit: 75, time: 600, unit: 'points' },
};

export const DIFFICULTY = {
  recrue: { id: 'recrue', name: 'Recrue', react: 0.65, aim: 0.075, turn: 3.2, dmg: 0.55, xp: 0.8 },
  regulier: { id: 'regulier', name: 'Régulier', react: 0.4, aim: 0.05, turn: 5, dmg: 0.8, xp: 1 },
  veteran: { id: 'veteran', name: 'Vétéran', react: 0.22, aim: 0.03, turn: 7.5, dmg: 1, xp: 1.35 },
};

export const BOT_NAMES = ['Vautour', 'Lynx', 'Sabre', 'Cobalt', 'Fauve', 'Onyx', 'Spectre', 'Brume', 'Titan', 'Rafale', 'Givre', 'Écho', 'Havre', 'Silex', 'Orion', 'Nacre', 'Corbeau', 'Ambre', 'Zénith', 'Karst'];

// Progression
export const MAX_LEVEL = 30;
export const RANKS = [[1, 'Recrue'], [2, 'Soldat'], [4, 'Caporal'], [6, 'Sergent'], [9, 'Adjudant'], [12, 'Lieutenant'], [16, 'Capitaine'], [20, 'Commandant'], [25, 'Colonel'], [30, 'Général']];
export const TIERS = [[0, 'Bronze'], [1100, 'Argent'], [1300, 'Or'], [1600, 'Platine'], [2000, 'Élite']];
export const xpAt = (L) => 500 * (L - 1) + 75 * (L - 1) * (L - 2);
export function levelOf(xp) { let L = 1; while (L < MAX_LEVEL && xp >= xpAt(L + 1)) L++; return L; }
export function rankOf(L) { let r = RANKS[0][1]; for (const [l, n] of RANKS) if (L >= l) r = n; return r; }
export function tierOf(rating) { let t = TIERS[0][1]; for (const [r, n] of TIERS) if (rating >= r) t = n; return t; }

/** Liste des déblocages [{level, kind, name}] triée par niveau. */
export function allUnlocks() {
  const out = [];
  for (const w of Object.values(WEAPONS)) if (w.unlock > 1) out.push({ level: w.unlock, kind: w.cls, name: w.name });
  for (const e of Object.values(EQUIPMENT)) if (e.unlock > 1) out.push({ level: e.unlock, kind: 'Équipement', name: e.name });
  for (const c of CAMOS) if (c.unlock > 1) out.push({ level: c.unlock, kind: 'Camouflage', name: c.name });
  for (const o of OPERATORS) if (o.unlock > 1) out.push({ level: o.unlock, kind: 'Opérateur', name: o.name });
  return out.sort((a, b) => a.level - b.level);
}
