// Collisions et lancers de rayon analytiques (sans moteur de rendu) : rapides et déterministes.
export const EYE = 1.62;

/** Rayon contre boîte alignée. Renvoie la distance d'entrée, ou -1. */
export function rayBox(ox, oy, oz, dx, dy, dz, b, max) {
  let t0 = 0, t1 = max, a, c, t;
  if (dx > -1e-9 && dx < 1e-9) { if (ox < b.x0 || ox > b.x1) return -1; } else {
    a = (b.x0 - ox) / dx; c = (b.x1 - ox) / dx; if (a > c) { t = a; a = c; c = t; }
    if (a > t0) t0 = a; if (c < t1) t1 = c; if (t0 > t1) return -1;
  }
  if (dy > -1e-9 && dy < 1e-9) { if (oy < b.y0 || oy > b.y1) return -1; } else {
    a = (b.y0 - oy) / dy; c = (b.y1 - oy) / dy; if (a > c) { t = a; a = c; c = t; }
    if (a > t0) t0 = a; if (c < t1) t1 = c; if (t0 > t1) return -1;
  }
  if (dz > -1e-9 && dz < 1e-9) { if (oz < b.z0 || oz > b.z1) return -1; } else {
    a = (b.z0 - oz) / dz; c = (b.z1 - oz) / dz; if (a > c) { t = a; a = c; c = t; }
    if (a > t0) t0 = a; if (c < t1) t1 = c; if (t0 > t1) return -1;
  }
  return t0;
}

export function raySphere(ox, oy, oz, dx, dy, dz, cx, cy, cz, r) {
  const lx = cx - ox, ly = cy - oy, lz = cz - oz;
  const tc = lx * dx + ly * dy + lz * dz; if (tc < 0) return -1;
  const d2 = lx * lx + ly * ly + lz * lz - tc * tc; if (d2 > r * r) return -1;
  return tc - Math.sqrt(r * r - d2);
}

export class Colliders {
  constructor(boxes) { this.b = boxes; }

  /** Distance du premier obstacle (décor ou sol) le long du rayon, bornée par max. */
  ray(ox, oy, oz, dx, dy, dz, max) {
    let best = max;
    if (dy < -1e-9) { const tg = -oy / dy; if (tg >= 0 && tg < best) best = tg; }
    const B = this.b;
    for (let i = 0; i < B.length; i++) { const t = rayBox(ox, oy, oz, dx, dy, dz, B[i], best); if (t >= 0 && t < best) best = t; }
    return best;
  }

  /** Vrai si le segment A→B n'est coupé par aucun obstacle. */
  clear(ax, ay, az, bx, by, bz) {
    const dx = bx - ax, dy = by - ay, dz = bz - az, len = Math.hypot(dx, dy, dz);
    if (len < 1e-6) return true;
    return this.ray(ax, ay, az, dx / len, dy / len, dz / len, len) >= len - 1e-3;
  }

  /** Déplace une entité {x,y,z,r,h,vx,vy,vz,onGround} axe par axe avec résolution des contacts. */
  move(e, dx, dy, dz) {
    const B = this.b, r = e.r, h = e.h;
    if (dx !== 0) {
      e.x += dx;
      for (let i = 0; i < B.length; i++) {
        const b = B[i];
        if (e.x + r > b.x0 && e.x - r < b.x1 && e.z + r > b.z0 && e.z - r < b.z1 && e.y + h > b.y0 && e.y < b.y1) { e.x = dx > 0 ? b.x0 - r : b.x1 + r; e.vx = 0; }
      }
    }
    if (dz !== 0) {
      e.z += dz;
      for (let i = 0; i < B.length; i++) {
        const b = B[i];
        if (e.x + r > b.x0 && e.x - r < b.x1 && e.z + r > b.z0 && e.z - r < b.z1 && e.y + h > b.y0 && e.y < b.y1) { e.z = dz > 0 ? b.z0 - r : b.z1 + r; e.vz = 0; }
      }
    }
    e.y += dy; e.onGround = false;
    if (e.y <= 0) { e.y = 0; e.vy = 0; e.onGround = true; }
    for (let i = 0; i < B.length; i++) {
      const b = B[i];
      if (e.x + r > b.x0 && e.x - r < b.x1 && e.z + r > b.z0 && e.z - r < b.z1 && e.y + h > b.y0 && e.y < b.y1) {
        if (dy <= 0) { e.y = b.y1; e.onGround = true; } else e.y = b.y0 - h;
        e.vy = 0;
      }
    }
  }
}
