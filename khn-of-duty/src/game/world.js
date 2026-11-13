// Construction visuelle d'une carte : ciel, sol, volumes fusionnés par matériau, éclairage.
import * as THREE from 'three';
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js';
import { buildWorldData } from './worlddata.js';

function rng(seed) { let a = seed >>> 0; return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
const TEX_SCALE = { corr: 2.6, concrete: 3, crate: 1.2, panel: 3.2, adobe: 4, ground: 6 };

/** Texture procédurale (canvas) : aucune ressource externe. */
function makeTexture(kind, color) {
  const S = kind === 'ground' ? 256 : 128, c = document.createElement('canvas'); c.width = c.height = S;
  const g = c.getContext('2d'), r = rng(kind.length * 977 + color);
  g.fillStyle = '#' + color.toString(16).padStart(6, '0'); g.fillRect(0, 0, S, S);
  const speck = (n, a) => { for (let i = 0; i < n; i++) { g.fillStyle = `rgba(${r() < 0.5 ? '0,0,0' : '255,255,255'},${r() * a})`; const s = 1 + r() * 3; g.fillRect(r() * S, r() * S, s, s); } };
  if (kind === 'corr') {
    for (let x = 0; x < S; x += 16) { g.fillStyle = 'rgba(0,0,0,.28)'; g.fillRect(x, 0, 4, S); g.fillStyle = 'rgba(255,255,255,.1)'; g.fillRect(x + 8, 0, 3, S); }
    g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, 0, S, 5); g.fillRect(0, S - 5, S, 5); speck(260, 0.16);
  } else if (kind === 'crate') {
    g.strokeStyle = 'rgba(0,0,0,.45)'; g.lineWidth = 10; g.strokeRect(5, 5, S - 10, S - 10);
    g.lineWidth = 3; for (let y = 32; y < S; y += 32) { g.beginPath(); g.moveTo(10, y); g.lineTo(S - 10, y); g.stroke(); }
    g.lineWidth = 7; g.beginPath(); g.moveTo(12, 12); g.lineTo(S - 12, S - 12); g.stroke(); speck(300, 0.14);
  } else if (kind === 'panel') {
    g.strokeStyle = 'rgba(0,0,0,.3)'; g.lineWidth = 2;
    for (let i = 0; i <= S; i += 64) { g.beginPath(); g.moveTo(i, 0); g.lineTo(i, S); g.moveTo(0, i); g.lineTo(S, i); g.stroke(); }
    g.fillStyle = 'rgba(0,0,0,.35)'; for (let y = 8; y < S; y += 64) for (let x = 8; x < S; x += 64) { g.fillRect(x, y, 3, 3); g.fillRect(x + 45, y, 3, 3); g.fillRect(x, y + 45, 3, 3); g.fillRect(x + 45, y + 45, 3, 3); }
    speck(220, 0.1);
  } else if (kind === 'adobe') {
    speck(900, 0.1); g.strokeStyle = 'rgba(60,35,10,.2)'; g.lineWidth = 1;
    for (let i = 0; i < 7; i++) { g.beginPath(); let x = r() * S, y = r() * S; g.moveTo(x, y); for (let k = 0; k < 4; k++) { x += (r() - 0.5) * 30; y += r() * 14; g.lineTo(x, y); } g.stroke(); }
  } else if (kind === 'ground') {
    speck(2600, 0.055); for (let i = 0; i < 40; i++) { g.fillStyle = `rgba(0,0,0,${r() * 0.06})`; g.beginPath(); g.arc(r() * S, r() * S, 8 + r() * 30, 0, 7); g.fill(); }
    g.strokeStyle = 'rgba(0,0,0,.16)'; g.lineWidth = 2; g.strokeRect(0, 0, S, S); // joints de dalles
  } else speck(1100, 0.12);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping; t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4;
  return t;
}

/** Boîte dont les UV suivent les dimensions réelles (texture à échelle constante). */
function boxGeo(w, h, d, ts) {
  const g = new THREE.BoxGeometry(w, h, d), uv = g.attributes.uv;
  const dims = [[d, h], [d, h], [w, d], [w, d], [w, h], [w, h]];
  for (let f = 0; f < 6; f++) for (let i = 0; i < 4; i++) { const k = f * 4 + i; uv.setXY(k, uv.getX(k) * dims[f][0] / ts, uv.getY(k) * dims[f][1] / ts); }
  return g;
}

