import React from 'react';
import {AbsoluteFill, Audio, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig, Easing} from 'remotion';
import {loadFont as loadSpace} from '@remotion/google-fonts/SpaceGrotesk';
import {loadFont as loadDM} from '@remotion/google-fonts/DMSans';

const {fontFamily: HEAD} = loadSpace('normal', {weights: ['500', '700'], subsets: ['latin']});
const {fontFamily: BODY} = loadDM('normal', {weights: ['400', '500', '700'], subsets: ['latin']});

// Design system (ui-ux-pro-max: HUD / Sci-Fi FUI, "Star white + launch blue")
const C = {bg: '#0B0B10', fg: '#F8FAFC', muted: '#94A3B8', card: '#1E1E23', border: '#1E293B', accent: '#3B82F6', glow: '#60A5FA', warm: '#D97757'};
const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const ease = Easing.bezier(0.16, 1, 0.3, 1);

const useSq = () => {
  const {width, height} = useVideoConfig();
  return width === height;
};

const fadeInOut = (f: number, start: number, end: number, d = 12) =>
  interpolate(f, [start, start + d, end - d, end], [0, 1, 1, 0], clamp);

// ---------- Background: HUD grid + glow + scanline ----------
const Background: React.FC = () => {
  const f = useCurrentFrame();
  const drift = (f * 0.4) % 80;
  const scan = interpolate(f % 150, [0, 150], [-10, 110]);
  return (
    <AbsoluteFill style={{background: C.bg}}>
      <AbsoluteFill style={{
        backgroundImage: `linear-gradient(${C.border}66 1px, transparent 1px), linear-gradient(90deg, ${C.border}66 1px, transparent 1px)`,
        backgroundSize: '80px 80px', backgroundPosition: `0 ${drift}px`,
        maskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
        WebkitMaskImage: 'radial-gradient(ellipse at center, black 20%, transparent 75%)',
        opacity: interpolate(f, [0, 30], [0, 1], clamp),
      }} />
      <AbsoluteFill style={{background: `radial-gradient(circle at ${50 + Math.sin(f / 60) * 8}% ${45 + Math.cos(f / 70) * 6}%, ${C.accent}33, transparent 45%)`}} />
      <div style={{position: 'absolute', left: 0, right: 0, top: `${scan}%`, height: 2, background: `linear-gradient(90deg, transparent, ${C.glow}55, transparent)`}} />
      <Corners />
    </AbsoluteFill>
  );
};

const Corners: React.FC = () => {
  const f = useCurrentFrame();
  const len = interpolate(f, [5, 35], [0, 60], {...clamp, easing: ease});
  const s = (pos: React.CSSProperties, bt: string, bl: string): React.CSSProperties => ({position: 'absolute', width: len, height: len, ...pos, [bt]: `2px solid ${C.glow}aa`, [bl]: `2px solid ${C.glow}aa`});
  return (
    <>
      <div style={s({top: 48, left: 48}, 'borderTop', 'borderLeft')} />
      <div style={s({top: 48, right: 48}, 'borderTop', 'borderRight')} />
      <div style={s({bottom: 48, left: 48}, 'borderBottom', 'borderLeft')} />
      <div style={s({bottom: 48, right: 48}, 'borderBottom', 'borderRight')} />
      <div style={{position: 'absolute', top: 60, left: 130, fontFamily: HEAD, fontSize: 18, letterSpacing: 4, color: C.muted, opacity: interpolate(f, [20, 40], [0, 0.8], clamp)}}>
        SYS://CLAUDE <span style={{color: Math.floor(f / 15) % 2 ? C.glow : 'transparent'}}>●</span> ONLINE
      </div>
      <div style={{position: 'absolute', top: 60, right: 130, fontFamily: HEAD, fontSize: 18, letterSpacing: 4, color: C.muted, opacity: interpolate(f, [20, 40], [0, 0.8], clamp)}}>
        T+{(f / 30).toFixed(2).padStart(5, '0')}s
      </div>
    </>
  );
};

