// Orbit screen: Capture sheet (bottom sheet over Twitter/X share).
// The moment of save: intent chip selection seals immutable IntentEnvelope.

function ScreenCapture() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: '#000',
      display: 'flex', flexDirection: 'column',
      fontFamily: SANS,
      position: 'relative',
    }}>
      {/* Background app — Twitter/X (dimmed). */}
      <div style={{ flex: 1, background: '#15202b', opacity: 0.32, filter: 'blur(0.5px)', display: 'flex', flexDirection: 'column' }}>
        <div style={{ height: 48, background: '#15202b', display: 'flex', alignItems: 'center', padding: '0 16px',
          color: '#fff', fontFamily: SANS, fontSize: 16, fontWeight: 700, borderBottom: '1px solid #2f3b48' }}>
          <span>𝕏</span>
          <span style={{ marginLeft: 'auto', fontSize: 13, fontWeight: 400, color: '#8899a6' }}>For you</span>
        </div>
        <div style={{ padding: 16 }}>
          <div style={{ display: 'flex', gap: 10, marginBottom: 8 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#1da1f2' }}/>
            <div>
              <div style={{ color: '#fff', fontSize: 14, fontWeight: 600 }}>Patrick OShaughnessy</div>
              <div style={{ color: '#8899a6', fontSize: 12 }}>@patrick_oshag · 2h</div>
            </div>
          </div>
          <div style={{ color: '#dfe6ec', fontSize: 14, lineHeight: 1.4 }}>
            The most underrated skill in early-stage pricing is the courage to anchor high and let the comparison do the work.
          </div>
          <div style={{ marginTop: 12, color: '#8899a6', fontSize: 12 }}>♡ 1.2k · ↻ 312</div>
        </div>
      </div>

      {/* Scrim */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'rgba(8,11,20,0.55)',
      }}/>

      {/* Bottom sheet */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        background: ORBIT.bgDeep,
        borderTopLeftRadius: 24, borderTopRightRadius: 24,
        padding: '14px 0 28px',
        boxShadow: '0 -20px 60px rgba(0,0,0,0.6)',
        borderTop: `1px solid ${ORBIT.ruleHi}`,
      }}>
        {/* Drag handle */}
        <div style={{ width: 36, height: 4, borderRadius: 2, background: ORBIT.creamFaint, margin: '0 auto 18px' }} />

        {/* Header */}
        <div style={{ padding: '0 24px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: ORBIT.accent, boxShadow: `0 0 12px ${ORBIT.accent}` }}/>
          <MonoLabel color={ORBIT.accent}>Save to Orbit</MonoLabel>
          <div style={{ marginLeft: 'auto', fontFamily: MONO, fontSize: 9, color: ORBIT.creamFaint, letterSpacing: '0.14em' }}>
            FROM 𝕏 · 9:51A
          </div>
        </div>

        {/* The captured content — quoted, preview */}
        <div style={{ padding: '0 24px 18px' }}>
          <div style={{
            background: ORBIT.bgPanel,
            border: `1px solid ${ORBIT.rule}`,
            borderRadius: 14,
            padding: '14px 16px',
            display: 'flex', gap: 12, alignItems: 'flex-start',
          }}>
            <SourceGlyph kind="twitter" size={22} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: MONO, fontSize: 9, color: ORBIT.creamFaint, letterSpacing: '0.12em', marginBottom: 4 }}>
                @PATRICK_OSHAG
              </div>
              <div style={{ fontFamily: SERIF, fontStyle: 'italic', fontSize: 16, lineHeight: 1.35, color: ORBIT.cream, letterSpacing: '-0.005em' }}>
                "The most underrated skill in early-stage pricing is the courage to anchor high…"
              </div>
            </div>
          </div>
        </div>

        {/* Intent picker */}
        <div style={{ padding: '0 24px 6px' }}>
          <MonoLabel style={{ marginBottom: 12 }}>// What did you mean?</MonoLabel>
        </div>

        <div style={{ padding: '0 24px 16px', display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <IntentChip intent="in orbit" active />
          <IntentChip intent="remind me" />
          <IntentChip intent="inspiration" />
          <IntentChip intent="reference" />
          <IntentChip intent="read later" />
        </div>

        {/* Optional layer */}
        <div style={{ padding: '6px 24px 18px' }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '12px 14px',
            background: ORBIT.bgPanel,
            borderRadius: 12,
            border: `1px solid ${ORBIT.rule}`,
          }}>
            <Icon size={16} stroke={ORBIT.creamDim}><path d="M12 5v14M5 12h14"/></Icon>
            <div style={{ fontSize: 13, color: ORBIT.creamDim, letterSpacing: '-0.005em' }}>
              Add a note <span style={{ color: ORBIT.creamFaint }}>· optional</span>
            </div>
            <div style={{ marginLeft: 'auto', fontFamily: MONO, fontSize: 9, color: ORBIT.creamFaint, letterSpacing: '0.12em' }}>
              SEALED AT SAVE
            </div>
          </div>
        </div>

        {/* CTA */}
        <div style={{ padding: '0 24px', display: 'flex', gap: 10 }}>
          <button style={{ ...primaryBtn, flex: 2 }}>Save</button>
          <button style={{ ...ghostBtn, flex: 1 }}>Cancel</button>
        </div>

        <div style={{ padding: '14px 24px 0', textAlign: 'center' }}>
          <div style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em', color: ORBIT.creamFaint, textTransform: 'uppercase' }}>
            ⌁ on-device · sealed · ≈ 1.4 s
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenCapture });
