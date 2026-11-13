// Cartes : données pures (aucune dépendance au rendu), partagées client / serveur.
// Chaque boîte : [x, z, largeur(x), profondeur(z), hauteur, matériau]. Le tracé est
// dupliqué par symétrie centrale pour garantir l'équité entre les deux camps.
export const MAPS = {
  kestrel: {
    id: 'kestrel', name: 'Dépôt Kestrel', sub: 'Terminal de fret · jour couvert', hx: 34, hz: 26,
    theme: { skyTop: 0x5f7f99, skyHor: 0xc5d0d4, fog: 0xb4c0c5, fogNear: 40, fogFar: 170, ground: 0x565a5d, hemi: [0xdfe9f0, 0x55585a, 2.3], sun: [0xfff1d6, 3.0, [0.5, 1, 0.35]], stars: false },
    mats: { contRed: ['corr', 0x8c3b2e], contBlue: ['corr', 0x2e5a7a], contGreen: ['corr', 0x3f6b4f], contYellow: ['corr', 0xb08a2e], concrete: ['concrete', 0x8a8d8c], crate: ['crate', 0x7a6444], wall: ['concrete', 0x6b7074] },
    boxes: [
      [-3.5, 3, 2.6, 6.1, 2.6, 'contRed'], [-9, -9, 6.1, 2.6, 2.6, 'contBlue'], [-9, 8, 2.6, 6.1, 2.6, 'contGreen'],
      [-5, 17, 6.1, 2.6, 5.2, 'contRed'], [-17, -1, 2.6, 6.1, 2.6, 'contYellow'], [-16, -15, 6.1, 2.6, 2.6, 'contGreen'],
      [-18, 14, 6.1, 2.6, 2.6, 'contBlue'], [-25, 6, 2.6, 6.1, 2.6, 'contRed'], [-25, -9, 4, 5, 3.4, 'concrete'],
      [-10, -20, 2.6, 6.1, 2.6, 'contYellow'], [-12, 1, 1.2, 1.2, 0.9, 'crate'], [-4, -6, 1.2, 1.2, 0.9, 'crate'],
      [-21, -5, 1.2, 2.4, 0.9, 'crate'], [-13, 20, 1.2, 1.2, 0.9, 'crate'], [-3, 10, 2.4, 1.2, 0.9, 'crate'],
      [-28, 18, 1.2, 1.2, 0.9, 'crate'], [-6, -16, 3, 0.6, 0.9, 'concrete'], [-22, 21, 3, 0.6, 0.9, 'concrete'],
    ],
    spawns: [[-31, -20], [-31, -13], [-30, -3], [-31, 12], [-31, 21], [-29, 1]],
    ffa: [[0, -23], [-13, -23.5], [1, 13]],
    zones: [[-20, 9], [0, 0], [20, -9]],
  },
  sirocco: {
    id: 'sirocco', name: 'Avant-poste Sirocco', sub: 'Hameau fortifié · crépuscule', hx: 36, hz: 24,
    theme: { skyTop: 0x3d5a80, skyHor: 0xf2b279, fog: 0xd9a877, fogNear: 30, fogFar: 150, ground: 0xb89868, hemi: [0xffd9a8, 0x7a5a3a, 2.0], sun: [0xffb070, 3.2, [-0.75, 0.55, 0.3]], stars: false },
    mats: { adobe: ['adobe', 0xc9a878], adobeDark: ['adobe', 0xa8875c], stone: ['concrete', 0x9a8b74], crate: ['crate', 0x7a6444], metal: ['panel', 0x55504a], wall: ['adobe', 0xb89868] },
    boxes: [
      [0, 0, 3, 3, 0.9, 'stone'], [-22, -12, 8, 7, 4, 'adobe'], [-10, 12, 7, 8, 4.5, 'adobe'], [-24, 10, 6, 6, 3.6, 'adobeDark'],
      [-30, -19, 5, 5, 3, 'adobeDark'], [-14, -2, 0.6, 9, 2.4, 'adobe'], [-6, -8, 8, 0.6, 2.4, 'adobe'], [-16, 20, 10, 0.6, 2.4, 'adobe'],
      [-8, 2, 4, 0.6, 0.9, 'stone'], [-18, 4, 0.6, 4, 0.9, 'stone'], [-3, -14, 4, 0.6, 0.9, 'stone'], [-28, -2, 3, 0.6, 0.9, 'stone'],
      [-12, -16, 1.2, 1.2, 0.9, 'crate'], [-4, 17, 1.2, 1.2, 0.9, 'crate'], [-19, -5, 1.2, 1.2, 0.9, 'crate'], [-2.5, -5, 1.2, 1.2, 0.9, 'crate'],
      [-9, -20, 4.6, 2.2, 1.7, 'metal'],
    ],
    spawns: [[-33, -10], [-33, -4], [-32, 2], [-33, 16], [-33, 21], [-30, 5]],
    ffa: [[0, -21], [-6, 21], [4, 8]],
    zones: [[-17, 12], [0, 0], [17, -12]],
  },
  boree: {
    id: 'boree', name: 'Station Borée', sub: 'Base polaire · nuit claire', hx: 30, hz: 30,
    theme: { skyTop: 0x04070e, skyHor: 0x1a2c44, fog: 0x101c2c, fogNear: 22, fogFar: 120, ground: 0x93a5b6, hemi: [0x9fc0ee, 0x2a3644, 1.9], sun: [0xbfd6ff, 1.7, [-0.4, 1, 0.3]], stars: true },
    mats: { module: ['panel', 0xc8d2d8], orange: ['panel', 0xc8642a], tank: ['corr', 0x8a959c], tower: ['panel', 0x59636b], pipe: ['concrete', 0x77828a], crate: ['crate', 0x5a6470], wall: ['concrete', 0x4c5863] },
    boxes: [
      [-12, -12, 10, 5, 3.2, 'module'], [-14, 8, 5, 10, 3.2, 'module'], [-22, -2, 4, 6, 3.2, 'orange'], [-25, 24, 6, 4, 3.2, 'module'],
      [-4, 13, 3, 3, 4, 'tank'], [-5, -22, 3, 3, 4, 'tank'], [-5, 5, 3, 3, 6, 'tower'],
      [-7.5, -1, 0.6, 6, 0.9, 'pipe'], [-18, 18, 6, 0.6, 0.9, 'pipe'], [-2, -8, 5, 0.6, 0.9, 'pipe'],
      [-20, -19, 4.4, 2.2, 1.8, 'orange'], [-9, 21, 1.2, 1.2, 0.9, 'crate'], [-24, 12, 1.2, 2.4, 0.9, 'crate'],
      [-15, -4, 1.2, 1.2, 0.9, 'crate'], [-3, 23, 2.4, 1.2, 0.9, 'crate'],
    ],
    lamps: [[-12, 3.5, -9.2, 0xffc070], [-11.2, 3.5, 8, 0xffc070], [-5, 6.3, 5, 0x9fd0ff]],
    spawns: [[-27, -24], [-27, -14], [-27, -8], [-27, 6], [-27, 17], [-25, 2]],
    ffa: [[0, -26], [10, 26], [0, 0]],
    zones: [[-19, 4], [0, 0], [19, -4]],
  },
};
export const MAP_IDS = Object.keys(MAPS);