// ---------- Spark mark (generic, not an official logo) ----------
const Spark: React.FC<{size: number; rot: number}> = ({size, rot}) => (
  <svg width={size} height={size} viewBox="-50 -50 100 100" style={{transform: `rotate(${rot}deg)`, filter: `drop-shadow(0 0 24px ${C.warm}aa)`}}>
    {Array.from({length: 8}).map((_, i) => (
      <rect key={i} x={-4} y={-46} width={8} height={36} rx={4} fill={C.warm} transform={`rotate(${i * 45})`} />
    ))}
    <circle r={9} fill={C.fg} />
  </svg>
);

// ---------- Scene 1: "Bonjour, je suis Claude." ----------
const Scene1: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const sp = spring({frame: f - 8, fps, config: {damping: 14, stiffness: 90}});
  const letters = 'Claude'.split('');
  const sq = useSq();
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', opacity: fadeInOut(f, 0, 120, 10)}}>
      <div style={{display: 'flex', flexDirection: sq ? 'column' : 'row', alignItems: 'center', gap: sq ? 24 : 48}}>
        <div style={{transform: `scale(${sp})`}}><Spark size={sq ? 170 : 200} rot={f * 0.8} /></div>
        <div style={{display: 'flex', fontFamily: HEAD, fontWeight: 700, fontSize: sq ? 200 : 220, color: C.fg, letterSpacing: -6}}>
          {letters.map((l, i) => {
            const t = interpolate(f, [20 + i * 3, 38 + i * 3], [0, 1], {...clamp, easing: ease});
            return <span key={i} style={{display: 'inline-block', opacity: t, transform: `translateY(${(1 - t) * 60}px)`, filter: `blur(${(1 - t) * 10}px)`}}>{l}</span>;
          })}
        </div>
      </div>
      <div style={{marginTop: 24, fontFamily: BODY, fontSize: sq ? 36 : 40, color: C.muted, letterSpacing: 2, opacity: interpolate(f, [50, 70], [0, 1], clamp)}}>
        Votre assistant IA, par Anthropic
      </div>
    </AbsoluteFill>
  );
};

// ---------- Scene 2: Raisonner · Coder · Construire ----------
const Icon: React.FC<{d: string}> = ({d}) => (
  <svg width={72} height={72} viewBox="0 0 24 24" fill="none" stroke={C.glow} strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg>
);
const ICONS = {
  brain: 'M9.5 2A2.5 2.5 0 0 1 12 4.5v15a2.5 2.5 0 0 1-4.96.44 2.5 2.5 0 0 1-2.96-3.08 3 3 0 0 1-.34-5.58 2.5 2.5 0 0 1 1.32-4.24 2.5 2.5 0 0 1 4.44-1.54M14.5 2A2.5 2.5 0 0 0 12 4.5v15a2.5 2.5 0 0 0 4.96.44 2.5 2.5 0 0 0 2.96-3.08 3 3 0 0 0 .34-5.58 2.5 2.5 0 0 0-1.32-4.24 2.5 2.5 0 0 0-4.44-1.54',
  code: 'M16 18l6-6-6-6M8 6l-6 6 6 6',
  build: 'M12 2l9 5v10l-9 5-9-5V7l9-5zM12 22V12M21 7l-9 5-9-5',
};
const Scene2: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const items = [
    {label: 'Raisonner', icon: ICONS.brain, at: 8},
    {label: 'Coder', icon: ICONS.code, at: 36},
    {label: 'Construire', icon: ICONS.build, at: 62},
  ];
  const sq = useSq();
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', opacity: fadeInOut(f, 0, 150, 10)}}>
      <div style={{display: 'flex', flexDirection: sq ? 'column' : 'row', gap: sq ? 24 : 56}}>
        {items.map((it, i) => {
          const s = spring({frame: f - it.at, fps, config: {damping: 13, mass: 0.8}});
          const active = f > it.at && (i === items.length - 1 || f < items[i + 1].at + 10);
          return (
            <div key={it.label} style={{
              width: sq ? 640 : 440, height: sq ? 170 : 360, borderRadius: 28, background: `${C.card}cc`,
              border: `1.5px solid ${active ? C.accent : C.border}`,
              boxShadow: active ? `0 0 60px ${C.accent}55, inset 0 0 40px ${C.accent}22` : 'none',
              display: 'flex', flexDirection: sq ? 'row' : 'column', alignItems: 'center', justifyContent: sq ? 'flex-start' : 'center', gap: 32, padding: sq ? '0 56px' : 0, boxSizing: 'border-box',
              transform: `translateY(${(1 - s) * 80}px) scale(${0.9 + s * 0.1})`, opacity: s,
            }}>
              <Icon d={it.icon} />
              <div style={{fontFamily: HEAD, fontWeight: 700, fontSize: 64, color: C.fg, flex: sq ? 1 : undefined}}>{it.label}</div>
              <div style={{width: sq ? interpolate(f, [it.at + 10, it.at + 30], [0, 80], {...clamp, easing: ease}) : interpolate(f, [it.at + 10, it.at + 30], [0, 200], {...clamp, easing: ease}), height: 3, background: C.accent, borderRadius: 2}} />
            </div>
          );
        })}
      </div>
      <div style={{marginTop: sq ? 40 : 64, fontFamily: BODY, fontSize: 44, color: C.muted, opacity: interpolate(f, [90, 108], [0, 1], clamp)}}>
        … avec <span style={{color: C.fg, fontWeight: 700}}>vous</span>.
      </div>
    </AbsoluteFill>
  );
};

