// Orbit screen: Diary (home).
// Chronological feed of captures grouped by mono date headers, today's
// cluster card prominently at top. Editorial-paged layout, not a grid feed.

function ScreenDiary() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: ORBIT.bgDeep, color: ORBIT.cream,
      display: 'flex', flexDirection: 'column',
      fontFamily: SANS,
    }}>
      {/* Header — wordmark, no chrome. */}
      <div style={{ padding: '20px 24px 14px', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
        <div>
          <MonoLabel size={9} style={{ marginBottom: 6 }}>Saturday · Apr 26</MonoLabel>
          <OrbitWordmark height={26} />
        </div>
        <div style={{ display: 'flex', gap: 14, alignItems: 'center', paddingBottom: 2 }}>
          <Icon size={20}><circle cx="11" cy="11" r="6"/><path d="m20 20-3.5-3.5"/></Icon>
          <Icon size={20}><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></Icon>
        </div>
      </div>

      <Rule />

      <div style={{ flex: 1, overflow: 'auto', padding: '0 0 32px' }}>

        {/* Cluster card — the wedge moment. */}
        <div style={{ padding: '20px 20px 8px' }}>
          <MonoLabel style={{ marginBottom: 10, marginLeft: 4 }}>// Orbit noticed</MonoLabel>
          <div style={{
            background: ORBIT.bgPanel,
            border: `1px solid ${ORBIT.accent}55`,
            borderRadius: 18,
            padding: 20,
            position: 'relative',
            overflow: 'hidden',
          }}>
            {/* Soft amber wash top-right */}
            <div style={{
              position: 'absolute', top: -40, right: -40, width: 160, height: 160,
              background: `radial-gradient(circle, ${ORBIT.accent}26 0%, transparent 70%)`,
              pointerEvents: 'none',
            }}/>

            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: ORBIT.accent, boxShadow: `0 0 10px ${ORBIT.accent}` }}/>
              <MonoLabel color={ORBIT.accent}>Research session · 4 captures</MonoLabel>
            </div>

            <div style={{
              fontFamily: SERIF, fontSize: 22, fontWeight: 400, lineHeight: 1.25,
              letterSpacing: '-0.015em', color: ORBIT.cream,
            }}>
              You came back to <span style={{ fontStyle: 'italic', color: ORBIT.accent }}>pricing</span> four times this weekend — across Twitter, Safari, and a podcast on pre-seed valuations.
            </div>

            <div style={{ marginTop: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
              <SourceGlyph kind="twitter" size={20}/>
              <SourceGlyph kind="safari" size={20}/>
              <SourceGlyph kind="podcasts" size={20}/>
              <SourceGlyph kind="notes" size={20}/>
              <div style={{ marginLeft: 'auto', fontFamily: MONO, fontSize: 10, color: ORBIT.creamFaint, letterSpacing: '0.12em' }}>
                Sat 9:14a → 11:42a
              </div>
            </div>

            <div style={{ marginTop: 18, display: 'flex', gap: 10 }}>
              <button style={primaryBtn}>Summarize</button>
              <button style={ghostBtn}>Open all</button>
              <button style={iconBtn}><Icon size={16}><path d="M6 6l12 12M6 18 18 6"/></Icon></button>
            </div>
          </div>
        </div>

        {/* Day section — Today */}
        <DiaryDay
          label="Today · Saturday"
          items={[
            { time: '11:42a', src: 'safari', intent: 'in orbit', preview: 'Stripe Atlas — pre-seed valuation memo (excerpt)', note: '"Anchor high. Let the comparison do the work."' },
            { time: '11:08a', src: 'podcasts', intent: 'reference', preview: 'Acquired · Pricing power & moat-building', note: 'clip · 14:22–17:08' },
            { time: '9:51a', src: 'twitter', intent: 'in orbit', preview: '@patrick_oshag · the most underrated skill in early-stage pricing…', note: '' },
            { time: '9:14a', src: 'safari', intent: 'remind me', preview: 'YC — pricing for the fundable startup', note: '' },
          ]}
        />

        <DiaryDay
          label="Yesterday · Friday"
          items={[
            { time: '8:33p', src: 'instagram', intent: 'inspiration', preview: 'kitchen.diaries — ramen, midnight', note: '' },
            { time: '4:12p', src: 'sms', intent: 'remind me', preview: 'Maya — "send when you have 20m"', note: 'attach: pricing memo' },
            { time: '2:01p', src: 'gmail', intent: 'read later', preview: 'Nathan from Stripe — re: founder dinner', note: '' },
          ]}
        />

        <DiaryDay
          label="Thu · Apr 24"
          last
          items={[
            { time: '9:48p', src: 'youtube', intent: 'inspiration', preview: 'A Conversation with Werner Herzog', note: '52m' },
            { time: '7:02p', src: 'photos', intent: 'in orbit', preview: 'Whiteboard — capture funnel sketch', note: '1 photo' },
          ]}
        />

      </div>
    </div>
  );
}

