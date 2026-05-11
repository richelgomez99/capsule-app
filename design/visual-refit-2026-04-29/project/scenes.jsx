// Orbit launch video — v2 (phone-centric, fast pacing)
// One continuous story on the phone. Every beat is a visible interaction.
// Envelopes are EDITABLE — you can re-tag, add layers, open the source,
// trigger follow-ups (text someone, draft a question, set a reminder).
//
// Beats:
//   0  – 3   Cold open: dot → wordmark
//   3  – 8   Manifesto: "Most AI waits. The right ones come to you."
//   8  – 13  Graveyard: 2,047 / 312 / 1.8% (fast count-up)
//  13  – 22  CAPTURE: Safari → highlight → Save to Orbit → intent chip → confirm
//  22  – 32  DIARY: captures land across apps; re-tag one (editable proof)
//  32  – 48  CLUSTER: bottom sheet → Summarize → cited bullets stream
//  48  – 58  CITATION: tap one → original source slides up
//  58  – 68  CALENDAR: block tomorrow → accepted
//  68  – 82  FOLLOW-UP: "Text Maya?" → SMS sheet pre-filled → Send
//  82  – 94  RAPID FEATURES: privacy toggle, quiet hours, Sunday digest
//  94  –110  Closing manifesto over still phone
//  110 –120  Logo lockup
// ─────────────────────────────────────────────────────────────────────────────

const PALETTE = {
  bg: '#0e1320', bgDeep: '#080b14',
  cream: '#f3ead8',
  creamDim: 'rgba(243,234,216,0.55)',
  creamFaint: 'rgba(243,234,216,0.22)',
  rule: 'rgba(243,234,216,0.14)',
  accent: '#e8b06a',
  accentDim: 'rgba(232,176,106,0.18)',
};

const SERIF = '"Cormorant Garamond", "EB Garamond", Georgia, serif';
const SANS = '"Inter", system-ui, -apple-system, sans-serif';
const MONO = '"JetBrains Mono", ui-monospace, monospace';

// ── Helpers ─────────────────────────────────────────────────────────────────
const fadeIn = (t, dur = 0.3) => clamp(t / dur, 0, 1);
const fadeOut = (t, end, dur = 0.3) => 1 - clamp((t - (end - dur)) / dur, 0, 1);
const ease = Easing.easeOutCubic;
const easeBack = Easing.easeOutBack;

// ── Caption (lower-third editorial mark) ────────────────────────────────────
function Caption({ text, sub }) {
  const { localTime, duration } = useSprite();
  const o = clamp(fadeIn(localTime, 0.3) - clamp((localTime - (duration - 0.3)) / 0.3, 0, 1), 0, 1);
  return (
    <div data-cap style={{
      position: 'absolute', left: 80, right: 80, bottom: 64, opacity: o,
      fontFamily: MONO, fontSize: 16, letterSpacing: '0.18em',
      textTransform: 'uppercase', color: PALETTE.creamDim,
      display: 'flex', justifyContent: 'space-between',
    }}>
      <div>{text}</div>
      {sub && <div style={{ color: PALETTE.creamFaint }}>{sub}</div>}
    </div>
  );
}

function WordReveal({ words, x, y, size, weight = 400, color, italic = false, perWord = 0.08, delay = 0, exitAt }) {
  const { localTime } = useSprite();
  return (
    <div style={{
      position: 'absolute', left: x, top: y,
      fontFamily: SERIF, fontSize: size, fontWeight: weight,
      fontStyle: italic ? 'italic' : 'normal',
      color: color || PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.0,
    }}>
      {words.map((w, i) => {
        const start = delay + i * perWord;
        const t = clamp((localTime - start) / 0.35, 0, 1);
        const eo = ease(t);
        const ex = exitAt ? clamp((localTime - exitAt) / 0.3, 0, 1) : 0;
        return (
          <span key={i} style={{
            display: 'inline-block', marginRight: '0.28em',
            opacity: eo * (1 - ex),
            transform: `translateY(${(1 - eo) * 14 + ex * -8}px)`,
          }}>{w}</span>
        );
      })}
    </div>
  );
}

