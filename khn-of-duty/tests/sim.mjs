// Test de logique sans navigateur : bots seuls sur chaque carte et chaque mode, jusqu'à la fin de partie.
import { buildWorldData } from '../src/game/worlddata.js';
import { Match } from '../src/game/match.js';
import { MAP_IDS } from '../src/core/maps.js';

let fail = 0;
const check = (ok, msg) => { if (!ok) { fail++; console.log('  ECHEC:', msg); } };
for (const map of MAP_IDS) {
  const w = buildWorldData(map);
  // connectivité : chaque apparition doit pouvoir rejoindre le centre et le camp adverse
  for (const s of w.spawnsAll) check(w.nav.path(s[0], s[1], -s[0], -s[1]), `${map}: apparition ${s} isolée`);
  for (const z of w.zones) check(w.nav.free(z[0], z[1]) >= 0, `${map}: zone ${z} inaccessible`);
  for (const mode of ['tdm', 'ffa', 'dom']) {
    const ev = {}; let end = null;
    const m = new Match(w, { mode, limit: mode === 'dom' ? 20 : 8, size: 4, difficulty: 'regulier', loadout: { primary: 'mistral', secondary: 'argos', equip: 'frag' }, name: 'Test' }, (t, d) => { ev[t] = (ev[t] || 0) + 1; if (t === 'end') end = d; });
    m.player.isBot = true; m.player.ai = m.chars[1].ai ? { ...m.chars[1].ai } : null; // le joueur est piloté par l'IA pour ce test
    for (const wp of m.player.weapons) wp.reserve = 9999;
    m.begin();
    let steps = 0, moved = 0; const p0 = m.chars.map((c) => [c.x, c.z]);
    while (!m.over && steps < 60 * 900) { m.update(1 / 60); steps++; }
    m.chars.forEach((c, i) => { if (Math.hypot(c.x - p0[i][0], c.z - p0[i][1]) > 1 || c.deaths > 0) moved++; });
    const kills = m.chars.reduce((s, c) => s + c.kills, 0), shots = m.chars.reduce((s, c) => s + c.shots, 0), hits = m.chars.reduce((s, c) => s + c.hits, 0);
    console.log(`${map}/${mode}: fin=${m.over} t=${(steps / 60).toFixed(0)}s score=${m.scores} kills=${kills} tirs=${shots} précision=${(100 * hits / Math.max(1, shots)).toFixed(0)}% gagnant=${m.winner} zones=${ev.zone || 0}`);
    check(m.over && end, `${map}/${mode}: la partie ne se termine pas`);
    check(kills > 0, `${map}/${mode}: aucun kill`);
    check(moved === m.chars.length, `${map}/${mode}: ${m.chars.length - moved} bot(s) immobile(s)`);
    check(m.chars.every((c) => Number.isFinite(c.x) && Math.abs(c.x) <= w.def.hx && Math.abs(c.z) <= w.def.hz && c.y >= 0), `${map}/${mode}: position hors carte`);
    if (mode === 'dom') check((ev.zone || 0) > 0, `${map}/dom: aucune capture`);
  }
}
console.log(fail ? `\n${fail} ECHEC(S)` : '\nSIM OK');
process.exit(fail ? 1 : 0);
