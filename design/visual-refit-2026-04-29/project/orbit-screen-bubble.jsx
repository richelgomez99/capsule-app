// Orbit screen: Bubble overlay (the always-on agent surface).
// User is reading Substack. Orbit's bubble pulses at the edge of the screen.
// Tap → radial menu of intent saves. The agent is present but not loud.

function ScreenBubble() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: '#fbf8f1', // Substack-cream background
      display: 'flex', flexDirection: 'column',
      fontFamily: SANS,
      position: 'relative', overflow: 'hidden',
    }}>
      {/* Background — Substack reading view */}
      <div style={{ flex: 1, padding: '24px 22px 0', color: '#1a1206', overflow: 'hidden' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}>
          <div style={{ width: 28, height: 28, borderRadius: 6, background: '#ff6719', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 700, fontSize: 15 }}>S</div>
          <div>
            <div style={{ fontSize: 12, fontWeight: 600 }}>Not Boring</div>
            <div style={{ fontSize: 10, color: '#888' }}>Packy McCormick · 8 min read</div>
          </div>
        </div>
        <div style={{ fontFamily: SERIF, fontSize: 26, fontWeight: 500, lineHeight: 1.18, letterSpacing: '-0.02em', marginBottom: 14 }}>
          The Pricing Power Anthology
        </div>
        <div style={{ fontFamily: SERIF, fontSize: 15, lineHeight: 1.5, color: '#2a1f10' }}>
          Every venture-scale company eventually faces the same question: how much can we charge before the market tells us we're wrong? The answer is almost always more than the founders think.
          <br/><br/>
          The mistake at pre-seed is to anchor on competitive median. Median is where mediocre companies cluster. The best companies stake a position one tier above their comp set and let the comparison do the persuading. <span style={{ background: 'rgba(232,176,106,0.4)', borderRadius: 2, padding: '0 2px' }}>The number defends itself.</span>
          <br/><br/>
          Three principles compound here. First, anchoring is psychological — once a buyer knows your number…
        </div>
      </div>

      {/* Selection lozenge — system context menu after long-press. Standard
          Android items only; Orbit can't inject here (no Accessibility
          Service). The save flow happens after Copy, via the bubble. */}
      <div style={{
        position: 'absolute',
        right: 22, top: 332,
        background: '#222', color: '#fff', borderRadius: 8,
        padding: '6px 12px', fontSize: 11, display: 'flex', gap: 12,
        boxShadow: '0 6px 20px rgba(0,0,0,0.25)',
      }}>
        <span style={{ opacity: 0.55 }}>Cut</span>
        <span style={{ color: '#fff', fontWeight: 600 }}>Copy</span>
        <span style={{ opacity: 0.55 }}>Share</span>
        <span style={{ opacity: 0.55 }}>Select all</span>
      </div>

      {/* Cue connecting copy → bubble — tiny dotted arc, hand-drawn feel */}
      <svg style={{ position: 'absolute', right: 14, top: 348, pointerEvents: 'none' }}
           width="180" height="380" viewBox="0 0 180 380" fill="none">
        <path d="M 28 8 C 70 80, 130 200, 132 332"
              stroke={ORBIT.accent} strokeWidth="1.5" strokeDasharray="3 5" strokeLinecap="round" opacity="0.55"/>
        <path d="M 128 326 l 4 8 l 6 -6" stroke={ORBIT.accent} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" opacity="0.7"/>
      </svg>

      {/* Just-copied toast above the bubble */}
      <div style={{
        position: 'absolute',
        right: 18, bottom: 188,
        padding: '10px 14px',
        background: ORBIT.bgDeep,
        border: `1px solid ${ORBIT.accent}66`,
        borderRadius: 12,
        boxShadow: `0 10px 28px rgba(0,0,0,0.35), 0 0 18px ${ORBIT.accent}22`,
        fontFamily: SANS, fontSize: 12,
        color: ORBIT.cream, lineHeight: 1.35,
        maxWidth: 220,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <div style={{ width: 6, height: 6, borderRadius: '50%', background: ORBIT.accent }}/>
          <span style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.16em', color: ORBIT.accent, textTransform: 'uppercase' }}>
            Caught from clipboard
          </span>
        </div>
        <div style={{ fontFamily: SERIF, fontStyle: 'italic', fontSize: 13.5, color: ORBIT.creamDim }}>
          “The number defends itself.”
        </div>
        <div style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em', color: ORBIT.creamFaint, textTransform: 'uppercase', marginTop: 6 }}>
          tap to keep · auto-clear in 12s
        </div>
      </div>

      {/* The radial menu — emerged from the bubble */}
      <div style={{
        position: 'absolute',
        right: 16, bottom: 110,
        width: 220, height: 220,
        pointerEvents: 'none',
      }}>
        {/* Soft halo */}
        <div style={{
          position: 'absolute', inset: 0,
          background: `radial-gradient(circle at 78% 78%, ${ORBIT.accent}33 0%, transparent 65%)`,
          borderRadius: '50%',
        }}/>

        {/* Five petals — each an intent */}
        <Petal label="in orbit" color="#84b8d6" angle={180}  r={88}/>
        <Petal label="remind me" color={ORBIT.accent}  angle={210} r={88}/>
        <Petal label="inspiration" color="#c8a4dc" angle={240} r={88}/>
        <Petal label="reference" color="#a4c8a4" angle={270} r={88}/>

        {/* Bubble itself — bottom-right */}
        <div style={{
          position: 'absolute', right: 0, bottom: 0,
          width: 64, height: 64, borderRadius: '50%',
          background: ORBIT.bgDeep,
          border: `2px solid ${ORBIT.accent}`,
          boxShadow: `0 12px 30px rgba(0,0,0,0.35), 0 0 24px ${ORBIT.accent}66`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{ width: 14, height: 14, borderRadius: '50%', background: ORBIT.accent, boxShadow: `0 0 14px ${ORBIT.accent}` }} />
        </div>
      </div>

      {/* Hint caption — peeking from below the menu */}
      <div style={{
        position: 'absolute', left: 22, bottom: 28,
        maxWidth: 220,
      }}>
        <MonoLabel size={9} color={'rgba(26,18,6,0.5)'}>// orbit · always available</MonoLabel>
        <div style={{ fontFamily: SERIF, fontStyle: 'italic', fontSize: 14, color: 'rgba(26,18,6,0.75)', lineHeight: 1.3, marginTop: 6 }}>
          Copy anything. Tap the bubble. Intent sealed at the moment of save.
        </div>
      </div>
    </div>
  );
}

function Petal({ label, color, angle, r }) {
  // Compute position from bubble center (bottom-right of container)
  const cx = 220 - 32;
  const cy = 220 - 32;
  const rad = (angle * Math.PI) / 180;
  const x = cx + Math.cos(rad) * r - 36;
  const y = cy + Math.sin(rad) * r - 18;
  return (
    <div style={{
      position: 'absolute', left: x, top: y,
      padding: '8px 12px',
      background: ORBIT.bgDeep,
      border: `1px solid ${color}77`,
      borderRadius: 999,
      fontFamily: SANS, fontSize: 11.5, fontWeight: 500,
      color: ORBIT.cream,
      display: 'inline-flex', alignItems: 'center', gap: 6,
      whiteSpace: 'nowrap',
      boxShadow: '0 6px 18px rgba(0,0,0,0.2)',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color }}/>
      {label}
    </div>
  );
}

Object.assign(window, { ScreenBubble });
