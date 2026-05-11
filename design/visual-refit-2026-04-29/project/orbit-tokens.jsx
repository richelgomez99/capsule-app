// Orbit design tokens — shared with launch video aesthetic.
// Quiet Almanac: dark navy ground, cream type, amber accent, serif italic for emphasis.

const ORBIT = {
  bg: '#0e1320',
  bgDeep: '#080b14',
  bgPanel: '#141a2b',
  bgPanelHi: '#1a2236',
  cream: '#f3ead8',
  creamDim: 'rgba(243,234,216,0.55)',
  creamFaint: 'rgba(243,234,216,0.22)',
  rule: 'rgba(243,234,216,0.10)',
  ruleHi: 'rgba(243,234,216,0.18)',
  accent: '#e8b06a',
  accentDim: 'rgba(232,176,106,0.16)',
  accentInk: '#1a1206',
  green: '#7fb38a',
  red: '#d97a6c',
};

const SERIF = '"Cormorant Garamond", "EB Garamond", Georgia, serif';
const SANS = '"Inter", system-ui, -apple-system, sans-serif';
const MONO = '"JetBrains Mono", ui-monospace, monospace';

// Tiny SVG icon set — line-weight, cream stroke, no fill.
function Icon({ d, size = 18, stroke = ORBIT.cream, sw = 1.6, fill = 'none', children }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill} stroke={stroke}
         strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      {d ? <path d={d} /> : children}
    </svg>
  );
}

// App-source glyphs (tiny round chips) — reused across screens.
function SourceGlyph({ kind, size = 22 }) {
  const map = {
    safari:    { bg: '#1d6fe6', label: 'S' },
    twitter:   { bg: '#1da1f2', label: 'X' },
    podcasts:  { bg: '#7c3aff', label: '♪' },
    notes:     { bg: '#fbbf24', label: '✎' },
    instagram: { bg: 'linear-gradient(135deg,#f58529,#dd2a7b,#8134af)', label: '◎' },
    sms:       { bg: '#34c759', label: '✉' },
    chrome:    { bg: '#1a73e8', label: 'C' },
    gmail:     { bg: '#ea4335', label: 'M' },
    photos:    { bg: '#fbbf24', label: '◐' },
    youtube:   { bg: '#ff0000', label: '▶' },
    nyt:       { bg: '#000', label: 'T' },
    substack:  { bg: '#ff6719', label: 'S' },
    files:     { bg: '#34c759', label: '◫' },
  };
  const m = map[kind] || { bg: '#444', label: '?' };
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: m.bg, color: '#fff',
      fontFamily: SANS, fontSize: size * 0.5, fontWeight: 600,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>{m.label}</div>
  );
}

// Mono caption row used everywhere — small, all-caps, tracked.
function MonoLabel({ children, color = ORBIT.creamDim, size = 10, style = {} }) {
  return (
    <div style={{
      fontFamily: MONO, fontSize: size, letterSpacing: '0.18em',
      textTransform: 'uppercase', color, ...style,
    }}>{children}</div>
  );
}

// Hairline rule.
function Rule({ color = ORBIT.rule, m = '0' }) {
  return <div style={{ height: 1, background: color, margin: m }} />;
}

// Intent chip — pill with a tiny dot. Reuse across capture sheet & diary.
function IntentChip({ intent, active = false, onClick }) {
  const colors = {
    'remind me':    ORBIT.accent,
    'inspiration':  '#c8a4dc',
    'in orbit':     '#84b8d6',
    'reference':    '#a4c8a4',
    'read later':   '#dcc384',
  };
  const c = colors[intent] || ORBIT.cream;
  return (
    <div onClick={onClick} style={{
      display: 'inline-flex', alignItems: 'center', gap: 8,
      padding: '8px 14px',
      borderRadius: 999,
      border: `1px solid ${active ? c : ORBIT.ruleHi}`,
      background: active ? `${c}24` : 'transparent',
      fontFamily: SANS, fontSize: 13, fontWeight: 500,
      color: active ? c : ORBIT.cream,
      letterSpacing: '-0.01em',
      cursor: 'pointer', whiteSpace: 'nowrap',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: c }} />
      {intent}
    </div>
  );
}

// Orbit logo — a self (filled dot) with an elliptical orbital ring around it,
// and one captured object on the ring. The orbit is tilted, hand-drawn-ish
// stroke. Reads as a mark at favicon size and as an editorial wordmark beside
// type. Pass `mono` to render in a single ink color (e.g. on dark or in print).
function OrbitMark({ size = 40, ink = ORBIT.cream, accent = ORBIT.accent, mono = false }) {
  const a = mono ? ink : accent;
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" fill="none" style={{ flexShrink: 0 }}>
      {/* Orbital ellipse — tilted ~22° */}
      <g transform="rotate(-22 32 32)">
        <ellipse cx="32" cy="32" rx="26" ry="13" stroke={ink} strokeWidth="1.4" strokeOpacity={mono ? 0.4 : 0.55} fill="none"/>
      </g>
      {/* Captured object — small accent dot on the ring */}
      <circle cx="55" cy="22" r="3" fill={a}/>
      {/* Self — the cream sun in the center */}
      <circle cx="32" cy="32" r="6.5" fill={ink}/>
    </svg>
  );
}

// Wordmark — mark + "Orbit" in serif. Use in headers.
function OrbitWordmark({ height = 28, ink = ORBIT.cream, accent = ORBIT.accent, mono = false }) {
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: height * 0.35 }}>
      <OrbitMark size={height * 1.15} ink={ink} accent={accent} mono={mono}/>
      <div style={{
        fontFamily: SERIF, fontSize: height, fontWeight: 400,
        letterSpacing: '-0.01em', color: ink, lineHeight: 1,
      }}>
        Orbit<span style={{ color: mono ? ink : accent }}>.</span>
      </div>
    </div>
  );
}

Object.assign(window, { ORBIT, SERIF, SANS, MONO, Icon, SourceGlyph, MonoLabel, Rule, IntentChip, OrbitMark, OrbitWordmark });
