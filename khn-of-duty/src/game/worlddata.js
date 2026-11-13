// Partie non visuelle d'une carte (collisions, navigation, apparitions) : utilisable côté client, tests et serveur.
import { MAPS, expandMap, toAABBs } from '../core/maps.js';
import { Colliders } from './physics.js';
import { NavGrid } from './nav.js';

export function buildWorldData(mapId) {
  const def = MAPS[mapId] || MAPS.kestrel, ex = expandMap(def), aabbs = toAABBs(ex.boxes);
  const nav = new NavGrid(def.hx, def.hz, aabbs);
  // Un point d'apparition placé dans un obstacle est ramené sur la cellule libre la plus proche.
  const fix = (s) => { const k = nav.idx(s[0], s[1]); if (k >= 0 && !nav.blk[k]) return s; const f = nav.free(s[0], s[1]); return [nav.cx(f), nav.cz(f)]; };
  return { def, ex, aabbs, colliders: new Colliders(aabbs), nav, spawns: ex.spawns.map((l) => l.map(fix)), spawnsAll: ex.all.map(fix), zones: def.zones };
}
