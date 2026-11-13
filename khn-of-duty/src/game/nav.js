// Navigation des bots : grille d'occupation (cellules de 1 m) + A* à 8 voisins + lissage du chemin.
const SQ2 = Math.SQRT2;

export class NavGrid {
  constructor(hx, hz, boxes) {
    this.hx = hx; this.hz = hz; this.w = Math.ceil(hx * 2); this.d = Math.ceil(hz * 2);
    const n = this.w * this.d, pad = 0.45;
    this.blk = new Uint8Array(n);
    for (const b of boxes) {
      if (b.y1 < 0.5 || b.y0 > 1.7) continue;
      const i0 = Math.max(0, Math.floor(b.x0 - pad + hx)), i1 = Math.min(this.w - 1, Math.floor(b.x1 + pad + hx));
      const j0 = Math.max(0, Math.floor(b.z0 - pad + hz)), j1 = Math.min(this.d - 1, Math.floor(b.z1 + pad + hz));
      for (let j = j0; j <= j1; j++) for (let i = i0; i <= i1; i++) {
        const cx = i + 0.5 - hx, cz = j + 0.5 - hz;
        if (cx > b.x0 - pad && cx < b.x1 + pad && cz > b.z0 - pad && cz < b.z1 + pad) this.blk[j * this.w + i] = 1;
      }
    }
    this.g = new Float32Array(n); this.f = new Float32Array(n); this.from = new Int32Array(n);
    this.stamp = new Uint32Array(n); this.closed = new Uint32Array(n); this.gen = 0;
    this.freeCells = []; for (let k = 0; k < n; k++) if (!this.blk[k]) this.freeCells.push(k);
  }

  idx(x, z) {
    const i = Math.floor(x + this.hx), j = Math.floor(z + this.hz);
    return (i < 0 || j < 0 || i >= this.w || j >= this.d) ? -1 : j * this.w + i;
  }
  cx(k) { return (k % this.w) + 0.5 - this.hx; }
  cz(k) { return Math.floor(k / this.w) + 0.5 - this.hz; }

  /** Cellule libre la plus proche de (x,z), ou -1. */
  free(x, z) {
    const i = Math.min(this.w - 1, Math.max(0, Math.floor(x + this.hx))), j = Math.min(this.d - 1, Math.max(0, Math.floor(z + this.hz)));
    if (!this.blk[j * this.w + i]) return j * this.w + i;
    for (let r = 1; r < 8; r++) {
      let best = -1, bd = 1e9;
      for (let dj = -r; dj <= r; dj++) for (let di = -r; di <= r; di++) {
        if (Math.max(Math.abs(di), Math.abs(dj)) !== r) continue;
        const a = i + di, b = j + dj; if (a < 0 || b < 0 || a >= this.w || b >= this.d) continue;
        const k = b * this.w + a; if (this.blk[k]) continue;
        const dd = di * di + dj * dj; if (dd < bd) { bd = dd; best = k; }
      }
      if (best >= 0) return best;
    }
    return -1;
  }

  randomPoint() { const k = this.freeCells[(Math.random() * this.freeCells.length) | 0]; return [this.cx(k), this.cz(k)]; }

  lineFree(ax, az, bx, bz) {
    const dx = bx - ax, dz = bz - az, len = Math.hypot(dx, dz), n = Math.ceil(len / 0.35);
    for (let s = 1; s < n; s++) { const k = this.idx(ax + dx * s / n, az + dz * s / n); if (k < 0 || this.blk[k]) return false; }
    return true;
  }

  /** Chemin lissé [[x,z],…] de A vers B, ou null si inatteignable. */
  path(ax, az, bx, bz) {
    const s = this.free(ax, az), t = this.free(bx, bz);
    if (s < 0 || t < 0) return null;
    if (s === t) return [[this.cx(t), this.cz(t)]];
    const { w, d, blk, g, f, from, stamp, closed } = this, gen = ++this.gen;
    const ti = t % w, tj = (t / w) | 0;
    const heap = [s]; g[s] = 0; f[s] = 0; stamp[s] = gen; from[s] = -1;
    let found = false;
    while (heap.length) {
      // extraction du minimum (tas binaire)
      const cur = heap[0], last = heap.pop();
      if (heap.length) {
        let p = 0; heap[0] = last;
        for (;;) {
          let l = p * 2 + 1, r = l + 1, m = p;
          if (l < heap.length && f[heap[l]] < f[heap[m]]) m = l;
          if (r < heap.length && f[heap[r]] < f[heap[m]]) m = r;
          if (m === p) break; const tmp = heap[p]; heap[p] = heap[m]; heap[m] = tmp; p = m;
        }
      }
      if (closed[cur] === gen) continue; closed[cur] = gen;
      if (cur === t) { found = true; break; }
      const ci = cur % w, cj = (cur / w) | 0;
      for (let dj = -1; dj <= 1; dj++) for (let di = -1; di <= 1; di++) {
        if (!di && !dj) continue;
        const ni = ci + di, nj = cj + dj; if (ni < 0 || nj < 0 || ni >= w || nj >= d) continue;
        const nk = nj * w + ni; if (blk[nk] || closed[nk] === gen) continue;
        if (di && dj && (blk[cj * w + ni] || blk[nj * w + ci])) continue; // pas de coupe d'angle
        const ng = g[cur] + (di && dj ? SQ2 : 1);
        if (stamp[nk] === gen && ng >= g[nk]) continue;
        const hx = Math.abs(ni - ti), hz = Math.abs(nj - tj);
        g[nk] = ng; f[nk] = ng + Math.max(hx, hz) + (SQ2 - 1) * Math.min(hx, hz); from[nk] = cur; stamp[nk] = gen;
        let c = heap.length; heap.push(nk);
        while (c > 0) { const p = (c - 1) >> 1; if (f[heap[p]] <= f[heap[c]]) break; const tmp = heap[p]; heap[p] = heap[c]; heap[c] = tmp; c = p; }
      }
    }
    if (!found) return null;
    const cells = []; for (let k = t; k !== -1; k = from[k]) cells.push(k);
    cells.reverse();
    const pts = cells.map((k) => [this.cx(k), this.cz(k)]), out = [];
    let i = 0;
    while (i < pts.length - 1) {
      let j = Math.min(pts.length - 1, i + 14);
      while (j > i + 1 && !this.lineFree(pts[i][0], pts[i][1], pts[j][0], pts[j][1])) j--;
      out.push(pts[j]); i = j;
    }
    return out;
  }
}