/** Développe une carte : symétrie centrale + murs d'enceinte + points d'apparition des deux camps. */
export function expandMap(def) {
  const boxes = [];
  for (const [x, z, w, d, h, m] of def.boxes) {
    boxes.push({ x, z, w, d, h, m });
    if (x !== 0 || z !== 0) boxes.push({ x: -x, z: -z, w, d, h, m });
  }
  const { hx, hz } = def, H = 5;
  boxes.push({ x: 0, z: -hz - 0.5, w: hx * 2 + 2, d: 1, h: H, m: 'wall' }, { x: 0, z: hz + 0.5, w: hx * 2 + 2, d: 1, h: H, m: 'wall' },
    { x: -hx - 0.5, z: 0, w: 1, d: hz * 2, h: H, m: 'wall' }, { x: hx + 0.5, z: 0, w: 1, d: hz * 2, h: H, m: 'wall' });
  const t0 = def.spawns.map(([x, z]) => [x, z]), t1 = def.spawns.map(([x, z]) => [-x, -z]);
  const extra = [];
  for (const [x, z] of def.ffa || []) { extra.push([x, z]); if (x !== 0 || z !== 0) extra.push([-x, -z]); }
  const lamps = [];
  for (const [x, y, z, c] of def.lamps || []) { lamps.push([x, y, z, c]); lamps.push([-x, y, -z, c]); }
  return { boxes, spawns: [t0, t1], all: [...t0, ...t1, ...extra], lamps };
}

/** Boîtes englobantes alignées (collisions, navigation, tir). */
export function toAABBs(boxes) {
  return boxes.map((b) => ({ x0: b.x - b.w / 2, x1: b.x + b.w / 2, y0: 0, y1: b.h, z0: b.z - b.d / 2, z1: b.z + b.d / 2 }));
}