// One day group — mono header + chronological entries with hairline rules.
function DiaryDay({ label, items, last = false }) {
  return (
    <div style={{ padding: '24px 0 0' }}>
      <div style={{ padding: '0 24px 12px' }}>
        <MonoLabel size={10}>{label}</MonoLabel>
      </div>
      <Rule />
      {items.map((it, i) => (
        <div key={i}>
          <div style={{ padding: '14px 24px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <div style={{ minWidth: 44, paddingTop: 2 }}>
              <div style={{ fontFamily: MONO, fontSize: 10, color: ORBIT.creamFaint, letterSpacing: '0.08em' }}>
                {it.time}
              </div>
            </div>
            <SourceGlyph kind={it.src} size={20}/>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13.5, lineHeight: 1.35, color: ORBIT.cream, letterSpacing: '-0.005em' }}>
                {it.preview}
              </div>
              {it.note && (
                <div style={{ fontFamily: SERIF, fontStyle: 'italic', fontSize: 13, color: ORBIT.creamDim, marginTop: 4, lineHeight: 1.3 }}>
                  {it.note}
                </div>
              )}
              <div style={{ marginTop: 8 }}>
                <span style={miniIntent(it.intent)}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: intentDot(it.intent) }} />
                  {it.intent}
                </span>
              </div>
            </div>
          </div>
          <Rule />
        </div>
      ))}
    </div>
  );
}

const intentDot = (i) => ({
  'remind me': ORBIT.accent,
  'inspiration': '#c8a4dc',
  'in orbit': '#84b8d6',
  'reference': '#a4c8a4',
  'read later': '#dcc384',
}[i] || ORBIT.cream);

const miniIntent = (i) => ({
  display: 'inline-flex', alignItems: 'center', gap: 6,
  fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em',
  color: ORBIT.creamDim, textTransform: 'uppercase',
});

const primaryBtn = {
  flex: 1,
  padding: '11px 16px',
  borderRadius: 999,
  border: 'none',
  background: ORBIT.accent,
  color: ORBIT.accentInk,
  fontFamily: SANS, fontWeight: 600, fontSize: 14,
  letterSpacing: '-0.01em',
  cursor: 'pointer',
};

const ghostBtn = {
  flex: 1,
  padding: '11px 16px',
  borderRadius: 999,
  border: `1px solid ${ORBIT.ruleHi}`,
  background: 'transparent',
  color: ORBIT.cream,
  fontFamily: SANS, fontWeight: 500, fontSize: 14,
  letterSpacing: '-0.01em',
  cursor: 'pointer',
};

const iconBtn = {
  width: 42, padding: 0, height: 42,
  borderRadius: 999,
  border: `1px solid ${ORBIT.ruleHi}`,
  background: 'transparent',
  color: ORBIT.creamDim,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  cursor: 'pointer',
};

Object.assign(window, { ScreenDiary });