// ---------- Scene 3: Claude × UI UX Pro Max ----------
const Counter: React.FC<{to: number; start: number}> = ({to, start}) => {
  const f = useCurrentFrame();
  const v = Math.round(interpolate(f, [start, start + 30], [0, to], {...clamp, easing: Easing.out(Easing.cubic)}));
  return <>{v}</>;
};
const Scene3: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const head = spring({frame: f - 4, fps, config: {damping: 16}});
  const stats = [
    {n: 80, label: 'styles UI', at: 40},
    {n: 192, label: 'palettes', at: 62},
    {n: 74, label: 'typographies', at: 88},
    {n: 120, label: 'règles UX', at: 120},
  ];
  const sq = useSq();
  const swatches = ['#0B0B10', '#1E293B', '#3B82F6', '#60A5FA', '#94A3B8', '#F8FAFC', '#D97757', '#22C55E'];
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', paddingBottom: sq ? 60 : 0, opacity: fadeInOut(f, 0, 210, 10)}}>
      <div style={{display: 'flex', alignItems: 'center', gap: sq ? 18 : 28, fontFamily: HEAD, fontWeight: 700, fontSize: sq ? 64 : 96, color: C.fg, opacity: head, transform: `translateY(${(1 - head) * -40}px)`}}>
        <Spark size={sq ? 58 : 84} rot={f} />
        <span>Claude</span>
        <span style={{color: C.accent, fontWeight: 500}}>×</span>
        <span style={{background: `linear-gradient(90deg, ${C.fg}, ${C.glow})`, WebkitBackgroundClip: 'text', color: 'transparent'}}>UI UX Pro Max</span>
      </div>
      <div style={{marginTop: 20, fontFamily: BODY, fontSize: sq ? 30 : 34, color: C.muted, opacity: interpolate(f, [18, 34], [0, 1], clamp)}}>
        L'intelligence design intégrée à votre assistant
      </div>
      <div style={{display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: sq ? 24 : 32, marginTop: sq ? 36 : 72, width: sq ? 824 : undefined}}>
        {stats.map((s) => {
          const p = spring({frame: f - s.at, fps, config: {damping: 14}});
          return (
            <div key={s.label} style={{width: sq ? 400 : 340, padding: sq ? '28px 0' : '40px 0', borderRadius: 24, background: `${C.card}dd`, border: `1.5px solid ${C.border}`, textAlign: 'center', opacity: p, transform: `translateY(${(1 - p) * 60}px)`, boxShadow: `0 20px 60px #00000080`}}>
              <div style={{fontFamily: HEAD, fontWeight: 700, fontSize: sq ? 88 : 104, color: C.fg, lineHeight: 1}}><Counter to={s.n} start={s.at} /></div>
              <div style={{fontFamily: BODY, fontSize: 32, color: C.glow, marginTop: 12, letterSpacing: 1}}>{s.label}</div>
            </div>
          );
        })}
      </div>
      <div style={{display: 'flex', gap: 12, marginTop: sq ? 28 : 56}}>
        {swatches.map((c, i) => {
          const t = interpolate(f, [62 + i * 4, 76 + i * 4], [0, 1], {...clamp, easing: ease});
          return <div key={c} style={{width: 72, height: 20, borderRadius: 10, background: c, border: `1px solid ${C.border}`, opacity: t, transform: `scaleX(${t})`}} />;
        })}
      </div>
    </AbsoluteFill>
  );
};

