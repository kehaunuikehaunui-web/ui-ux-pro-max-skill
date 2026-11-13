// Modèles 3D : armes (paramétriques), bras en vue subjective, soldats à la troisième personne.
import * as THREE from 'three';

const GEO = {};
const B = (w, h, d) => GEO[`b${w}|${h}|${d}`] || (GEO[`b${w}|${h}|${d}`] = new THREE.BoxGeometry(w, h, d));
const C = (r, l) => GEO[`c${r}|${l}`] || (GEO[`c${r}|${l}`] = new THREE.CylinderGeometry(r, r, l, 10).rotateX(Math.PI / 2));
const lam = (color) => new THREE.MeshLambertMaterial({ color });

/** Arme : origine à la poignée, canon vers -Z. userData.muzzle = position de la bouche. */
export function buildWeapon(def, camo = 0x4a5258) {
  const g = new THREE.Group(), body = lam(camo), dark = lam(0x15181a), steel = lam(0x3b4347);
  const add = (geo, mat, x, y, z, rx = 0) => { const m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); m.rotation.x = rx; g.add(m); return m; };
  const L = def.len;
  if (def.id === 'argos' || def.id === 'vesper') {
    add(B(0.034, 0.042, L), steel, 0, 0.045, -L / 2 + 0.05);
    add(B(0.03, 0.03, L * 0.8), body, 0, 0.012, -L / 2 + 0.07);
    add(B(0.03, 0.11, 0.046), dark, 0, -0.05, 0.035, 0.22);
    add(B(0.008, 0.012, 0.008), dark, 0, 0.072, -L + 0.07);
    if (def.id === 'vesper') { add(B(0.026, 0.16, 0.034), dark, 0, -0.08, -0.06); add(B(0.02, 0.02, 0.16), steel, 0, 0.03, 0.14); }
  } else {
    add(B(0.055, 0.085, L * 0.42), body, 0, 0.03, -L * 0.2);                 // boîtier
    add(B(0.05, 0.065, L * 0.36), dark, 0, 0.032, -L * 0.58);                 // garde-main
    add(C(0.011, L * 0.3), steel, 0, 0.04, -L * 0.88);                        // canon
    add(B(0.032, 0.1, 0.045), dark, 0, -0.055, 0.02, 0.25);                   // poignée
    add(B(0.045, 0.07, 0.24), body, 0, 0.012, 0.16);                          // crosse
    add(B(0.05, 0.1, 0.03), dark, 0, 0.005, 0.285);
    if (def.id === 'kodiak') { add(C(0.014, L * 0.42), steel, 0, 0.012, -L * 0.72); add(B(0.058, 0.045, 0.14), body, 0, 0.008, -L * 0.62); } else {
      const ml = def.id === 'fennec' ? 0.2 : def.id === 'baliste' ? 0.08 : 0.15;
      add(B(0.03, ml, 0.058), dark, 0, -0.04 - ml / 2, -L * 0.22, def.id === 'mistral' ? 0.2 : 0); // chargeur
    }
    if (def.scope) { add(C(0.024, 0.3), dark, 0, 0.115, -L * 0.2); add(B(0.02, 0.03, 0.04), dark, 0, 0.085, -L * 0.12); add(B(0.02, 0.03, 0.04), dark, 0, 0.085, -L * 0.28); } else { add(B(0.014, 0.026, 0.02), dark, 0, 0.085, -L * 0.05); add(B(0.01, 0.024, 0.012), dark, 0, 0.076, -L * 0.72); }
  }
  g.userData.muzzle = new THREE.Vector3(0, 0.04, -L - 0.04);
  g.userData.sightY = def.scope ? 0.115 : (def.id === 'argos' || def.id === 'vesper') ? 0.078 : 0.098;
  return g;
}

/** Arme + avant-bras pour la vue subjective. */
export function buildViewModel(def, camo, sleeve) {
  const g = new THREE.Group(), gun = buildWeapon(def, camo), sl = lam(sleeve), gl = lam(0x121516);
  g.add(gun);
  const arm = (x, y, z, ry, len) => {
    const a = new THREE.Group(); a.position.set(x, y, z); a.rotation.set(0.35, ry, 0);
    const f = new THREE.Mesh(B(0.075, 0.075, len), sl); f.position.z = len / 2 + 0.04; a.add(f);
    const h = new THREE.Mesh(B(0.07, 0.08, 0.09), gl); a.add(h); g.add(a);
  };
  arm(0.005, -0.045, 0.03, -0.12, 0.42);
  if (def.slot === 'primary') arm(-0.012, -0.012, -def.len * 0.55, 0.62, 0.46); else arm(-0.02, -0.07, 0.03, 0.5, 0.4);
  g.userData.muzzle = gun.userData.muzzle; g.userData.sightY = gun.userData.sightY;
  return g;
}