// ── Persistent Phone (rendered ONCE, content swaps per beat) ────────────────
// The phone stays on screen from t=13 → t=110. Sub-scenes render inside it.
function StoryPhone({ children, x, y, scale = 1, rotate = 0 }) {
  return (
    <div style={{
      position: 'absolute', left: x, top: y,
      transform: `scale(${scale}) rotate(${rotate}deg)`,
      transformOrigin: 'center top',
      transition: 'transform 600ms cubic-bezier(0.34, 1.2, 0.64, 1), left 600ms cubic-bezier(0.34, 1.2, 0.64, 1), top 600ms cubic-bezier(0.34, 1.2, 0.64, 1)',
    }}>
      <div style={{
        width: 420, height: 880, borderRadius: 52,
        background: '#0a0a0a', border: '10px solid #1a1a1a',
        boxShadow: '0 50px 120px rgba(0,0,0,0.6), 0 0 0 1px rgba(232,176,106,0.06)',
        padding: 8, boxSizing: 'border-box',
      }}>
        <div style={{
          width: '100%', height: '100%', borderRadius: 44,
          overflow: 'hidden', background: PALETTE.bgDeep,
          position: 'relative', display: 'flex', flexDirection: 'column',
        }}>
          {/* status bar */}
          <div style={{
            height: 44, padding: '0 28px',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            color: PALETTE.cream, fontFamily: SANS, fontSize: 14, fontWeight: 500,
            flexShrink: 0, position: 'relative',
          }}>
            <div>9:47</div>
            <div style={{
              position: 'absolute', left: '50%', top: 12, transform: 'translateX(-50%)',
              width: 22, height: 22, borderRadius: '50%', background: '#000',
            }}/>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <svg width="14" height="14" viewBox="0 0 16 16"><path d="M8 13.3L.67 5.97a10.37 10.37 0 0114.66 0L8 13.3z" fill={PALETTE.cream}/></svg>
              <svg width="14" height="10" viewBox="0 0 16 10"><rect x="0" y="2" width="13" height="6" rx="1" fill="none" stroke={PALETTE.cream} strokeWidth="1"/><rect x="2" y="4" width="9" height="2" fill={PALETTE.cream}/></svg>
            </div>
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}

// ── OPENING MONTAGE (0–13s) ─────────────────────────────────────────────────
// "What do we do on our phones every day?"
// Rapid montage of capture moments → dump into Notes/Notion → silence → "...nothing."
// Then a quiet manifesto beat. The phone never leaves until the next act picks up.

// A tiny phone "tile" used in the montage grid.
function MiniPhone({ x, y, scale = 0.32, rotate = 0, children, opacity = 1, glow = false }) {
  return (
    <div style={{
      position: 'absolute', left: x, top: y,
      transform: `translate(-50%, -50%) scale(${scale}) rotate(${rotate}deg)`,
      opacity,
      transition: 'opacity 200ms',
    }}>
      <div style={{
        width: 420, height: 880, borderRadius: 52,
        background: '#0a0a0a', border: '10px solid #1a1a1a',
        boxShadow: glow
          ? `0 30px 80px rgba(0,0,0,0.7), 0 0 60px ${PALETTE.accent}40`
          : '0 30px 80px rgba(0,0,0,0.7)',
        padding: 8, boxSizing: 'border-box',
      }}>
        <div style={{
          width: '100%', height: '100%', borderRadius: 44,
          overflow: 'hidden', background: '#0c0f18',
          position: 'relative', display: 'flex', flexDirection: 'column',
        }}>
          {children}
        </div>
      </div>
    </div>
  );
}

// Screenshot flash overlay
function ShotFlash({ trigger }) {
  const o = trigger ? clamp((trigger.t - trigger.at) / 0.08, 0, 1) * (1 - clamp((trigger.t - trigger.at - 0.05) / 0.2, 0, 1)) : 0;
  return o > 0 ? (
    <div style={{ position: 'absolute', inset: 0, background: 'white', opacity: o * 0.85, pointerEvents: 'none' }}/>
  ) : null;
}

// Selection highlight overlay
function CopyHighlight({ from, to, on }) {
  return on ? (
    <div style={{
      position: 'absolute', left: from.x, top: from.y,
      width: to.x - from.x, height: to.y - from.y,
      background: 'rgba(120,180,255,0.28)',
      border: '1px solid rgba(120,180,255,0.6)',
      borderRadius: 2, pointerEvents: 'none',
    }}/>
  ) : null;
}

// Screen content variants
function TwitterScreen({ t, shotAt }) {
  return (
    <>
      <div style={{ height: 36, background: '#15202b', display: 'flex', alignItems: 'center', padding: '0 12px',
        fontFamily: SANS, fontSize: 11, color: '#8899a6' }}>
        <div style={{ width: 24, height: 24, borderRadius: '50%', background: '#1da1f2', marginRight: 8 }}/>
        <span style={{ color: '#fff', fontWeight: 600 }}>Patrick OShaughnessy</span>
        <span style={{ marginLeft: 6 }}>· 2h</span>
      </div>
      <div style={{ flex: 1, padding: 14, fontFamily: SANS, fontSize: 12, color: '#dfe6ec', background: '#15202b', lineHeight: 1.4 }}>
        The most underrated skill in early-stage pricing is the courage to anchor high and let the comparison do the work.
        <div style={{ marginTop: 14, color: '#8899a6', fontSize: 10 }}>♡ 1.2k · ↻ 312</div>
      </div>
      <ShotFlash trigger={{ t, at: shotAt }} />
    </>
  );
}

function ArticleScreen({ t, copyAt }) {
  const showCopy = t > copyAt && t < copyAt + 0.6;
  return (
    <>
      <div style={{ height: 32, background: '#1a1f2c', display: 'flex', alignItems: 'center', padding: '0 14px',
        fontFamily: SANS, fontSize: 10, color: '#888' }}>
        nytimes.com
      </div>
      <div style={{ flex: 1, padding: 14, background: '#fbf8f1', color: '#1a1206',
        fontFamily: SERIF, fontSize: 14, lineHeight: 1.45, position: 'relative' }}>
        <div style={{ fontSize: 18, fontWeight: 600, marginBottom: 10, letterSpacing: '-0.01em' }}>
          The Loneliness of the Long-Distance Founder
        </div>
        Most early-stage operators describe the work in the same way:{' '}
        <span style={{
          background: showCopy ? 'rgba(80,140,255,0.32)' : 'transparent',
          boxShadow: showCopy ? '0 0 0 1px rgba(80,140,255,0.6)' : 'none',
          transition: 'background 120ms',
        }}>relentless context-switching with no one to compare notes against.</span>
        <CopyHighlight on={false} from={{x:0,y:0}} to={{x:0,y:0}}/>
        {showCopy && (
          <div style={{
            position: 'absolute', left: 24, top: 90,
            background: '#222', color: '#fff', borderRadius: 8,
            fontSize: 9, padding: '4px 10px', display: 'flex', gap: 8,
            opacity: clamp((t - copyAt) / 0.15, 0, 1),
          }}>
            <span style={{ color: '#7ab' }}>Copy</span>
            <span>Look Up</span>
            <span>Share</span>
          </div>
        )}
      </div>
    </>
  );
}

function PodcastScreen({ t, shotAt }) {
  return (
    <>
      <div style={{ flex: 1, background: 'linear-gradient(160deg, #5b3aff 0%, #1a1245 100%)',
        padding: 18, color: '#fff', display: 'flex', flexDirection: 'column' }}>
        <div style={{ fontFamily: SANS, fontSize: 9, opacity: 0.7, letterSpacing: '0.12em', textTransform: 'uppercase' }}>Podcasts · Now Playing</div>
        <div style={{ width: '100%', aspectRatio: '1', borderRadius: 14, background: 'linear-gradient(135deg,#ff7eb6,#7c3aff)', margin: '14px 0' }}/>
        <div style={{ fontFamily: SERIF, fontSize: 17, fontWeight: 500, lineHeight: 1.2 }}>Acquired</div>
        <div style={{ fontFamily: SANS, fontSize: 10, opacity: 0.7, marginTop: 4 }}>Pricing power & moat-building</div>
        <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-around', fontSize: 18, opacity: 0.85 }}>
          <span>⏮</span><span>⏯</span><span>⏭</span>
        </div>
      </div>
      <ShotFlash trigger={{ t, at: shotAt }} />
    </>
  );
}

function IGScreen({ t, shotAt }) {
  return (
    <>
      <div style={{ height: 32, background: '#000', display: 'flex', alignItems: 'center', padding: '0 12px',
        fontFamily: SANS, fontSize: 11, color: '#fff', fontWeight: 600 }}>Instagram</div>
      <div style={{ flex: 1, background: '#000', padding: 0 }}>
        <div style={{ height: 28, padding: '0 12px', display: 'flex', alignItems: 'center', gap: 8, color: '#fff', fontSize: 10 }}>
          <div style={{ width: 18, height: 18, borderRadius: '50%', background: 'linear-gradient(135deg,#f58529,#dd2a7b,#8134af)' }}/>
          <span style={{ fontWeight: 600 }}>kitchen.diaries</span>
        </div>
        <div style={{ height: 200, background: 'linear-gradient(180deg,#3b2410,#150a04)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: SERIF, fontStyle: 'italic', fontSize: 24, color: '#e8b06a' }}>
          ramen · midnight
        </div>
        <div style={{ padding: 10, color: '#fff', fontSize: 10, fontFamily: SANS }}>
          <div style={{ display: 'flex', gap: 14, fontSize: 16, marginBottom: 6 }}>
            <span>♡</span><span>💬</span><span>↗</span>
          </div>
          The 4-hour broth. Recipe in stories →
        </div>
      </div>
      <ShotFlash trigger={{ t, at: shotAt }} />
    </>
  );
}

function MessageScreen({ t, copyAt }) {
  const showCopy = t > copyAt && t < copyAt + 0.6;
  return (
    <>
      <div style={{ height: 32, background: '#1a1f2c', display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: SANS, fontSize: 11, color: '#fff', fontWeight: 600 }}>Maya Chen</div>
      <div style={{ flex: 1, background: '#0c0f18', padding: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ alignSelf: 'flex-start', maxWidth: '78%', background: '#2a2f3c', color: '#fff',
          borderRadius: 14, padding: '8px 12px', fontFamily: SANS, fontSize: 11, lineHeight: 1.35,
          position: 'relative',
          boxShadow: showCopy ? '0 0 0 2px rgba(80,140,255,0.6)' : 'none',
          background: showCopy ? 'rgba(80,140,255,0.2)' : '#2a2f3c',
        }}>
          Read "How Brian Chesky runs Airbnb" — changed how I think about taste in product.
        </div>
        <div style={{ alignSelf: 'flex-start', maxWidth: '60%', background: '#2a2f3c', color: '#fff',
          borderRadius: 14, padding: '8px 12px', fontFamily: SANS, fontSize: 11 }}>
          Send when you have 20m
        </div>
      </div>
    </>
  );
}

function NotesDumpScreen({ t, fillAt }) {
  // dumps appear one by one
  const items = [
    { kind: 'shot', label: 'IMG_4221.HEIC' },
    { kind: 'paste', label: 'relentless context-switching with no one…' },
    { kind: 'shot', label: 'IMG_4222.HEIC' },
    { kind: 'paste', label: 'Read "How Brian Chesky runs Airbnb"…' },
    { kind: 'shot', label: 'IMG_4223.HEIC' },
    { kind: 'shot', label: 'IMG_4224.HEIC' },
  ];
  return (
    <>
      <div style={{ height: 32, background: '#fbf8f1', display: 'flex', alignItems: 'center', padding: '0 12px',
        fontFamily: SANS, fontSize: 10, color: '#666', fontWeight: 600 }}>Notes · Untitled</div>
      <div style={{ flex: 1, background: '#fbf8f1', padding: 12, fontFamily: SANS, fontSize: 9.5,
        color: '#1a1206', display: 'flex', flexDirection: 'column', gap: 8, overflow: 'hidden' }}>
        {items.map((it, i) => {
          const o = clamp((t - fillAt - i * 0.18) / 0.2, 0, 1);
          return (
            <div key={i} style={{ opacity: o, transform: `translateY(${(1-o)*4}px)` }}>
              {it.kind === 'shot' ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div style={{ width: 28, height: 22, background: 'linear-gradient(135deg,#999,#bbb)', borderRadius: 3 }}/>
                  <div style={{ color: '#888', fontSize: 8 }}>{it.label}</div>
                </div>
              ) : (
                <div style={{ color: '#1a1206', borderLeft: '2px solid #ccc', paddingLeft: 6,
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>"{it.label}"</div>
              )}
            </div>
          );
        })}
      </div>
    </>
  );
}

function NotionDumpScreen({ t, fillAt }) {
  const items = [
    'price anchoring — Patrick OShaughnessy',
    'broth recipe (kitchen.diaries)',
    'Brian Chesky article (Maya)',
    'pricing teardown? (link)',
    'Acquired ep — pricing',
    'send to Maya re: anchor pricing',
  ];
  return (
    <>
      <div style={{ height: 32, background: '#fff', display: 'flex', alignItems: 'center', padding: '0 12px',
        fontFamily: SANS, fontSize: 10, color: '#37352f', fontWeight: 600 }}>📥 Inbox</div>
      <div style={{ flex: 1, background: '#fff', padding: '10px 12px', fontFamily: SANS, fontSize: 10,
        color: '#37352f', display: 'flex', flexDirection: 'column', gap: 7 }}>
        {items.map((s, i) => {
          const o = clamp((t - fillAt - i * 0.18) / 0.2, 0, 1);
          return (
            <div key={i} style={{ opacity: o, display: 'flex', gap: 6, alignItems: 'flex-start' }}>
              <span style={{ color: '#999' }}>☐</span>
              <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s}</span>
            </div>
          );
        })}
      </div>
    </>
  );
}

// Date stamp that flips through dates to show time passing
function DateFlip({ t, startAt }) {
  const dates = ['oct 14', 'oct 22', 'nov 3', 'nov 19', 'dec 8', 'jan 6', 'feb 18', 'mar 30'];
  const local = t - startAt;
  const idx = Math.min(dates.length - 1, Math.floor(local / 0.18));
  if (local < 0) return null;
  return (
    <div style={{
      fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
      color: PALETTE.creamFaint, textTransform: 'uppercase',
    }}>{dates[idx]}</div>
  );
}

function OpeningMontage() {
  return (
    <Sprite start={0} end={13.0}>
      {({ localTime: t }) => {
        // Storyboard:
        //  0.0  fade up: question "what do we do on our phones every day?"
        //  1.5  question fades; first cluster of phones flies in (capture moments)
        //  2.0  twitter screenshot (flash)
        //  2.5  article copy
        //  3.0  podcast screenshot
        //  3.5  IG screenshot
        //  4.0  message copy
        //  4.5  cluster pulls back; "and then..." appears
        //  5.5  two phones zoom in: notes dump + notion inbox; items rain in
        //  8.5  "and then..." question fades; date flips begin (months pass)
        //  10.5 dumps grey out; everything desaturates; ".....nothing." reveals
        //  12.0 fade to black for handoff
        const exit = clamp((t - 12.0) / 1.0, 0, 1);
        const masterO = 1 - exit;

        // Beat A — opening question
        const qIn = clamp(t / 0.5, 0, 1);
        const qOut = clamp((t - 1.6) / 0.4, 0, 1);
        const qO = (qIn - qOut) * masterO;

        // Beat B — capture montage
        const phones = [
          { x: 480,  y: 540, rot: -8, in: 1.7, out: 4.8, render: (lt) => <TwitterScreen  t={lt} shotAt={2.0 - 1.7} />, scale: 0.34 },
          { x: 760,  y: 380, rot: 4,  in: 2.1, out: 4.8, render: (lt) => <ArticleScreen  t={lt} copyAt={2.5 - 2.1} />, scale: 0.36 },
          { x: 1100, y: 600, rot: -3, in: 2.5, out: 4.8, render: (lt) => <PodcastScreen  t={lt} shotAt={3.0 - 2.5} />, scale: 0.34 },
          { x: 1380, y: 380, rot: 6,  in: 2.9, out: 4.8, render: (lt) => <IGScreen       t={lt} shotAt={3.5 - 2.9} />, scale: 0.36 },
          { x: 1660, y: 600, rot: -5, in: 3.3, out: 4.8, render: (lt) => <MessageScreen  t={lt} copyAt={4.0 - 3.3} />, scale: 0.34 },
        ];

        // Beat C — "and then..." — exits BEFORE the "months later" reveal so they don't collide
        const andThenIn = clamp((t - 4.6) / 0.5, 0, 1);
        const andThenOut = clamp((t - 9.6) / 0.4, 0, 1);
        const andThenO = (andThenIn - andThenOut) * masterO;

        // Beat D — destination phones (notes + notion); slide off when "months later" reveals
        const destIn = clamp((t - 5.4) / 0.5, 0, 1);
        const destOut = clamp((t - 10.6) / 0.5, 0, 1);
        const destO = (destIn - destOut) * masterO;
        // grey-out at 9.5
        const greyOut = clamp((t - 9.5) / 0.8, 0, 1);

        // Beat E — date flip
        const dateO = clamp((t - 8.0) / 0.4, 0, 1) - clamp((t - 11.0) / 0.4, 0, 1);

        // Beat F — "months later, you scroll past it..."
        const nothingIn = clamp((t - 11.0) / 0.6, 0, 1);
        const nothingO = nothingIn * masterO;

        return (
          <div style={{ position: 'absolute', inset: 0, opacity: masterO }}>
            {/* Opening question */}
            <div style={{
              position: 'absolute', left: 0, right: 0, top: 380,
              textAlign: 'center', opacity: qO,
              transform: `translateY(${(1 - qIn) * 12}px)`,
            }}>
              <div style={{
                fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
                color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 28,
              }}>// every day</div>
              <div style={{
                fontFamily: SERIF, fontSize: 110, fontWeight: 300,
                color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05,
              }}>
                What do we all do<br/>
                <span style={{ fontStyle: 'italic', color: PALETTE.creamDim }}>on our phones?</span>
              </div>
            </div>

            {/* Capture montage phones */}
            {phones.map((p, i) => {
              const inT = clamp((t - p.in) / 0.4, 0, 1);
              const outT = clamp((t - p.out) / 0.5, 0, 1);
              const o = (inT - outT) * masterO;
              if (o <= 0.02) return null;
              const lt = t - p.in;
              const slide = (1 - ease(inT)) * 60;
              return (
                <MiniPhone key={i}
                  x={p.x} y={p.y + slide} scale={p.scale * (0.85 + 0.15 * ease(inT))}
                  rotate={p.rot}
                  opacity={o} glow>
                  {p.render(lt)}
                </MiniPhone>
              );
            })}

            {/* "and then..." */}
            <div style={{
              position: 'absolute', left: 120, top: 180,
              opacity: andThenO,
              transform: `translateY(${(1 - andThenIn) * 8}px)`,
            }}>
              <div style={{
                fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
                color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 18,
              }}>// and then…</div>
              <div style={{
                fontFamily: SERIF, fontSize: 88, fontWeight: 300,
                color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.0,
              }}>
                We dump it<br/>
                <span style={{ fontStyle: 'italic' }}>somewhere.</span>
              </div>
            </div>

            {/* Destination phones — notes + notion */}
            {destO > 0.02 && (
              <>
                <div style={{
                  position: 'absolute', left: 720, top: 540,
                  opacity: destO,
                  filter: `grayscale(${greyOut * 0.8}) brightness(${1 - greyOut * 0.35})`,
                  transition: 'filter 400ms',
                }}>
                  <MiniPhone x={0} y={0} scale={0.55} rotate={-3} glow>
                    <NotesDumpScreen t={t} fillAt={5.8} />
                  </MiniPhone>
                </div>
                <div style={{
                  position: 'absolute', left: 1200, top: 540,
                  opacity: destO,
                  filter: `grayscale(${greyOut * 0.8}) brightness(${1 - greyOut * 0.35})`,
                  transition: 'filter 400ms',
                }}>
                  <MiniPhone x={0} y={0} scale={0.55} rotate={4} glow>
                    <NotionDumpScreen t={t} fillAt={5.9} />
                  </MiniPhone>
                </div>
              </>
            )}

            {/* Date flip — months pass */}
            <div style={{
              position: 'absolute', left: 0, right: 0, top: 920,
              textAlign: 'center', opacity: dateO,
            }}>
              <DateFlip t={t} startAt={8.0} />
            </div>

            {/* "...nothing." reveal */}
            <div style={{
              position: 'absolute', left: 120, right: 120, top: 320,
              opacity: nothingO,
              textAlign: 'left',
            }}>
              <div style={{
                fontFamily: SERIF, fontSize: 120, fontWeight: 300,
                color: PALETTE.creamDim, letterSpacing: '-0.025em', lineHeight: 1.0,
              }}>
                Months later,<br/>
                <span style={{ color: PALETTE.cream, fontStyle: 'italic' }}>you'll scroll past it.</span>
              </div>
              <div style={{
                marginTop: 36,
                fontFamily: SERIF, fontSize: 64, fontWeight: 400,
                color: PALETTE.accent, letterSpacing: '-0.02em', fontStyle: 'italic',
                opacity: clamp((t - 11.6) / 0.5, 0, 1),
              }}>You won't even remember why you saved it.</div>
            </div>

            <div data-cap style={{
              position: 'absolute', left: 80, right: 80, bottom: 64,
              fontFamily: MONO, fontSize: 16, letterSpacing: '0.18em',
              textTransform: 'uppercase', color: PALETTE.creamDim,
              display: 'flex', justifyContent: 'space-between',
              opacity: 0.6 * masterO,
            }}>
              <div>// the everyday loop</div>
              <div style={{ color: PALETTE.creamFaint }}>00</div>
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

// ── ACT 1 — CAPTURE MOMENT (the "I want that" beat) ─────────────────────────
function CaptureAct() {
  return (
    <Sprite start={13.0} end={22.0}>
      {({ localTime }) => {
        const inT = clamp(localTime / 0.4, 0, 1);
        // Phone slides in from right
        const phoneX = 1180 + (1 - ease(inT)) * 600;

        // Sub-beats inside this act:
        // 0    Safari article visible
        // 1.5  Text highlights
        // 2.5  Long-press menu appears
        // 3.5  "Save to Orbit" tapped → sheet drops
        // 5.0  Intent chips in sheet
        // 6.0  "in orbit" tap, sheet collapses to envelope
        // 7.5  envelope flies into diary tab
        const t = localTime;

        return (
          <div style={{ position: 'absolute', inset: 0 }}>
            {/* Left annotation */}
            <div style={{
              position: 'absolute', left: 120, top: 280, width: 600,
              opacity: clamp((t - 0.4) / 0.4, 0, 1),
            }}>
              <div style={{
                fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
                color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 24,
                display: 'flex', alignItems: 'center', gap: 14,
              }}>
                <span style={{ color: PALETTE.creamDim }}>01 / 06</span>
                <span style={{ width: 32, height: 1, background: PALETTE.accent }}/>
                Capture
              </div>
              <div style={{
                fontFamily: SERIF, fontSize: 88, fontWeight: 300,
                color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05, marginBottom: 32,
              }}>
                Highlight.<br/>
                <span style={{ fontStyle: 'italic' }}>Tell Orbit why.</span>
              </div>
              <div style={{
                fontFamily: SERIF, fontSize: 24, color: PALETTE.creamDim,
                lineHeight: 1.4, maxWidth: 520,
              }}>
                One gesture. From any app. Orbit asks what you meant — remind, inspire, follow up — and lands it in your diary.
              </div>
            </div>

            <StoryPhone x={phoneX} y={120} scale={0.95}>
              <SafariCaptureScene t={t} />
            </StoryPhone>
            <Caption text="// from any app" sub="03" />
          </div>
        );
      }}
    </Sprite>
  );
}

function SafariCaptureScene({ t }) {
  // t in 0..9
  const showHighlight = t > 1.0;
  const highlightProg = clamp((t - 1.0) / 0.6, 0, 1);
  const showMenu = t > 2.3;
  const tapSave = t > 3.4 && t < 3.8;
  const sheetT = clamp((t - 3.6) / 0.5, 0, 1);
  const showChips = t > 4.5;
  const tapChip = t > 5.8 && t < 6.2;
  const sheetExit = clamp((t - 6.2) / 0.4, 0, 1);
  const flyT = clamp((t - 6.6) / 1.0, 0, 1);
  const showConfirm = t > 7.4 && t < 9.0;

  return (
    <>
      {/* Safari URL bar */}
      <div style={{
        height: 44, background: '#1a1f2c', display: 'flex', alignItems: 'center',
        gap: 8, padding: '0 14px', borderBottom: '1px solid rgba(255,255,255,0.06)',
        flexShrink: 0,
      }}>
        <div style={{ width: 14, height: 14, borderRadius: 4, background: PALETTE.creamFaint, opacity: 0.4 }}/>
        <div style={{
          flex: 1, height: 28, background: 'rgba(255,255,255,0.06)', borderRadius: 8,
          padding: '0 12px', display: 'flex', alignItems: 'center',
          fontFamily: SANS, fontSize: 12, color: PALETTE.creamDim,
        }}>a16z.com/pricing-playbook</div>
      </div>

      {/* Article body */}
      <div style={{
        flex: 1, padding: '24px 22px', overflow: 'hidden', position: 'relative',
        background: '#0c1018',
      }}>
        <div style={{ fontFamily: SERIF, fontSize: 22, color: PALETTE.cream, lineHeight: 1.3, marginBottom: 14 }}>
          The Pre-Seed Pricing Playbook
        </div>
        <div style={{ fontFamily: SANS, fontSize: 12, color: PALETTE.creamDim, marginBottom: 18 }}>
          a16z · 8 min read
        </div>
        <div style={{ fontFamily: SERIF, fontSize: 17, color: PALETTE.cream, lineHeight: 1.55 }}>
          Most early-stage founders treat pricing as a number.{' '}
          <span style={{
            background: showHighlight ? `rgba(232,176,106,${0.35 * highlightProg})` : 'transparent',
            transition: 'background 200ms',
            boxShadow: showHighlight ? `0 0 0 2px rgba(232,176,106,${0.6 * highlightProg})` : 'none',
            borderRadius: 2,
          }}>Anchor on a single hero price; let comparison do the work.</span>{' '}
          Discounting at the bottom of the funnel is a tax on conviction.
        </div>

        {/* Long-press selection menu */}
        {showMenu && !tapChip && sheetT < 0.1 && (
          <div style={{
            position: 'absolute', left: 60, top: 200,
            background: '#1a1f2c', borderRadius: 12, padding: 4,
            display: 'flex', gap: 0, fontFamily: SANS, fontSize: 12, color: PALETTE.cream,
            boxShadow: '0 8px 24px rgba(0,0,0,0.6)',
            border: '1px solid rgba(255,255,255,0.08)',
            opacity: clamp((t - 2.3) / 0.3, 0, 1),
            transform: `translateY(${(1 - clamp((t - 2.3) / 0.3, 0, 1)) * 8}px)`,
          }}>
            <MenuItem label="Copy" />
            <MenuItem label="Look Up" />
            <MenuItem label="Save to Orbit" highlight tap={tapSave} accent />
          </div>
        )}

        {/* tap pulse on menu item */}
        {tapSave && (
          <div style={{
            position: 'absolute', left: 200, top: 215,
            width: 30, height: 30, borderRadius: '50%',
            border: `2px solid ${PALETTE.accent}`,
            opacity: 1 - clamp((t - 3.4) / 0.4, 0, 1),
            transform: `scale(${1 + clamp((t - 3.4) / 0.4, 0, 1) * 1.4})`,
          }}/>
        )}
      </div>

      {/* Save to Orbit bottom sheet */}
      {sheetT > 0 && sheetExit < 1 && (
        <div style={{
          position: 'absolute', left: 0, right: 0, bottom: 0,
          background: 'rgba(20,26,42,0.98)',
          borderTop: `1px solid ${PALETTE.accentDim}`,
          borderRadius: '20px 20px 0 0',
          padding: '20px 22px 26px',
          backdropFilter: 'blur(20px)',
          transform: `translateY(${(1 - sheetT * (1 - sheetExit)) * 100}%)`,
        }}>
          {/* handle */}
          <div style={{
            width: 36, height: 4, borderRadius: 2, background: PALETTE.creamFaint,
            margin: '0 auto 16px',
          }}/>
          <div style={{
            fontFamily: MONO, fontSize: 10, letterSpacing: '0.18em',
            color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 10,
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: PALETTE.accent }}/>
            Save to Orbit
          </div>
          <div style={{
            fontFamily: SERIF, fontStyle: 'italic', fontSize: 15,
            color: PALETTE.cream, lineHeight: 1.4, marginBottom: 16,
            padding: '10px 12px', background: 'rgba(232,176,106,0.06)',
            borderLeft: `2px solid ${PALETTE.accent}`, borderRadius: 4,
          }}>
            "Anchor on a single hero price; let comparison do the work."
          </div>
          <div style={{
            fontFamily: MONO, fontSize: 10, letterSpacing: '0.14em',
            color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 10,
          }}>What did you mean?</div>
          {showChips && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {[
                { label: 'remind me', i: 0 },
                { label: 'inspiration', i: 1 },
                { label: 'in orbit', i: 2, sel: tapChip || t > 6.0 },
                { label: '+ add tag', i: 3, dim: true },
              ].map(c => {
                const o = clamp((t - 4.5 - c.i * 0.1) / 0.3, 0, 1);
                return (
                  <div key={c.i} style={{
                    padding: '10px 16px', borderRadius: 100,
                    border: `1px solid ${c.sel ? PALETTE.accent : PALETTE.rule}`,
                    background: c.sel ? 'rgba(232,176,106,0.16)' : 'transparent',
                    color: c.sel ? PALETTE.accent : (c.dim ? PALETTE.creamDim : PALETTE.cream),
                    fontFamily: SANS, fontSize: 13, fontWeight: 500,
                    opacity: o, transform: `scale(${c.sel ? 1.05 : 1})`,
                    transition: 'all 200ms',
                  }}>{c.label}</div>
                );
              })}
            </div>
          )}
          {tapChip && (
            <div style={{
              position: 'absolute', left: 178, top: 152,
              width: 30, height: 30, borderRadius: '50%',
              border: `2px solid ${PALETTE.accent}`,
              opacity: 1 - clamp((t - 5.8) / 0.4, 0, 1),
              transform: `scale(${1 + clamp((t - 5.8) / 0.4, 0, 1) * 1.4})`,
            }}/>
          )}
        </div>
      )}

      {/* Flying envelope after sheet collapses */}
      {flyT > 0 && flyT < 1 && (
        <div style={{
          position: 'absolute',
          left: 200 - 60,
          top: 600 - flyT * 540,
          transform: `scale(${1 - flyT * 0.5})`,
          opacity: 1 - flyT * 0.4,
        }}>
          <div style={{
            width: 120, height: 36, borderRadius: 18,
            background: PALETTE.accent,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#1a1206', fontFamily: SANS, fontSize: 11, fontWeight: 600,
            boxShadow: `0 0 24px ${PALETTE.accent}`,
            letterSpacing: '0.04em',
          }}>
            ● in orbit
          </div>
        </div>
      )}

      {/* Toast confirmation */}
      {showConfirm && (
        <div style={{
          position: 'absolute', left: 16, right: 16, top: 60,
          background: 'rgba(232,176,106,0.96)', color: '#1a1206',
          borderRadius: 12, padding: '12px 14px',
          fontFamily: SANS, fontSize: 13, fontWeight: 500,
          display: 'flex', alignItems: 'center', gap: 10,
          opacity: clamp((t - 7.4) / 0.3, 0, 1) - clamp((t - 8.6) / 0.3, 0, 1),
          boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
        }}>
          <span style={{ fontSize: 14 }}>●</span>
          Saved · tap to edit intent or dismiss
        </div>
      )}
    </>
  );
}

function MenuItem({ label, highlight, tap, accent }) {
  return (
    <div style={{
      padding: '8px 12px',
      color: accent ? PALETTE.accent : PALETTE.cream,
      fontWeight: highlight ? 600 : 400,
      borderLeft: highlight ? `1px solid rgba(255,255,255,0.08)` : 'none',
    }}>{label}</div>
  );
}

// ── ACT 2 — DIARY: captures land + re-tag (proves editable) ─────────────────
function DiaryAct() {
  return (
    <Sprite start={22.0} end={32.0}>
      {({ localTime }) => {
        const t = localTime;
        return (
          <div style={{ position: 'absolute', inset: 0 }}>
            <div style={{
              position: 'absolute', left: 120, top: 280, width: 600,
              opacity: clamp((t - 0.3) / 0.4, 0, 1),
            }}>
              <div style={{
                fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
                color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 24,
                display: 'flex', alignItems: 'center', gap: 14,
              }}>
                <span style={{ color: PALETTE.creamDim }}>02 / 06</span>
                <span style={{ width: 32, height: 1, background: PALETTE.accent }}/>
                Diary
              </div>
              <div style={{
                fontFamily: SERIF, fontSize: 88, fontWeight: 300,
                color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05, marginBottom: 32,
              }}>
                Captures land<br/>
                <span style={{ fontStyle: 'italic' }}>across every app.</span>
              </div>
              <div style={{
                fontFamily: SERIF, fontSize: 24, color: PALETTE.creamDim,
                lineHeight: 1.4, maxWidth: 520,
              }}>
                Edit any intent, retag it, add layers, jump to the source. Nothing's locked — the agent learns from your edits.
              </div>
            </div>

            <StoryPhone x={1180} y={120} scale={0.95}>
              <DiaryScene t={t} />
            </StoryPhone>
            <Caption text="// editable · always" sub="04" />
          </div>
        );
      }}
    </Sprite>
  );
}

const DIARY_CAPTURES = [
  { time: 'Fri 8:14p', app: 'Twitter',  intent: 'in orbit', text: '"price anchoring lessons from Jasper..."' },
  { time: 'Fri 11:02p', app: 'Safari',  intent: 'in orbit', text: 'a16z — pre-seed pricing playbook' },
  { time: 'Sat 8:21a', app: 'Podcasts', intent: 'inspiration', text: 'Acquired — pre-seed valuations' },
  { time: 'Sat 9:33a', app: 'Safari',   intent: 'in orbit', text: 'Stripe Atlas — pricing pages teardown' },
];

function DiaryScene({ t }) {
  // Captures appear staggered 0..3.5s, then user taps capture #2 at 4.5,
  // intent editor opens, retags from 'in orbit' to 'remind me' at 6.5, closes 7.5
  const editorT = t > 4.4 ? clamp((t - 4.5) / 0.4, 0, 1) : 0;
  const editorClose = t > 7.5 ? clamp((t - 7.5) / 0.4, 0, 1) : 0;
  const editorVis = editorT - editorClose;
  const retagged = t > 6.4;
  const tapEdit = t > 4.3 && t < 4.7;
  const tapNew = t > 6.4 && t < 6.8;

  return (
    <>
      <div style={{ padding: '20px 24px 12px', flexShrink: 0 }}>
        <div style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          marginBottom: 14,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: PALETTE.accent, boxShadow: `0 0 12px ${PALETTE.accent}` }}/>
            <div style={{ fontFamily: SERIF, fontSize: 24, color: PALETTE.cream, letterSpacing: '-0.02em' }}>Orbit</div>
          </div>
          <div style={{
            fontFamily: MONO, fontSize: 10, letterSpacing: '0.12em',
            color: PALETTE.creamFaint, textTransform: 'uppercase',
          }}>Diary</div>
        </div>
        <div style={{
          fontFamily: SERIF, fontSize: 32, fontWeight: 300,
          color: PALETTE.cream, letterSpacing: '-0.02em', lineHeight: 1.05,
        }}>
          Saturday<br/>
          <span style={{ fontStyle: 'italic', color: PALETTE.creamDim }}>morning.</span>
        </div>
      </div>
      <div style={{ flex: 1, padding: '0 24px', position: 'relative', overflow: 'hidden' }}>
        <div style={{
          fontFamily: MONO, fontSize: 10, letterSpacing: '0.12em',
          color: PALETTE.creamFaint, textTransform: 'uppercase',
          padding: '12px 0 4px', borderTop: `1px solid ${PALETTE.rule}`, marginTop: 8,
        }}>4 captures · today</div>

        {DIARY_CAPTURES.map((c, i) => {
          const appearAt = 0.6 + i * 0.5;
          const o = clamp((t - appearAt) / 0.4, 0, 1);
          const isEditing = i === 1;
          const intentNow = (isEditing && retagged) ? 'remind me' : c.intent;
          const intentHi = isEditing && tapEdit;
          return (
            <div key={i} style={{
              display: 'flex', gap: 12, padding: '12px 0',
              borderBottom: `1px solid rgba(243,234,216,0.06)`,
              opacity: o,
              background: intentHi ? 'rgba(232,176,106,0.08)' : 'transparent',
              borderRadius: 6,
              transition: 'background 200ms',
            }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: PALETTE.creamFaint, marginTop: 8 }}/>
              <div style={{ flex: 1 }}>
                <div style={{
                  fontFamily: MONO, fontSize: 10, letterSpacing: '0.08em',
                  color: PALETTE.creamDim, textTransform: 'uppercase',
                  display: 'flex', gap: 8, marginBottom: 4, whiteSpace: 'nowrap',
                  alignItems: 'center',
                }}>
                  <span>{c.time}</span>
                  <span>·</span>
                  <span>{c.app}</span>
                  <span style={{ flex: 1 }}/>
                  <span style={{
                    padding: '2px 8px', borderRadius: 100,
                    background: isEditing && retagged ? 'rgba(232,176,106,0.16)' : 'rgba(243,234,216,0.06)',
                    color: isEditing && retagged ? PALETTE.accent : PALETTE.creamDim,
                    border: isEditing && retagged ? `1px solid ${PALETTE.accent}` : 'none',
                    fontWeight: 500,
                  }}>{intentNow}</span>
                </div>
                <div style={{
                  fontFamily: SANS, fontSize: 13, color: PALETTE.cream, lineHeight: 1.4,
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                }}>{c.text}</div>
              </div>
            </div>
          );
        })}

        {/* tap pulse on row 2 */}
        {tapEdit && (
          <div style={{
            position: 'absolute', left: 200, top: 156,
            width: 28, height: 28, borderRadius: '50%',
            border: `2px solid ${PALETTE.accent}`,
            opacity: 1 - clamp((t - 4.3) / 0.4, 0, 1),
            transform: `scale(${1 + clamp((t - 4.3) / 0.4, 0, 1) * 1.4})`,
          }}/>
        )}

        {/* Intent editor popover */}
        {editorVis > 0.05 && (
          <div style={{
            position: 'absolute', left: 16, right: 16, top: 140,
            background: 'rgba(20,26,42,0.98)', borderRadius: 16,
            border: `1px solid ${PALETTE.accentDim}`, padding: '16px 18px',
            boxShadow: '0 12px 40px rgba(0,0,0,0.5)',
            opacity: editorVis,
            transform: `translateY(${(1 - editorVis) * 12}px)`,
          }}>
            <div style={{
              fontFamily: MONO, fontSize: 10, letterSpacing: '0.18em',
              color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 10,
            }}>Edit intent</div>
            <div style={{
              fontFamily: SERIF, fontStyle: 'italic', fontSize: 13,
              color: PALETTE.cream, marginBottom: 14,
            }}>"a16z — pre-seed pricing playbook"</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
              {['remind me','inspiration','in orbit','+ tag'].map((c, i) => {
                const sel = (retagged && c === 'remind me') || (!retagged && c === 'in orbit');
                const isNew = retagged && c === 'remind me';
                return (
                  <div key={i} style={{
                    padding: '6px 12px', borderRadius: 100,
                    border: `1px solid ${sel ? PALETTE.accent : PALETTE.rule}`,
                    background: sel ? 'rgba(232,176,106,0.14)' : 'transparent',
                    color: sel ? PALETTE.accent : PALETTE.cream,
                    fontFamily: SANS, fontSize: 11, fontWeight: 500,
                    transform: isNew && tapNew ? 'scale(1.08)' : 'scale(1)',
                    transition: 'all 200ms',
                  }}>{c}</div>
                );
              })}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <SmallBtn label="Open source" />
              <SmallBtn label="Add note" />
              <SmallBtn label="Dismiss" dim />
            </div>
          </div>
        )}
      </div>
    </>
  );
}

function SmallBtn({ label, dim }) {
  return (
    <div style={{
      padding: '6px 12px', borderRadius: 8,
      border: `1px solid ${PALETTE.rule}`,
      color: dim ? PALETTE.creamDim : PALETTE.cream,
      fontFamily: SANS, fontSize: 11, fontWeight: 500,
    }}>{label}</div>
  );
}

// ── ACT 3 — CLUSTER → SUMMARY → CITATION → CALENDAR ────────────────────────
function ClusterAct() {
  return (
    <Sprite start={32.0} end={68.0}>
      {({ localTime }) => {
        const t = localTime;
        return (
          <div style={{ position: 'absolute', inset: 0 }}>
            <ClusterAnnotations t={t} />
            <StoryPhone x={1180} y={120} scale={0.95}>
              <ClusterScene t={t} />
            </StoryPhone>
            <Caption text="// the agent speaks first" sub="05" />
          </div>
        );
      }}
    </Sprite>
  );
}

function ClusterAnnotations({ t }) {
  // Stagger boundaries so each label fully exits before the next enters (no cross-fade overlap)
  const labels = [
    { at: 0,    until: 3.6,  kicker: '03 / 06', title: 'Surface', body: 'Three captures. Two domains. Four hours. Orbit speaks first.' },
    { at: 4,    until: 13.6, kicker: '04 / 06', title: 'Summarize', body: 'Tap once. Cited bullets stream — every claim traced to its capture.' },
    { at: 14,   until: 21.6, kicker: '05 / 06', title: 'Audit', body: 'Tap a citation. The original source slides up — read the source, not the summary.' },
    { at: 22,   until: 35.6, kicker: '06 / 06', title: 'Act', body: 'Tomorrow at 10:30 is open. Block it. The agent does the next thing.' },
  ];
  return (
    <>
      {labels.map((l, i) => {
        const o = clamp((t - l.at) / 0.4, 0, 1) - clamp((t - l.until) / 0.4, 0, 1);
        if (o <= 0) return null;
        return (
          <div key={i} style={{
            position: 'absolute', left: 120, top: 280, width: 600,
            opacity: o, transform: `translateY(${(1 - o) * 12}px)`,
          }}>
            <div style={{
              fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
              color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 24,
              display: 'flex', alignItems: 'center', gap: 14,
            }}>
              <span style={{ color: PALETTE.creamDim }}>{l.kicker}</span>
              <span style={{ width: 32, height: 1, background: PALETTE.accent }}/>
              {l.title}
            </div>
            <div style={{
              fontFamily: SERIF, fontSize: 76, fontWeight: 300,
              color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05, marginBottom: 28,
            }}>{l.body}</div>
          </div>
        );
      })}
    </>
  );
}

const BULLETS = [
  { text: 'Anchor on a single hero price; let comparison do the work.', src: 'a16z · Sat 11:02p', id: '7f3a' },
  { text: 'Land-and-expand beats discounting at pre-seed.',              src: 'Acquired clip · Sat 8:21a', id: '8b21' },
  { text: 'Pricing CTA above the fold; one-line value, no jargon.',      src: 'Stripe Atlas · Sat 9:33a', id: '9c44' },
];

function ClusterScene({ t }) {
  // 0    Diary visible (carry-over)
  // 0.5  Cluster card slides up
  // 3    Tap Summarize
  // 4    Bullets stream in (one per 0.8s)
  // 14   Tap citation
  // 14.5 Source sheet slides up
  // 22   Source dismissed
  // 22.5 Calendar chip appears
  // 27   Tap block
  // 27.5 Accepted state
  const cardT = clamp((t - 0.4) / 0.6, 0, 1);
  const tapSum = t > 2.8 && t < 3.2;
  const showBullets = t > 3.2;
  const tapCite = t > 13.6 && t < 14.0;
  const showSource = t > 13.8 && t < 22.0;
  const sourceT = clamp((t - 13.8) / 0.5, 0, 1) - clamp((t - 21.5) / 0.4, 0, 1);
  const calT = clamp((t - 22.4) / 0.5, 0, 1);
  const tapBlock = t > 26.8 && t < 27.2;
  const accepted = t > 27.2;

  return (
    <>
      {/* Diary header (compact) */}
      <div style={{ padding: '16px 24px 8px', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: PALETTE.accent, boxShadow: `0 0 12px ${PALETTE.accent}` }}/>
          <div style={{ fontFamily: SERIF, fontSize: 22, color: PALETTE.cream }}>Orbit</div>
        </div>
        <div style={{ fontFamily: SERIF, fontSize: 26, color: PALETTE.cream, letterSpacing: '-0.02em' }}>
          Saturday <span style={{ fontStyle: 'italic', color: PALETTE.creamDim }}>morning.</span>
        </div>
      </div>

      <div style={{ flex: 1, padding: '0 24px', position: 'relative', overflow: 'hidden' }}>
        <div style={{
          fontFamily: MONO, fontSize: 10, letterSpacing: '0.12em',
          color: PALETTE.creamFaint, textTransform: 'uppercase',
          padding: '8px 0 4px', borderTop: `1px solid ${PALETTE.rule}`, marginTop: 4,
        }}>4 captures · pricing</div>
        {DIARY_CAPTURES.map((c, i) => (
          <div key={i} style={{
            display: 'flex', gap: 12, padding: '8px 0',
            borderBottom: '1px solid rgba(243,234,216,0.06)',
          }}>
            <div style={{ width: 6, height: 6, borderRadius: '50%', background: PALETTE.accent, marginTop: 6, opacity: 0.6 }}/>
            <div style={{ flex: 1 }}>
              <div style={{
                fontFamily: MONO, fontSize: 9, letterSpacing: '0.08em',
                color: PALETTE.creamDim, textTransform: 'uppercase', whiteSpace: 'nowrap',
              }}>
                {c.time} · {c.app}
              </div>
              <div style={{
                fontFamily: SANS, fontSize: 12, color: PALETTE.cream,
                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
              }}>{c.text}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Cluster card / bottom sheet */}
      <div style={{
        position: 'absolute', left: 12, right: 12, bottom: 12,
        background: 'rgba(20,26,42,0.97)',
        border: `1px solid ${PALETTE.accentDim}`,
        borderRadius: 24, padding: '18px 20px',
        boxShadow: `0 -20px 60px rgba(0,0,0,0.6)`,
        backdropFilter: 'blur(20px)',
        transform: `translateY(${(1 - cardT) * 360}px)`,
        opacity: cardT,
      }}>
        <div style={{
          fontFamily: MONO, fontSize: 10, letterSpacing: '0.14em',
          color: PALETTE.accent, textTransform: 'uppercase',
          marginBottom: 10, display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <span style={{ width: 6, height: 6, borderRadius: '50%', background: PALETTE.accent }}/>
          Research-session cluster
        </div>

        {!showBullets && (
          <>
            <div style={{
              fontFamily: SERIF, fontSize: 16, fontStyle: 'italic',
              color: PALETTE.cream, lineHeight: 1.4, marginBottom: 14,
            }}>
              "You came back to <span style={{ color: PALETTE.accent, fontStyle: 'normal' }}>pricing</span> four times this weekend across Twitter, Safari, and a podcast. Want me to pull it together?"
            </div>
            <div style={{ display: 'flex', gap: 8, position: 'relative' }}>
              <button style={{
                background: PALETTE.accent, color: '#1a1206',
                border: 'none', borderRadius: 100, padding: '9px 16px',
                fontFamily: SANS, fontSize: 12, fontWeight: 600,
                position: 'relative',
              }}>
                Summarize
                {tapSum && (
                  <div style={{
                    position: 'absolute', inset: 0, borderRadius: 100,
                    boxShadow: `0 0 0 ${clamp((t - 2.8) / 0.4, 0, 1) * 14}px ${PALETTE.accentDim}`,
                    opacity: 1 - clamp((t - 2.8) / 0.4, 0, 1),
                  }}/>
                )}
              </button>
              <button style={{
                background: 'transparent', color: PALETTE.cream,
                border: `1px solid ${PALETTE.rule}`, borderRadius: 100,
                padding: '9px 14px', fontFamily: SANS, fontSize: 12,
              }}>Open all</button>
              <button style={{
                background: 'transparent', color: PALETTE.creamDim,
                border: 'none', padding: '9px 10px', fontFamily: SANS, fontSize: 12,
              }}>Dismiss</button>
            </div>
          </>
        )}

        {showBullets && (
          <>
            <div style={{
              fontFamily: SANS, fontSize: 11, color: PALETTE.creamDim,
              marginBottom: 10, display: 'flex', alignItems: 'center', gap: 8,
            }}>
              <span style={{ color: PALETTE.accent }}>●</span>
              4 captures · all sourced
            </div>
            {BULLETS.map((b, i) => {
              const o = clamp((t - 3.6 - i * 0.7) / 0.5, 0, 1);
              const isCited = i === 0;
              const citedHi = isCited && tapCite;
              return (
                <div key={i} style={{
                  opacity: ease(o), transform: `translateY(${(1 - ease(o)) * 8}px)`,
                  marginBottom: 8, position: 'relative',
                  background: citedHi ? 'rgba(232,176,106,0.10)' : 'transparent',
                  borderRadius: 6, padding: citedHi ? '4px 6px' : '0',
                  margin: citedHi ? '-4px -6px 8px' : '0 0 8px',
                }}>
                  <div style={{
                    fontFamily: SERIF, fontSize: 14, color: PALETTE.cream,
                    lineHeight: 1.45,
                  }}>{b.text}</div>
                  <div style={{
                    fontFamily: MONO, fontSize: 9, color: PALETTE.accent,
                    letterSpacing: '0.06em', marginTop: 2,
                  }}>↳ {b.src}</div>
                </div>
              );
            })}

            {/* Calendar chip */}
            {calT > 0 && (
              <div style={{
                marginTop: 6, padding: '12px 14px',
                background: accepted ? 'rgba(232,176,106,0.12)' : 'rgba(243,234,216,0.04)',
                border: `1px solid ${accepted ? PALETTE.accent : PALETTE.rule}`,
                borderRadius: 14,
                opacity: calT, transform: `translateY(${(1 - calT) * 8}px)`,
                display: 'flex', alignItems: 'center', gap: 10,
                position: 'relative',
              }}>
                <div style={{
                  width: 28, height: 28, borderRadius: 8,
                  background: accepted ? PALETTE.accent : PALETTE.rule,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  flexShrink: 0,
                }}>
                  {accepted ? (
                    <svg width="12" height="12" viewBox="0 0 14 14" fill="none">
                      <path d="M3 7l3 3 5-6" stroke="#1a1206" strokeWidth="2" strokeLinecap="round" fill="none"/>
                    </svg>
                  ) : (
                    <svg width="12" height="12" viewBox="0 0 14 14"><rect x="2" y="3" width="10" height="9" rx="1" stroke={PALETTE.cream} strokeWidth="1.2" fill="none"/></svg>
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{
                    fontFamily: SANS, fontSize: 12, color: PALETTE.cream, fontWeight: 500,
                  }}>{accepted ? 'Blocked: Pricing · 10:30–12:00' : 'Tomorrow 10:30–12 is open. Block it?'}</div>
                  <div style={{
                    fontFamily: MONO, fontSize: 9, color: PALETTE.creamFaint,
                    letterSpacing: '0.06em', marginTop: 1,
                  }}>{accepted ? 'Calendar · synced' : 'Suggested by Orbit'}</div>
                </div>
                {!accepted && (
                  <div style={{
                    background: PALETTE.cream, color: PALETTE.bg,
                    borderRadius: 100, padding: '6px 12px',
                    fontFamily: SANS, fontSize: 11, fontWeight: 600,
                    position: 'relative',
                  }}>
                    Block
                    {tapBlock && (
                      <div style={{
                        position: 'absolute', inset: 0, borderRadius: 100,
                        boxShadow: `0 0 0 ${clamp((t - 26.8) / 0.4, 0, 1) * 12}px ${PALETTE.accentDim}`,
                        opacity: 1 - clamp((t - 26.8) / 0.4, 0, 1),
                      }}/>
                    )}
                  </div>
                )}
              </div>
            )}
          </>
        )}

        {/* tap pulse on first citation */}
        {tapCite && (
          <div style={{
            position: 'absolute', left: 120, top: 138,
            width: 28, height: 28, borderRadius: '50%',
            border: `2px solid ${PALETTE.accent}`,
            opacity: 1 - clamp((t - 13.6) / 0.4, 0, 1),
            transform: `scale(${1 + clamp((t - 13.6) / 0.4, 0, 1) * 1.3})`,
          }}/>
        )}
      </div>

      {/* Source detail sheet */}
      {sourceT > 0.05 && (
        <div style={{
          position: 'absolute', left: 0, right: 0, bottom: 0, top: 80,
          background: 'rgba(8,11,20,0.98)',
          borderRadius: '20px 20px 0 0',
          padding: '14px 22px 22px',
          opacity: sourceT,
          transform: `translateY(${(1 - sourceT) * 100}%)`,
          backdropFilter: 'blur(20px)',
          border: `1px solid ${PALETTE.accentDim}`,
        }}>
          <div style={{ width: 36, height: 4, borderRadius: 2, background: PALETTE.creamFaint, margin: '0 auto 14px' }}/>
          <div style={{
            fontFamily: MONO, fontSize: 10, letterSpacing: '0.18em',
            color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 10,
          }}>Source · envelope #7f3a</div>
          <div style={{
            fontFamily: SERIF, fontSize: 18, color: PALETTE.cream,
            letterSpacing: '-0.01em', marginBottom: 8,
          }}>The Pre-Seed Pricing Playbook</div>
          <div style={{
            fontFamily: SANS, fontSize: 11, color: PALETTE.creamDim, marginBottom: 14,
          }}>a16z · saved Sat 11:02p · Safari</div>
          <div style={{
            padding: '12px 14px',
            background: 'rgba(232,176,106,0.06)',
            borderLeft: `2px solid ${PALETTE.accent}`,
            borderRadius: 4, marginBottom: 14,
            fontFamily: SERIF, fontSize: 13, fontStyle: 'italic',
            color: PALETTE.cream, lineHeight: 1.4,
          }}>
            "Anchor on a single hero price; let comparison do the work."
          </div>
          <div style={{
            fontFamily: SANS, fontSize: 12, color: PALETTE.creamDim,
            lineHeight: 1.5, marginBottom: 14,
          }}>
            Most early-stage founders treat pricing as a number. Discounting at the bottom of the funnel is a tax on conviction…
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <SmallBtn label="Open in Safari" />
            <SmallBtn label="Edit intent" />
            <SmallBtn label="Dismiss" dim />
          </div>
        </div>
      )}
    </>
  );
}

// ── ACT 4 — FOLLOW-UP: Text Maya? ───────────────────────────────────────────
function FollowUpAct() {
  return (
    <Sprite start={68.0} end={82.0}>
      {({ localTime }) => {
        const t = localTime;
        return (
          <div style={{ position: 'absolute', inset: 0 }}>
            <FollowUpAnnotations t={t} />
            <StoryPhone x={1180} y={120} scale={0.95}>
              <FollowUpScene t={t} />
            </StoryPhone>
            <Caption text="// the agent does the next thing" sub="07" />
          </div>
        );
      }}
    </Sprite>
  );
}

function FollowUpAnnotations({ t }) {
  return (
    <div style={{ position: 'absolute', left: 120, top: 280, width: 600 }}>
      <div style={{
        fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
        color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 24,
        display: 'flex', alignItems: 'center', gap: 14,
        opacity: clamp(t / 0.4, 0, 1),
      }}>
        <span style={{ color: PALETTE.creamDim }}>07 / 06</span>
        <span style={{ width: 32, height: 1, background: PALETTE.accent }}/>
        Follow up
      </div>
      <div style={{
        fontFamily: SERIF, fontSize: 88, fontWeight: 300,
        color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05, marginBottom: 32,
      }}>
        Text Maya?<br/>
        <span style={{ fontStyle: 'italic' }}>Already drafted.</span>
      </div>
      <div style={{
        fontFamily: SERIF, fontSize: 24, color: PALETTE.creamDim,
        lineHeight: 1.4, maxWidth: 520,
      }}>
        Save a name and Orbit knows it's a person — pre-fills the message, opens the sheet, leaves the send to you.
      </div>
    </div>
  );
}

function FollowUpScene({ t }) {
  // 0    Capture appears: "Maya at Stripe — knows pricing"
  // 1    Follow-up suggestion appears
  // 3    Tap "Text Maya"
  // 4    SMS sheet slides in, pre-filled
  // 8    Tap Send
  // 9    Sent confirmation
  const captureIn = clamp(t / 0.5, 0, 1);
  const suggestIn = clamp((t - 1.0) / 0.5, 0, 1);
  const tapText = t > 2.8 && t < 3.2;
  const sheetT = clamp((t - 3.2) / 0.6, 0, 1) - clamp((t - 12.0) / 0.4, 0, 1);
  const tapSend = t > 7.6 && t < 8.0;
  const sent = t > 8.0;

  return (
    <>
      <div style={{ padding: '20px 24px 12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: PALETTE.accent, boxShadow: `0 0 12px ${PALETTE.accent}` }}/>
          <div style={{ fontFamily: SERIF, fontSize: 22, color: PALETTE.cream }}>Orbit</div>
        </div>
        <div style={{ fontFamily: SERIF, fontSize: 26, color: PALETTE.cream }}>
          One more thing
        </div>
      </div>
      <div style={{ flex: 1, padding: '0 24px', position: 'relative' }}>
        {/* Capture card */}
        <div style={{
          padding: '14px 16px',
          background: 'rgba(243,234,216,0.04)',
          border: `1px solid ${PALETTE.rule}`,
          borderRadius: 14, marginTop: 8,
          opacity: captureIn,
        }}>
          <div style={{
            fontFamily: MONO, fontSize: 10, letterSpacing: '0.08em',
            color: PALETTE.creamDim, textTransform: 'uppercase',
            display: 'flex', gap: 8, marginBottom: 6,
          }}>Sat 9:33a · Notes · person</div>
          <div style={{
            fontFamily: SERIF, fontStyle: 'italic', fontSize: 16,
            color: PALETTE.cream, marginBottom: 8,
          }}>"Maya at Stripe — knows pricing"</div>
          <div style={{
            display: 'inline-block',
            padding: '3px 10px', borderRadius: 100,
            background: 'rgba(232,176,106,0.14)',
            color: PALETTE.accent, border: `1px solid ${PALETTE.accent}`,
            fontFamily: SANS, fontSize: 11, fontWeight: 500,
          }}>contact</div>
        </div>

        {/* Follow-up suggestion */}
        <div style={{
          marginTop: 16, padding: '14px 16px',
          background: 'rgba(232,176,106,0.08)',
          border: `1px solid ${PALETTE.accentDim}`,
          borderRadius: 16, opacity: suggestIn,
          transform: `translateY(${(1 - suggestIn) * 8}px)`,
        }}>
          <div style={{
            fontFamily: SERIF, fontSize: 15, color: PALETTE.cream,
            lineHeight: 1.4, fontStyle: 'italic', marginBottom: 12,
          }}>
            "Want to ask Maya about the hero-price idea?"
          </div>
          <div style={{ display: 'flex', gap: 8, position: 'relative' }}>
            <div style={{
              position: 'relative',
              background: PALETTE.accent, color: '#1a1206',
              borderRadius: 100, padding: '9px 14px',
              fontFamily: SANS, fontSize: 12, fontWeight: 600,
            }}>
              Text Maya
              {tapText && (
                <div style={{
                  position: 'absolute', inset: 0, borderRadius: 100,
                  boxShadow: `0 0 0 ${clamp((t - 2.8) / 0.4, 0, 1) * 14}px ${PALETTE.accentDim}`,
                  opacity: 1 - clamp((t - 2.8) / 0.4, 0, 1),
                }}/>
              )}
            </div>
            <SmallBtn label="Add to reading list" />
            <SmallBtn label="Later" dim />
          </div>
        </div>
      </div>

      {/* SMS sheet */}
      {sheetT > 0.05 && (
        <div style={{
          position: 'absolute', left: 0, right: 0, bottom: 0, top: 80,
          background: 'rgba(15,18,28,0.98)',
          borderRadius: '20px 20px 0 0',
          padding: '14px 18px 22px',
          opacity: sheetT, transform: `translateY(${(1 - sheetT) * 100}%)`,
          backdropFilter: 'blur(20px)',
          border: `1px solid ${PALETTE.accentDim}`,
          display: 'flex', flexDirection: 'column',
        }}>
          <div style={{ width: 36, height: 4, borderRadius: 2, background: PALETTE.creamFaint, margin: '0 auto 12px' }}/>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            paddingBottom: 12, borderBottom: `1px solid ${PALETTE.rule}`, marginBottom: 14,
          }}>
            <div style={{ fontFamily: SANS, fontSize: 12, color: PALETTE.creamDim }}>Cancel</div>
            <div style={{ fontFamily: SANS, fontSize: 14, color: PALETTE.cream, fontWeight: 600 }}>New Message</div>
            <div style={{ fontFamily: SANS, fontSize: 12, color: PALETTE.accent }}>Send</div>
          </div>
          <div style={{
            display: 'flex', gap: 10, alignItems: 'center', paddingBottom: 10,
            borderBottom: `1px solid ${PALETTE.rule}`, marginBottom: 14,
          }}>
            <div style={{ fontFamily: SANS, fontSize: 11, color: PALETTE.creamDim }}>To:</div>
            <div style={{
              padding: '4px 10px', borderRadius: 100,
              background: 'rgba(232,176,106,0.14)', color: PALETTE.accent,
              fontFamily: SANS, fontSize: 12, fontWeight: 500,
              border: `1px solid ${PALETTE.accent}`,
            }}>Maya Chen</div>
          </div>

          {/* iMessage-style draft bubble */}
          <div style={{ flex: 1 }}>
            <div style={{
              alignSelf: 'flex-end', maxWidth: '85%', marginLeft: 'auto',
              background: PALETTE.accent, color: '#1a1206',
              borderRadius: 18, padding: '10px 14px',
              fontFamily: SANS, fontSize: 13, lineHeight: 1.4, fontWeight: 500,
            }}>
              Hey Maya — saw a16z's pricing piece this morning, thinking about hero pricing for our pre-seed. Spare 15 min this week?
            </div>
            {sent && (
              <div style={{
                fontFamily: MONO, fontSize: 9, color: PALETTE.creamDim,
                letterSpacing: '0.08em', textAlign: 'right', marginTop: 6,
                opacity: clamp((t - 8.0) / 0.4, 0, 1),
              }}>delivered</div>
            )}
          </div>

          {/* compose row */}
          <div style={{
            marginTop: 12, padding: '10px 12px',
            background: 'rgba(255,255,255,0.04)',
            borderRadius: 100,
            display: 'flex', alignItems: 'center', gap: 10,
            fontFamily: SANS, fontSize: 12, color: PALETTE.creamDim,
          }}>
            <div style={{ flex: 1 }}>iMessage</div>
            <div style={{
              width: 28, height: 28, borderRadius: '50%',
              background: tapSend ? PALETTE.accent : PALETTE.cream,
              color: '#1a1206',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, position: 'relative',
            }}>↑
              {tapSend && (
                <div style={{
                  position: 'absolute', inset: 0, borderRadius: '50%',
                  boxShadow: `0 0 0 ${clamp((t - 7.6) / 0.4, 0, 1) * 12}px ${PALETTE.accentDim}`,
                  opacity: 1 - clamp((t - 7.6) / 0.4, 0, 1),
                }}/>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

// ── ACT 5 — RAPID FEATURES (privacy, quiet, digest) — phone stays on ───────
function RapidFeatures() {
  return (
    <Sprite start={82.0} end={94.0}>
      {({ localTime }) => {
        const t = localTime;
        // 3 vignettes, 4s each
        const vIdx = t < 4 ? 0 : t < 8 ? 1 : 2;
        const vT = t < 4 ? t : t < 8 ? t - 4 : t - 8;

        const titles = [
          { kicker: '08 / Privacy', title: 'Local AI, one tap.', body: 'Strong cloud terms by default. Or run it all on-device — Gemini Nano, zero cloud calls.' },
          { kicker: '09 / Quiet',   title: 'Five surfaces.\nA day. Max.', body: 'Quiet hours 9p–7a. Dismiss a pattern, it cools for a week. The agent gets quieter and more right.' },
          { kicker: '10 / Rhythm',  title: 'Sunday, 8 pm.\nA week, distilled.', body: 'Morning card. Evening reflection. Sunday digest. The agent has a cadence, not a feed.' },
        ];
        const v = titles[vIdx];

        return (
          <div style={{ position: 'absolute', inset: 0 }}>
            <div style={{
              position: 'absolute', left: 120, top: 320, width: 600,
              opacity: clamp(vT / 0.3, 0, 1) - clamp((vT - 3.7) / 0.3, 0, 1),
              transform: `translateY(${(1 - clamp(vT / 0.4, 0, 1)) * 12}px)`,
            }}>
              <div style={{
                fontFamily: MONO, fontSize: 12, letterSpacing: '0.24em',
                color: PALETTE.accent, textTransform: 'uppercase', marginBottom: 22,
              }}>{v.kicker}</div>
              <div style={{
                fontFamily: SERIF, fontSize: 76, fontWeight: 300,
                color: PALETTE.cream, letterSpacing: '-0.025em', lineHeight: 1.05, marginBottom: 24,
                whiteSpace: 'pre-line',
              }}>{v.title}</div>
              <div style={{
                fontFamily: SERIF, fontSize: 22, color: PALETTE.creamDim,
                lineHeight: 1.4, maxWidth: 520,
              }}>{v.body}</div>
            </div>
            <StoryPhone x={1180} y={120} scale={0.95}>
              {vIdx === 0 && <PhonePrivacy t={vT} />}
              {vIdx === 1 && <PhoneQuiet t={vT} />}
              {vIdx === 2 && <PhoneDigest t={vT} />}
            </StoryPhone>
            <Caption text="// also" sub={`0${8 + vIdx}`} />
          </div>
        );
      }}
    </Sprite>
  );
}

function PhonePrivacy({ t }) {
  const toggleAt = 1.6;
  const toggled = t >= toggleAt;
  return (
    <div style={{ padding: '24px 24px' }}>
      <div style={{
        fontFamily: MONO, fontSize: 11, letterSpacing: '0.18em',
        color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 18,
      }}>Settings · Privacy</div>
      <div style={{
        background: 'rgba(243,234,216,0.04)',
        border: `1px solid ${PALETTE.rule}`, borderRadius: 14, padding: 18,
        marginBottom: 14,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <div style={{ fontFamily: SANS, fontSize: 14, color: PALETTE.cream, fontWeight: 500 }}>Use on-device AI</div>
            <div style={{ fontFamily: SANS, fontSize: 11, color: PALETTE.creamDim, marginTop: 4 }}>Gemini Nano · zero cloud calls</div>
          </div>
          <div style={{
            width: 48, height: 28, borderRadius: 100,
            background: toggled ? PALETTE.accent : PALETTE.rule,
            position: 'relative', transition: 'background 300ms',
          }}>
            <div style={{
              position: 'absolute', top: 4, left: toggled ? 24 : 4,
              width: 20, height: 20, borderRadius: '50%', background: PALETTE.cream,
              transition: 'left 300ms cubic-bezier(0.34, 1.56, 0.64, 1)',
            }}/>
          </div>
        </div>
      </div>
      <div style={{
        fontFamily: MONO, fontSize: 10, color: PALETTE.creamFaint,
        letterSpacing: '0.08em', display: 'flex', gap: 6, alignItems: 'center',
        whiteSpace: 'nowrap',
      }}>
        <span>capture</span><span>→</span>
        <span style={{ color: toggled ? PALETTE.accent : PALETTE.creamDim, fontWeight: toggled ? 600 : 400 }}>device</span>
        <span>→</span>
        <span style={{ textDecoration: toggled ? 'line-through' : 'none', opacity: toggled ? 0.4 : 1 }}>cloud</span>
        <span>→</span><span>response</span>
      </div>
      {toggled && (
        <div style={{
          marginTop: 14, padding: '8px 12px',
          background: 'rgba(232,176,106,0.10)',
          border: `1px solid ${PALETTE.accentDim}`,
          borderRadius: 10, fontFamily: MONO, fontSize: 10,
          color: PALETTE.accent, letterSpacing: '0.08em',
        }}>● 0 cloud calls in last 24h</div>
      )}
    </div>
  );
}

function PhoneQuiet({ t }) {
  const dots = [
    { hr: 7.5, label: 'Morning card' },
    { hr: 9.7, label: 'Cluster · pricing' },
    { hr: 13.5, label: 'Capture confirmed' },
    { hr: 18.0, label: 'Evening reflection' },
    { hr: 20.5, label: 'Sunday digest' },
  ];
  return (
    <div style={{ padding: '24px' }}>
      <div style={{
        fontFamily: MONO, fontSize: 11, letterSpacing: '0.18em',
        color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 24,
      }}>Today · 9:47 am</div>
      <div style={{ position: 'relative', height: 80, marginBottom: 28 }}>
        <div style={{ position: 'absolute', left: 0, right: 0, top: 30, height: 1, background: PALETTE.rule }}/>
        {Array.from({ length: 25 }).map((_, h) => (
          <div key={h} style={{
            position: 'absolute', top: 26, left: `${(h / 24) * 100}%`,
            width: 1, height: h % 6 === 0 ? 10 : 4, background: PALETTE.creamFaint,
          }}/>
        ))}
        <div style={{ position: 'absolute', left: 0, top: 22, width: `${(7/24)*100}%`, height: 16, background: 'rgba(243,234,216,0.04)' }}/>
        <div style={{ position: 'absolute', left: `${(21/24)*100}%`, top: 22, width: `${(3/24)*100}%`, height: 16, background: 'rgba(243,234,216,0.04)' }}/>
        {dots.map((d, i) => {
          const o = clamp((t - 0.4 - i * 0.2) / 0.3, 0, 1);
          return (
            <div key={i} style={{
              position: 'absolute', top: 18, left: `${(d.hr/24)*100}%`,
              transform: `translateX(-50%) scale(${0.4 + 0.6 * easeBack(o)})`,
              width: 12, height: 12, borderRadius: '50%',
              background: PALETTE.accent, boxShadow: `0 0 14px ${PALETTE.accent}`,
              opacity: o,
            }}/>
          );
        })}
        {[0, 6, 12, 18, 24].map(h => (
          <div key={h} style={{
            position: 'absolute', top: 50, left: `${(h/24)*100}%`,
            transform: 'translateX(-50%)',
            fontFamily: MONO, fontSize: 9, color: PALETTE.creamFaint, letterSpacing: '0.08em',
          }}>{String(h).padStart(2, '0')}:00</div>
        ))}
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12 }}>
        <div style={{
          fontFamily: SERIF, fontSize: 80, fontWeight: 300,
          color: PALETTE.accent, letterSpacing: '-0.03em', lineHeight: 1,
        }}>{Math.min(5, Math.floor(clamp(t / 1.4, 0, 1) * 5))}</div>
        <div style={{
          fontFamily: SERIF, fontSize: 32, fontWeight: 300,
          color: PALETTE.creamDim, whiteSpace: 'nowrap',
        }}>/ 5 max</div>
      </div>
      <div style={{
        fontFamily: MONO, fontSize: 10, color: PALETTE.creamDim,
        letterSpacing: '0.08em', textTransform: 'uppercase', marginTop: 4,
      }}>Quiet 9p–7a · 7-day cooldown</div>
    </div>
  );
}

function PhoneDigest({ t }) {
  const days = [
    { d: 'Mon', n: 7,  s: 0.18 },
    { d: 'Tue', n: 4,  s: 0.10 },
    { d: 'Wed', n: 11, s: 0.30 },
    { d: 'Thu', n: 6,  s: 0.16 },
    { d: 'Fri', n: 9,  s: 0.24 },
    { d: 'Sat', n: 14, s: 0.40, hi: true },
    { d: 'Sun', n: 3,  s: 0.08 },
  ];
  return (
    <div style={{ padding: '24px' }}>
      <div style={{
        fontFamily: MONO, fontSize: 11, letterSpacing: '0.18em',
        color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 8,
      }}>Sunday digest · 8:00 pm</div>
      <div style={{
        fontFamily: SERIF, fontSize: 28, fontWeight: 300,
        color: PALETTE.cream, letterSpacing: '-0.02em', marginBottom: 6, lineHeight: 1.1,
      }}>Your week, in clusters.</div>
      <div style={{
        fontFamily: SANS, fontSize: 11, color: PALETTE.creamDim, marginBottom: 22,
      }}>54 captures · 3 themes</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {days.map((it, i) => {
          const o = clamp((t - 0.3 - i * 0.12) / 0.3, 0, 1);
          return (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, opacity: o }}>
              <div style={{
                fontFamily: MONO, fontSize: 10, color: PALETTE.creamDim,
                letterSpacing: '0.10em', textTransform: 'uppercase', width: 32,
              }}>{it.d}</div>
              <div style={{
                height: 14, width: it.s * 240 * ease(o),
                background: it.hi ? PALETTE.accent : PALETTE.rule, borderRadius: 3,
              }}/>
              <div style={{
                fontFamily: MONO, fontSize: 10,
                color: it.hi ? PALETTE.accent : PALETTE.creamDim,
                fontVariantNumeric: 'tabular-nums',
              }}>{it.n}</div>
              {it.hi && (
                <div style={{ fontFamily: SERIF, fontSize: 12, fontStyle: 'italic', color: PALETTE.cream }}>
                  → "pricing"
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ── Closing manifesto + lockup ──────────────────────────────────────────────
function ClosingManifesto() {
  return (
    <Sprite start={94.0} end={110.0}>
      {({ localTime }) => {
        const t = localTime;
        const o = clamp(t / 0.4, 0, 1) - clamp((t - 15.6) / 0.4, 0, 1);
        return (
          <div style={{ position: 'absolute', inset: 0, opacity: o }}>
            <WordReveal words={['Most', 'AI', 'assistants']} x={120} y={260}
              size={130} weight={300} color={PALETTE.creamDim} perWord={0.10} exitAt={14.4}/>
            <WordReveal words={['wait.']} x={120} y={400}
              size={130} weight={300} color={PALETTE.creamDim} perWord={0.10} delay={0.6} exitAt={14.4}/>
            <WordReveal words={['This', 'one', 'moves']} x={120} y={560}
              size={130} weight={400} color={PALETTE.cream} perWord={0.10} delay={1.6} exitAt={14.4}/>
            <WordReveal words={['with', 'you.']} x={120} y={700}
              size={130} weight={500} italic color={PALETTE.accent} perWord={0.12} delay={2.4} exitAt={14.4}/>
            <Caption text="// the bet, restated" sub="11" />
          </div>
        );
      }}
    </Sprite>
  );
}

function LogoLockup() {
  return (
    <Sprite start={110.0} end={120.0}>
      {({ localTime }) => {
        const t = clamp(localTime / 0.5, 0, 1);
        const exit = clamp((localTime - 9.2) / 0.4, 0, 1);
        const o = ease(t) * (1 - exit);
        return (
          <div style={{
            position: 'absolute', inset: 0, opacity: o,
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 24, marginBottom: 28 }}>
              <div style={{ width: 18, height: 18, borderRadius: '50%', background: PALETTE.accent, boxShadow: `0 0 30px ${PALETTE.accent}` }}/>
              <div style={{ fontFamily: SERIF, fontSize: 168, color: PALETTE.cream, letterSpacing: '-0.03em', lineHeight: 1 }}>Orbit</div>
            </div>
            <div style={{
              fontFamily: MONO, fontSize: 14, letterSpacing: '0.36em',
              color: PALETTE.creamDim, textTransform: 'uppercase', marginBottom: 60,
            }}>A personal AI agent · Android · 2026</div>
            <div style={{
              display: 'flex', gap: 32, alignItems: 'center',
              fontFamily: MONO, fontSize: 12, color: PALETTE.creamFaint,
              letterSpacing: '0.18em', textTransform: 'uppercase',
            }}>
              <span>Demo Day · May 22</span>
              <span style={{ width: 4, height: 4, borderRadius: '50%', background: PALETTE.creamFaint }}/>
              <span>orbit.app</span>
            </div>
          </div>
        );
      }}
    </Sprite>
  );
}

// ── VO Subtitles ────────────────────────────────────────────────────────────
// Spoken pace ~2.4 wps. Each cue lives at a global timecode and is sync'd to
// the actual scene boundary it covers. Cues never cross-fade or overlap.
const VO_CUES = [
  // Opening montage (0–13)
  { at: 1.5,  end: 4.4,  text: 'Every day, we capture pieces of who we want to become.' },
  { at: 4.8,  end: 9.4,  text: "A screenshot. A copy. A snippet. A friend's recommendation." },
  { at: 9.8,  end: 11.0, text: 'And then\u2026 we dump it somewhere.' },
  { at: 11.2, end: 12.8, text: "Months later, you can't even remember why you saved it." },

  // Capture act (13–22)
  { at: 13.4, end: 16.6, text: 'Most AI assistants wait for you to ask.' },
  { at: 16.9, end: 19.4, text: 'Orbit comes to you.' },
  { at: 19.7, end: 21.8, text: 'Highlight anything. From any app.' },

  // Diary (22–32)
  { at: 22.4, end: 26.6, text: 'Orbit asks what you meant \u2014 remind, inspire, follow up.' },
  { at: 26.9, end: 31.6, text: "Re-tag, add a layer, open the source. Nothing's locked." },

  // Cluster — Surface (32–36)
  { at: 32.4, end: 35.6, text: 'Friday night. Saturday morning. You came back to pricing four times.' },

  // Cluster — Summarize (36–46)
  { at: 36.4, end: 39.6, text: 'Orbit notices. Orbit speaks first.' },
  { at: 40.0, end: 45.6, text: 'Cited bullets, streamed in seconds \u2014 every claim traced to its capture.' },

  // Cluster — Audit (46–54)
  { at: 46.4, end: 49.6, text: 'Tap any citation.' },
  { at: 50.0, end: 53.6, text: 'Read the source, not the summary.' },

  // Cluster — Act (54–68)
  { at: 54.4, end: 58.4, text: 'Tomorrow at 10:30 is open.' },
  { at: 58.8, end: 62.6, text: 'Block it. The agent does the next thing.' },

  // Follow-up (68–82)
  { at: 68.4, end: 72.6, text: 'Save a name? Orbit knows it\u2019s a person.' },
  { at: 73.0, end: 77.6, text: 'When the moment\u2019s right, the follow-up\u2019s already drafted.' },
  { at: 78.0, end: 81.6, text: 'You hit send.' },

  // Rapid features (82–94)
  { at: 82.4, end: 85.6, text: 'Cloud by default. Or run it all on-device.' },
  { at: 86.4, end: 89.6, text: 'Five surfaces a day, max. Quiet hours, always.' },
  { at: 90.4, end: 93.6, text: 'Sunday at 8 \u2014 your week, distilled.' },

  // Closing manifesto (94–110)
  { at: 96.0,  end: 99.6,  text: 'Most AI waits for you to ask.' },
  { at: 100.0, end: 103.6, text: "We're building one that moves with you." },
  { at: 104.5, end: 108.5, text: 'A personal agent that\u2019s yours \u2014 quietly, always.' },
];

function Subtitles() {
  const time = useTime();
  const active = VO_CUES.find(c => time >= c.at && time < c.end);
  if (!active) return null;
  const localT = time - active.at;
  const dur = active.end - active.at;
  const o = clamp(localT / 0.25, 0, 1) - clamp((localT - (dur - 0.3)) / 0.3, 0, 1);
  return (
    <div data-vo style={{
      position: 'absolute', left: '50%', bottom: 110,
      transform: 'translateX(-50%)',
      maxWidth: 1280, padding: '14px 28px',
      background: 'rgba(8,11,20,0.78)',
      border: `1px solid ${PALETTE.rule}`,
      borderRadius: 8, backdropFilter: 'blur(12px)',
      fontFamily: SERIF, fontSize: 30, fontWeight: 400,
      color: PALETTE.cream, letterSpacing: '-0.005em',
      lineHeight: 1.3, textAlign: 'center',
      opacity: o, whiteSpace: 'nowrap',
    }}>{active.text}</div>
  );
}

// ── Persistent chrome ───────────────────────────────────────────────────────
function PersistentChrome() {
  const time = useTime();
  return (
    <>
      <div style={{ position: 'absolute', left: 80, right: 80, top: 56, height: 1, background: PALETTE.rule }}/>
      <div style={{
        position: 'absolute', left: 80, top: 32,
        fontFamily: MONO, fontSize: 13, letterSpacing: '0.24em',
        color: PALETTE.creamDim, textTransform: 'uppercase',
        display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: PALETTE.accent }}/>
        Orbit · Launch
      </div>
      <div style={{
        position: 'absolute', right: 80, top: 32,
        fontFamily: MONO, fontSize: 13, letterSpacing: '0.18em',
        color: PALETTE.creamDim, fontVariantNumeric: 'tabular-nums',
      }}>{time.toFixed(2).padStart(6, '0')} / 120.00</div>
      <div style={{ position: 'absolute', left: 80, right: 80, bottom: 40, height: 1, background: PALETTE.rule }}/>
    </>
  );
}

function StageBackground() {
  return (
    <>
      <div style={{
        position: 'absolute', inset: 0,
        background: `radial-gradient(ellipse at center, ${PALETTE.bg} 0%, ${PALETTE.bgDeep} 100%)`,
      }}/>
      <div style={{
        position: 'absolute', inset: 0, opacity: 0.03,
        backgroundImage: `linear-gradient(${PALETTE.cream} 1px, transparent 1px), linear-gradient(90deg, ${PALETTE.cream} 1px, transparent 1px)`,
        backgroundSize: '80px 80px',
      }}/>
    </>
  );
}

function Video() {
  return (
    <>
      <StageBackground />
      <OpeningMontage />
      <CaptureAct />
      <DiaryAct />
      <ClusterAct />
      <FollowUpAct />
      <RapidFeatures />
      <ClosingManifesto />
      <LogoLockup />
      <Subtitles />
      <PersistentChrome />
    </>
  );
}

Object.assign(window, { Video, Subtitles, PALETTE, SERIF, SANS, MONO });
