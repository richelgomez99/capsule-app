// Orbit screen: Cluster detail.
// User tapped Summarize on a cluster. Three cited bullets stream in (each
// citation enforced at the DB layer — Principle XII: Provenance Or It Didn't
// Happen). Calendar block proposal at bottom (AppFunctions Calendar call).

function ScreenCluster() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: ORBIT.bgDeep, color: ORBIT.cream,
      display: 'flex', flexDirection: 'column',
      fontFamily: SANS,
    }}>
      {/* Top bar — back + cluster meta */}
      <div style={{ padding: '14px 18px 10px', display: 'flex', alignItems: 'center', gap: 14 }}>
        <Icon size={20}><path d="m15 6-6 6 6 6"/></Icon>
        <div style={{ flex: 1 }}>
          <MonoLabel size={9}>Research session · Apr 26</MonoLabel>
          <div style={{ fontSize: 14, fontWeight: 500, marginTop: 2, letterSpacing: '-0.01em' }}>
            Pricing
          </div>
        </div>
        <Icon size={20} stroke={ORBIT.creamDim}><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></Icon>
      </div>
      <Rule />

      <div style={{ flex: 1, overflow: 'auto' }}>

        {/* Hero — the question, paraphrased */}
        <div style={{ padding: '24px 24px 20px' }}>
          <MonoLabel style={{ marginBottom: 12 }}>// what you've been circling</MonoLabel>
          <div style={{
            fontFamily: SERIF, fontSize: 26, fontWeight: 400, lineHeight: 1.2,
            letterSpacing: '-0.018em', color: ORBIT.cream,
          }}>
            How to think about <span style={{ fontStyle: 'italic', color: ORBIT.accent }}>pre-seed valuation</span> when the comparison set is thin.
          </div>
          <div style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 10 }}>
            <SourceGlyph kind="twitter" size={20}/>
            <SourceGlyph kind="safari" size={20}/>
            <SourceGlyph kind="podcasts" size={20}/>
            <SourceGlyph kind="notes" size={20}/>
            <span style={{ fontFamily: MONO, fontSize: 9.5, color: ORBIT.creamFaint, letterSpacing: '0.12em' }}>
              · 4 captures · Sat 9:14a → 11:42a
            </span>
          </div>
        </div>

        {/* Bullets — cited */}
        <div style={{ padding: '0 24px 24px' }}>
          <div style={{
            background: ORBIT.bgPanel,
            borderRadius: 16,
            padding: '20px 18px 8px',
            border: `1px solid ${ORBIT.rule}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: ORBIT.green }}/>
              <MonoLabel color={ORBIT.green}>Three things you found</MonoLabel>
              <div style={{ marginLeft: 'auto', fontFamily: MONO, fontSize: 9, color: ORBIT.creamFaint, letterSpacing: '0.14em' }}>
                ⌁ HAIKU 4.5 · 2.1S
              </div>
            </div>

            <Bullet
              n="01"
              text="Anchor the round at the top of your peers, not the middle. The comparison set does the persuasion — the number defends itself."
              cites={[
                { src: 'twitter', label: '@patrick_oshag · 9:51a' },
                { src: 'podcasts', label: 'Acquired 14:22' },
              ]}
            />
            <Bullet
              n="02"
              text="At pre-seed, dilution past 22% signals desperation to anyone reading your cap table later. Hold the line on size before chasing the valuation."
              cites={[
                { src: 'safari', label: 'YC pricing memo · §3' },
              ]}
            />
            <Bullet
              n="03"
              text="A thin comparison set is a feature, not a bug — argue from your category, not from a single deal. The comp deck wins the room."
              cites={[
                { src: 'safari', label: 'Stripe Atlas memo · p.4' },
                { src: 'notes', label: 'Maya · pricing thread' },
              ]}
              last
            />
          </div>
        </div>

        {/* Calendar block proposal */}
        <div style={{ padding: '0 24px 22px' }}>
          <div style={{
            background: 'transparent',
            border: `1px solid ${ORBIT.accent}55`,
            borderRadius: 16,
            padding: '18px 18px 16px',
            display: 'flex', gap: 14, alignItems: 'flex-start',
          }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12,
              background: ORBIT.accentDim,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexShrink: 0,
            }}>
              <Icon size={20} stroke={ORBIT.accent}>
                <rect x="3" y="5" width="18" height="16" rx="2"/>
                <path d="M3 10h18M8 3v4M16 3v4"/>
              </Icon>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontFamily: SERIF, fontSize: 17, lineHeight: 1.25, letterSpacing: '-0.01em' }}>
                You have <span style={{ fontStyle: 'italic', color: ORBIT.accent }}>10:30 to 12:00</span> free tomorrow. Block it for pricing?
              </div>
              <div style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em', color: ORBIT.creamFaint, marginTop: 8, textTransform: 'uppercase' }}>
                Sun · Apr 27 · 90 min · Google Calendar
              </div>
              <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
                <button style={{ ...primaryBtn, flex: '0 0 auto', padding: '9px 16px', fontSize: 13 }}>Block it</button>
                <button style={{ ...ghostBtn, flex: '0 0 auto', padding: '9px 14px', fontSize: 13 }}>Move it</button>
                <button style={{ ...ghostBtn, flex: '0 0 auto', padding: '9px 14px', fontSize: 13, color: ORBIT.creamDim, borderColor: ORBIT.rule }}>Not now</button>
              </div>
            </div>
          </div>
        </div>

        {/* Footer — provenance promise */}
        <div style={{ padding: '0 24px 28px' }}>
          <div style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em', color: ORBIT.creamFaint, textTransform: 'uppercase', textAlign: 'center', lineHeight: 1.6 }}>
            every claim cites a capture you made.<br/>
            tap a citation to audit the source.
          </div>
        </div>

      </div>
    </div>
  );
}

function Bullet({ n, text, cites, last = false }) {
  return (
    <div style={{ padding: '14px 0', borderTop: '1px solid ' + ORBIT.rule, ...(last ? {} : {}) }}>
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <div style={{ fontFamily: MONO, fontSize: 11, color: ORBIT.accent, letterSpacing: '0.1em', paddingTop: 2 }}>
          {n}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, lineHeight: 1.45, color: ORBIT.cream, letterSpacing: '-0.005em' }}>
            {text}
          </div>
          <div style={{ marginTop: 10, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {cites.map((c, i) => (
              <div key={i} style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                padding: '4px 9px 4px 4px',
                background: ORBIT.bgPanelHi,
                borderRadius: 999,
                border: `1px solid ${ORBIT.rule}`,
              }}>
                <SourceGlyph kind={c.src} size={16}/>
                <span style={{ fontFamily: MONO, fontSize: 9.5, letterSpacing: '0.06em', color: ORBIT.creamDim }}>
                  {c.label}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { ScreenCluster });