function makeTag(name, color) {
  const c = document.createElement('canvas'); c.width = 256; c.height = 64;
  const g = c.getContext('2d'); g.font = '600 30px "Chakra Petch", sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
  g.fillStyle = 'rgba(8,14,16,.55)'; const w = Math.min(250, g.measureText(name).width + 28); g.fillRect(128 - w / 2, 8, w, 38);
  g.fillStyle = color; g.fillText(name, 128, 28);
  g.beginPath(); g.moveTo(120, 48); g.lineTo(136, 48); g.lineTo(128, 60); g.fill();
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, depthTest: false, transparent: true, sizeAttenuation: false }));
  s.scale.set(0.17, 0.0425, 1); s.renderOrder = 10;
  return s;
}

/** Soldat articulé (jambes, buste, bras, arme) avec animation de marche et de chute. */
export class CharModel {
  constructor({ uniform, accent, weapon, tag }) {
    const g = this.group = new THREE.Group(); g.rotation.order = 'YXZ';
    this.mats = [lam(uniform), lam(0x1b1f21), new THREE.MeshBasicMaterial({ color: accent })];
    const [mU, mD, mA] = this.mats;
    const box = (w, h, d, m, x, y, z, parent) => { const me = new THREE.Mesh(B(w, h, d), m); me.position.set(x, y, z); parent.add(me); return me; };
    this.legL = new THREE.Group(); this.legL.position.set(-0.12, 0.86, 0); g.add(this.legL); box(0.17, 0.86, 0.2, mD, 0, -0.43, 0, this.legL);
    this.legR = new THREE.Group(); this.legR.position.set(0.12, 0.86, 0); g.add(this.legR); box(0.17, 0.86, 0.2, mD, 0, -0.43, 0, this.legR);
    const up = this.upper = new THREE.Group(); up.position.y = 0.86; g.add(up);
    box(0.46, 0.62, 0.26, mU, 0, 0.33, 0, up); box(0.5, 0.34, 0.3, mD, 0, 0.38, 0, up);
    box(0.22, 0.22, 0.22, mD, 0, 0.76, 0, up); box(0.27, 0.12, 0.29, mU, 0, 0.88, 0, up);
    box(0.28, 0.04, 0.3, mA, 0, 0.83, 0, up); box(0.52, 0.09, 0.1, mA, 0, 0.56, 0, up);
    const aim = this.aim = new THREE.Group(); aim.position.set(0, 0.52, 0); up.add(aim);
    box(0.12, 0.12, 0.5, mU, 0.21, -0.03, -0.2, aim);
    box(0.12, 0.12, 0.56, mU, -0.15, -0.03, -0.27, aim).rotation.y = -0.36;
    this.gun = buildWeapon(weapon, 0x2a2f33); this.gun.position.set(0.12, 0.0, -0.42); aim.add(this.gun);
    this.blob = new THREE.Mesh(GEO.blob || (GEO.blob = new THREE.CircleGeometry(0.5, 16).rotateX(-Math.PI / 2)), CharModel.blobMat || (CharModel.blobMat = new THREE.MeshBasicMaterial({ color: 0, transparent: true, opacity: 0.32, depthWrite: false })));
    this.blob.position.y = 0.03; g.add(this.blob);
    if (tag) { this.tag = makeTag(tag, '#3fd6c0'); this.tag.position.y = 2.2; g.add(this.tag); }
    this.walk = 0; this.dead = 0;
  }

  update(c, dt) {
    const g = this.group;
    if (!c.alive) {
      if (this.dead === 0 && this.tag) this.tag.visible = false;
      this.dead += dt; const k = Math.min(1, this.dead / 0.38);
      g.rotation.x = k * k * 1.5; g.position.y = c.y + k * 0.14; this.blob.visible = false;
      return;
    }
    g.position.set(c.x, c.y, c.z); g.rotation.y = c.yaw; g.rotation.x = 0;
    this.aim.rotation.x = c.pitch;
    this.walk += dt * c.speed * 2.1;
    const sw = Math.sin(this.walk) * Math.min(1, c.speed / 4) * 0.7;
    this.legL.rotation.x = sw; this.legR.rotation.x = -sw;
    this.upper.position.y = 0.86 + Math.abs(Math.cos(this.walk)) * Math.min(1, c.speed / 5) * 0.03;
  }

  reset() { this.dead = 0; this.blob.visible = true; if (this.tag) this.tag.visible = true; this.group.rotation.x = 0; }

  dispose() {
    this.mats.forEach((m) => m.dispose());
    this.gun.traverse((o) => o.material && o.material.dispose());
    if (this.tag) { this.tag.material.map.dispose(); this.tag.material.dispose(); }
  }
}

export function disposeModel(g) { g.traverse((o) => { if (o.material) o.material.dispose(); }); }