export function buildWorld(mapId, quality) {
  const data = buildWorldData(mapId), { def, ex } = data, th = def.theme;
  const group = new THREE.Group(), trash = [];
  const keep = (x) => { trash.push(x); return x; };
  const shadows = quality !== 'low';

  // ciel : dôme à dégradé vertical
  const sky = keep(new THREE.SphereGeometry(450, 24, 14));
  const top = new THREE.Color(th.skyTop), hor = new THREE.Color(th.skyHor), col = new THREE.Color(), cols = [];
  const sp = sky.attributes.position;
  for (let i = 0; i < sp.count; i++) { const k = Math.min(1, Math.max(0, sp.getY(i) / 450 * 2.2)); col.copy(hor).lerp(top, Math.pow(k, 0.6)); cols.push(col.r, col.g, col.b); }
  sky.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
  group.add(new THREE.Mesh(sky, keep(new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.BackSide, fog: false, depthWrite: false }))));
  if (th.stars) {
    const r = rng(7), pts = [];
    for (let i = 0; i < 500; i++) { const a = r() * 6.283, e = 0.12 + r() * 1.4, R = 430; pts.push(Math.cos(a) * Math.cos(e) * R, Math.sin(e) * R, Math.sin(a) * Math.cos(e) * R); }
    const sg = keep(new THREE.BufferGeometry()); sg.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3));
    group.add(new THREE.Points(sg, keep(new THREE.PointsMaterial({ color: 0xdfeaff, size: 1.6, sizeAttenuation: false, fog: false, depthWrite: false }))));
  }
  const sunDir = new THREE.Vector3(...th.sun[2]).normalize();
  const disc = new THREE.Mesh(keep(new THREE.CircleGeometry(th.stars ? 14 : 22, 24)), keep(new THREE.MeshBasicMaterial({ color: th.sun[0], fog: false, depthWrite: false })));
  disc.position.copy(sunDir).multiplyScalar(420); disc.lookAt(0, 0, 0); group.add(disc);

  // sol
  const gt = keep(makeTexture('ground', th.ground)); gt.repeat.set((def.hx * 2 + 80) / TEX_SCALE.ground, (def.hz * 2 + 80) / TEX_SCALE.ground);
  const ground = new THREE.Mesh(keep(new THREE.PlaneGeometry(def.hx * 2 + 80, def.hz * 2 + 80)), keep(new THREE.MeshLambertMaterial({ map: gt })));
  ground.rotation.x = -Math.PI / 2; ground.receiveShadow = shadows; group.add(ground);

  // volumes fusionnés par matériau : un seul appel de rendu par matériau
  const byMat = {};
  for (const b of ex.boxes) {
    const [kind] = def.mats[b.m] || ['concrete'];
    const g = boxGeo(b.w, b.h, b.d, TEX_SCALE[kind] || 3); g.translate(b.x, b.h / 2, b.z);
    (byMat[b.m] = byMat[b.m] || []).push(g);
  }
  for (const m in byMat) {
    const [kind, color] = def.mats[m] || ['concrete', 0x888888];
    const geo = keep(mergeGeometries(byMat[m])); byMat[m].forEach((g) => g.dispose());
    const mesh = new THREE.Mesh(geo, keep(new THREE.MeshLambertMaterial({ map: keep(makeTexture(kind, color)) })));
    mesh.castShadow = mesh.receiveShadow = shadows; group.add(mesh);
  }

  // éclairage
  group.add(new THREE.HemisphereLight(th.hemi[0], th.hemi[1], th.hemi[2]));
  const sun = new THREE.DirectionalLight(th.sun[0], th.sun[1]);
  sun.position.copy(sunDir).multiplyScalar(80); group.add(sun, sun.target);
  if (shadows) {
    const e = Math.max(def.hx, def.hz) + 8, sc = sun.shadow.camera;
    sun.castShadow = true; sc.left = -e; sc.right = e; sc.top = e; sc.bottom = -e; sc.near = 1; sc.far = 200;
    sun.shadow.mapSize.set(quality === 'high' ? 2048 : 1024, quality === 'high' ? 2048 : 1024); sun.shadow.bias = -0.0006; sun.shadow.normalBias = 0.03;
  }
  // lampes (cartes de nuit) : volume émissif partout ; lumières ponctuelles réservées à la qualité haute (coût par pixel)
  for (const [x, y, z, c] of ex.lamps) {
    const lm = new THREE.Mesh(keep(new THREE.BoxGeometry(0.35, 0.18, 0.35)), keep(new THREE.MeshBasicMaterial({ color: c })));
    lm.position.set(x, y, z); group.add(lm);
    if (quality === 'high') { const pl = new THREE.PointLight(c, 60, 22, 2); pl.position.set(x, y + 0.4, z); group.add(pl); }
  }

  return {
    ...data, group, fog: new THREE.Fog(th.fog, th.fogNear, th.fogFar), background: new THREE.Color(th.skyHor),
    dispose() { for (const t of trash) t.dispose(); if (sun.shadow.map) sun.shadow.map.dispose(); },
  };
}