// ---------- Scene 4: Prompt -> interface -> CTA ----------
const PROMPT = 'Crée un dashboard SaaS sombre et premium';
const Scene4: React.FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const chars = Math.floor(interpolate(f, [6, 40], [0, PROMPT.length], clamp));
  const morph = spring({frame: f - 44, fps, config: {damping: 16}});
  const cta = spring({frame: f - 72, fps, config: {damping: 14}});
  const ui = interpolate(f, [70, 80], [1, 0.0], clamp);
  const sq = useSq();
  const W = sq ? 900 : 1100;
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', opacity: interpolate(f, [0, 10], [0, 1], clamp)}}>
      {/* prompt + generated UI */}
      <div style={{position: 'absolute', display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: ui, transform: `scale(${1 - (1 - ui) * 0.08})`}}>
        <div style={{width: W, boxSizing: 'border-box', padding: '28px 36px', borderRadius: 20, background: C.card, border: `1.5px solid ${C.accent}`, boxShadow: `0 0 50px ${C.accent}44`, fontFamily: BODY, fontSize: sq ? 34 : 40, color: C.fg, display: 'flex', alignItems: 'center', gap: 20}}>
          <span style={{color: C.glow, fontFamily: HEAD}}>›</span>
          <span>{PROMPT.slice(0, chars)}<span style={{opacity: Math.floor(f / 8) % 2 ? 1 : 0, color: C.glow}}>▍</span></span>
        </div>
        <div style={{marginTop: 36, width: W, height: sq ? 380 : 400, borderRadius: 24, background: '#111118', border: `1.5px solid ${C.border}`, overflow: 'hidden', display: 'flex', opacity: morph, transform: `translateY(${(1 - morph) * 60}px)`}}>
          <div style={{width: 200, background: '#15151c', padding: 24, display: 'flex', flexDirection: 'column', gap: 18}}>
            {[0, 1, 2, 3, 4].map((i) => <div key={i} style={{height: 16, borderRadius: 8, background: i === 1 ? C.accent : C.border, width: `${90 - i * 8}%`}} />)}
          </div>
          <div style={{flex: 1, padding: 28, display: 'flex', flexDirection: 'column', gap: 22}}>
            <div style={{display: 'flex', gap: 18}}>
              {['12.4k', '98%', '+32%'].map((k, i) => (
                <div key={k} style={{flex: 1, height: 100, borderRadius: 16, background: C.card, border: `1px solid ${C.border}`, padding: 18, fontFamily: HEAD, fontWeight: 700, fontSize: 40, color: i === 2 ? '#22C55E' : C.fg}}>{k}</div>
              ))}
            </div>
            <div style={{flex: 1, borderRadius: 16, background: C.card, border: `1px solid ${C.border}`, display: 'flex', alignItems: 'flex-end', gap: 14, padding: 20}}>
              {[40, 65, 50, 80, 70, 95, 85, 110, 100, 130].map((h, i) => (
                <div key={i} style={{flex: 1, borderRadius: 6, background: `linear-gradient(180deg, ${C.glow}, ${C.accent})`, height: h * interpolate(f, [50 + i * 1.5, 64 + i * 1.5], [0, 1], {...clamp, easing: ease})}} />
              ))}
            </div>
          </div>
        </div>
      </div>
      {/* CTA end card */}
      <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: cta, transform: `scale(${0.9 + cta * 0.1})`}}>
        <Spark size={140} rot={f * 0.8} />
        <div style={{marginTop: 32, fontFamily: HEAD, fontWeight: 700, fontSize: sq ? 100 : 120, color: C.fg, letterSpacing: -3, textAlign: 'center', lineHeight: 1.05}}>
          Claude <span style={{color: C.accent, fontWeight: 500}}>×</span>{sq ? <br /> : ' '}UI UX Pro Max
        </div>
        <div style={{marginTop: sq ? 24 : 16, fontFamily: BODY, fontSize: sq ? 34 : 44, color: C.muted}}>Du prompt à l'interface. Pro, en quelques secondes.</div>
        <div style={{marginTop: 48, padding: '22px 52px', borderRadius: 999, background: C.accent, fontFamily: HEAD, fontWeight: 700, fontSize: 38, color: '#000', boxShadow: `0 0 60px ${C.accent}88`}}>
          Essayez-le maintenant
        </div>
      </div>
    </AbsoluteFill>
  );
};

