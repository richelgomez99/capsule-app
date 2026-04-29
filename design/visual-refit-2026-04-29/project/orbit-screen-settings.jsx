// Orbit screen: Settings.
// Privacy is constitutional: default-cloud + on-device toggle, quiet hours,
// dismissal cooldowns, the forget-everything button.

function ScreenSettings() {
  return (
    <div style={{
      width: '100%', height: '100%',
      background: ORBIT.bgDeep, color: ORBIT.cream,
      display: 'flex', flexDirection: 'column',
      fontFamily: SANS,
    }}>
      {/* Top bar */}
      <div style={{ padding: '14px 18px 10px', display: 'flex', alignItems: 'center', gap: 14 }}>
        <Icon size={20}><path d="m15 6-6 6 6 6"/></Icon>
        <div style={{ fontSize: 14, fontWeight: 500, letterSpacing: '-0.01em' }}>Settings</div>
      </div>
      <Rule />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 28 }}>
        {/* Hero — voice-y, partner-tone */}
        <div style={{ padding: '24px 24px 22px' }}>
          <MonoLabel style={{ marginBottom: 10 }}>// Principle I · Default Privacy</MonoLabel>
          <div style={{
            fontFamily: SERIF, fontSize: 22, fontWeight: 400, lineHeight: 1.25,
            letterSpacing: '-0.015em', color: ORBIT.cream,
          }}>
            Orbit is <span style={{ fontStyle: 'italic', color: ORBIT.accent }}>quiet by default</span>. You decide what it sees, when it speaks, and what it forgets.
          </div>
        </div>

        <SettingSection label="Where your captures think">
          <ToggleRow
            title="Use on-device AI"
            sub="Routes summarization, intent classification, and embeddings through Gemini Nano. Zero cloud calls. Pixel 8 and up."
            tag="LOCAL · NANO 4"
            on
          />
          <ToggleRow
            title="Cloud LLM (default)"
            sub="Anthropic Sonnet 4.6 + Haiku 4.5 via Vercel AI Gateway. No training on your content, no human review, deletable on request."
            tag="DEFAULT"
            on
          />
        </SettingSection>

        <SettingSection label="When Orbit is allowed to surface">
          <ValueRow
            title="Proactive surface ceiling"
            sub="Hard cap per 24 hours."
            value="5"
          />
          <ValueRow
            title="Quiet hours"
            sub="No notifications, no morning card."
            value="9p → 7a"
          />
          <ValueRow
            title="Dismissal cooldown"
            sub="If you dismiss a pattern, Orbit holds it back."
            value="7 days"
          />
          <ToggleRow
            title="Sunday digest"
            sub="One paged review on Sunday morning."
            on
          />
        </SettingSection>

        <SettingSection label="What Orbit may capture">
          <ToggleRow title="Floating bubble" sub="Always available across apps." on />
          <ToggleRow title="Share sheet" sub="From any app's share menu." on />
          <ToggleRow title="Clipboard observation" sub="Foreground only. Sealed at copy." on />
          <ToggleRow title="Screenshot pipeline" sub="OCR + intent at save. Photo never leaves device unless you tag it." on />
        </SettingSection>

        <SettingSection label="The hard line">
          <DangerRow
            title="Forget everything from before"
            sub="Erases captures, envelopes, embeddings, and learned preferences. Cloud backups are wiped within 24 hours. The button is real and works."
          />
        </SettingSection>

        <div style={{ padding: '16px 24px 0', textAlign: 'center' }}>
          <div style={{ fontFamily: MONO, fontSize: 9, letterSpacing: '0.14em', color: ORBIT.creamFaint, textTransform: 'uppercase', lineHeight: 1.7 }}>
            Orbit · v1.0.4 · build 218<br/>
            16 constitutional principles · audit log open
          </div>
        </div>
      </div>
    </div>
  );
}

function SettingSection({ label, children }) {
  return (
    <>
      <div style={{ padding: '8px 24px 10px' }}>
        <MonoLabel size={9.5}>{label}</MonoLabel>
      </div>
      <Rule />
      {children}
    </>
  );
}

function ToggleRow({ title, sub, tag, on = false }) {
  return (
    <>
      <div style={{ padding: '14px 24px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ fontSize: 14, color: ORBIT.cream, letterSpacing: '-0.005em' }}>{title}</div>
            {tag && (
              <span style={{
                fontFamily: MONO, fontSize: 8.5, letterSpacing: '0.14em',
                color: ORBIT.accent, padding: '2px 7px', borderRadius: 4,
                background: ORBIT.accentDim, border: `1px solid ${ORBIT.accent}55`,
              }}>{tag}</span>
            )}
          </div>
          {sub && (
            <div style={{ fontSize: 12, lineHeight: 1.4, color: ORBIT.creamDim, marginTop: 4 }}>
              {sub}
            </div>
          )}
        </div>
        <Toggle on={on} />
      </div>
      <Rule />
    </>
  );
}

function ValueRow({ title, sub, value }) {
  return (
    <>
      <div style={{ padding: '14px 24px', display: 'flex', gap: 14, alignItems: 'center' }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, color: ORBIT.cream, letterSpacing: '-0.005em' }}>{title}</div>
          {sub && <div style={{ fontSize: 12, color: ORBIT.creamDim, marginTop: 3 }}>{sub}</div>}
        </div>
        <div style={{
          fontFamily: SERIF, fontSize: 19, fontStyle: 'italic',
          color: ORBIT.accent, letterSpacing: '-0.01em',
        }}>{value}</div>
        <Icon size={16} stroke={ORBIT.creamFaint}><path d="m9 6 6 6-6 6"/></Icon>
      </div>
      <Rule />
    </>
  );
}

function DangerRow({ title, sub }) {
  return (
    <>
      <div style={{ padding: '16px 24px' }}>
        <div style={{ fontSize: 14, color: ORBIT.red, letterSpacing: '-0.005em', fontFamily: SERIF, fontStyle: 'italic', fontSize: 17 }}>
          {title}
        </div>
        <div style={{ fontSize: 12, lineHeight: 1.45, color: ORBIT.creamDim, marginTop: 6 }}>
          {sub}
        </div>
        <button style={{
          marginTop: 12,
          padding: '10px 16px',
          borderRadius: 999,
          border: `1px solid ${ORBIT.red}66`,
          background: 'transparent',
          color: ORBIT.red,
          fontFamily: SANS, fontWeight: 500, fontSize: 13,
          letterSpacing: '-0.005em',
          cursor: 'pointer',
        }}>
          Forget everything
        </button>
      </div>
      <Rule />
    </>
  );
}

function Toggle({ on }) {
  return (
    <div style={{
      width: 44, height: 26, borderRadius: 999,
      background: on ? ORBIT.accent : 'rgba(243,234,216,0.18)',
      position: 'relative', flexShrink: 0,
      transition: 'background 200ms',
    }}>
      <div style={{
        position: 'absolute',
        top: 3, left: on ? 21 : 3,
        width: 20, height: 20, borderRadius: '50%',
        background: on ? ORBIT.accentInk : ORBIT.cream,
        transition: 'left 200ms',
      }}/>
    </div>
  );
}

Object.assign(window, { ScreenSettings });
