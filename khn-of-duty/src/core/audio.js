// Effets sonores entièrement synthétisés (WebAudio) : aucun fichier audio externe.
const SHOT = {
  ar: { dur: 0.16, f: 1500, thump: 150, g: 0.7 }, smg: { dur: 0.11, f: 2300, thump: 190, g: 0.55 }, sg: { dur: 0.34, f: 800, thump: 90, g: 1 },
  sr: { dur: 0.5, f: 1000, thump: 70, g: 1 }, pistol: { dur: 0.13, f: 1900, thump: 170, g: 0.6 },
};

export class Sfx {
  constructor() { this.ctx = null; this.vol = 0.7; }

  /** À appeler depuis un geste utilisateur (clic / toucher) : les navigateurs bloquent l'audio avant. */
  unlock() {
    try {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
        this.ctx = new AC(); this.master = this.ctx.createGain(); this.master.gain.value = this.vol; this.master.connect(this.ctx.destination);
        const n = this.ctx.sampleRate, buf = this.ctx.createBuffer(1, n, n), d = buf.getChannelData(0);
        for (let i = 0; i < n; i++) d[i] = Math.random() * 2 - 1;
        this.noise = buf;
      } else if (this.ctx.state === 'suspended') this.ctx.resume();
    } catch { this.ctx = null; }
  }

  setVolume(v) { this.vol = v; if (this.master) this.master.gain.value = v; }

  burst(dur, freq, gain, type = 'lowpass', q = 0.8) {
    const c = this.ctx, t = c.currentTime, s = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain();
    s.buffer = this.noise; s.loop = true; f.type = type; f.frequency.setValueAtTime(freq, t); f.frequency.exponentialRampToValueAtTime(Math.max(80, freq * 0.25), t + dur); f.Q.value = q;
    g.gain.setValueAtTime(gain, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    s.connect(f).connect(g).connect(this.master); s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.02);
  }

  tone(f0, f1, dur, gain, type = 'sine', delay = 0) {
    const c = this.ctx, t = c.currentTime + delay, o = c.createOscillator(), g = c.createGain();
    o.type = type; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
    g.gain.setValueAtTime(gain, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    o.connect(g).connect(this.master); o.start(t); o.stop(t + dur + 0.02);
  }

  shot(kind, dist = 0) {
    if (!this.ctx || dist > 90) return;
    const p = SHOT[kind] || SHOT.ar, a = p.g * (dist < 1 ? 0.5 : Math.min(0.4, 3.2 / (dist + 4)));
    this.burst(p.dur, p.f * (dist > 25 ? 0.6 : 1), a); this.tone(p.thump, p.thump * 0.35, p.dur * 0.8, a * 0.9);
  }
  hit() { if (this.ctx) this.tone(1900, 1500, 0.05, 0.16, 'square'); }
  headshot() { if (this.ctx) { this.tone(2500, 2100, 0.06, 0.16, 'square'); this.tone(3300, 3000, 0.07, 0.1, 'square', 0.04); } }
  kill() { if (this.ctx) { this.tone(660, 660, 0.09, 0.2, 'triangle'); this.tone(990, 990, 0.16, 0.2, 'triangle', 0.08); } }
  hurt() { if (this.ctx) this.burst(0.14, 420, 0.35); }
  reload() { if (this.ctx) { this.burst(0.04, 3000, 0.2, 'bandpass', 4); setTimeout(() => this.ctx && this.burst(0.05, 2000, 0.25, 'bandpass', 4), 420); } }
  dry() { if (this.ctx) this.burst(0.03, 3500, 0.15, 'bandpass', 6); }
  explode(dist = 0) { if (!this.ctx) return; const a = Math.min(0.9, 9 / (dist + 9)); this.burst(0.9, 600, a); this.tone(90, 28, 0.7, a); }
  ui() { if (this.ctx) this.tone(880, 700, 0.05, 0.07, 'triangle'); }
  confirm() { if (this.ctx) { this.tone(520, 520, 0.07, 0.1, 'triangle'); this.tone(780, 780, 0.1, 0.1, 'triangle', 0.06); } }
  capture(good) { if (this.ctx) { this.tone(good ? 520 : 400, good ? 520 : 400, 0.12, 0.14, 'triangle'); this.tone(good ? 780 : 300, good ? 780 : 300, 0.2, 0.14, 'triangle', 0.12); } }
  stim() { if (this.ctx) this.tone(300, 900, 0.3, 0.12, 'sine'); }
  end(win) { if (!this.ctx) return; const n = win ? [392, 494, 587, 784] : [392, 349, 294, 220]; n.forEach((f, i) => this.tone(f, f, 0.32, 0.14, 'triangle', i * 0.16)); }
}