// ---------- Subtitles ----------
const SUBS = [
  {from: 15, to: 72, text: 'Bonjour, je suis Claude.'},
  {from: 130, to: 227, text: 'Je raisonne, je code, et je construis avec vous.'},
  {from: 280, to: 474, text: 'Équipé de UI UX Pro Max : styles, palettes, typographies et règles UX.'},
  {from: 485, to: 547, text: "Du prompt à l'interface. Pro, en quelques secondes."},
];
const Subtitles: React.FC = () => {
  const f = useCurrentFrame();
  const sq = useSq();
  const s = SUBS.find((x) => f >= x.from && f < x.to);
  if (!s) return null;
  const o = fadeInOut(f, s.from, s.to, 6);
  return (
    <div style={{position: 'absolute', bottom: sq ? 70 : 90, left: 0, right: 0, display: 'flex', justifyContent: 'center', opacity: o}}>
      <div style={{maxWidth: sq ? 880 : undefined, textAlign: 'center', padding: '12px 28px', borderRadius: 12, background: '#000000b3', fontFamily: BODY, fontWeight: 500, fontSize: sq ? 32 : 36, color: C.fg}}>{s.text}</div>
    </div>
  );
};

// ---------- Composition ----------
export const Promo: React.FC = () => (
  <AbsoluteFill>
    <Background />
    <Sequence from={0} durationInFrames={120}><Scene1 /></Sequence>
    <Sequence from={120} durationInFrames={150}><Scene2 /></Sequence>
    <Sequence from={270} durationInFrames={210}><Scene3 /></Sequence>
    <Sequence from={480} durationInFrames={120}><Scene4 /></Sequence>
    <Subtitles />
    <Audio src={staticFile('pad.mp3')} volume={(f) => interpolate(f, [0, 20, 570, 600], [0, 0.22, 0.22, 0], clamp)} />
    <Sequence from={15}><Audio src={staticFile('l1.wav')} /></Sequence>
    <Sequence from={130}><Audio src={staticFile('l2.wav')} /></Sequence>
    <Sequence from={280}><Audio src={staticFile('l3.wav')} /></Sequence>
    <Sequence from={485}><Audio src={staticFile('l4.wav')} /></Sequence>
  </AbsoluteFill>
);
