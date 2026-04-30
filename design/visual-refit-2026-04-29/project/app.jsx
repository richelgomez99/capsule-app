// app.jsx — root App; loaded last so window.useTweaks etc are defined.

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "#e8b06a",
  "showCaptions": true,
  "showChrome": true,
  "showSubtitles": true,
  "duration": 120
}/*EDITMODE-END*/;

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);

  React.useEffect(() => {
    PALETTE.accent = t.accent;
    PALETTE.accentDim = t.accent + '2e';
  }, [t.accent]);

  const [, forceUpdate] = React.useReducer(x => x + 1, 0);
  React.useEffect(() => { forceUpdate(); }, [t.accent, t.showCaptions, t.showChrome, t.showSubtitles]);

  return (
    <>
      <Stage
        width={1920}
        height={1080}
        duration={t.duration}
        background={PALETTE.bgDeep}
        loop={true}
        autoplay={true}
        persistKey="orbit-launch"
      >
        <ConditionalVideo showCaptions={t.showCaptions} showChrome={t.showChrome} showSubtitles={t.showSubtitles} />
      </Stage>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Brand" />
        <TweakColor
          label="Accent"
          value={t.accent}
          onChange={(v) => setTweak('accent', v)}
        />
        <TweakSection label="Layout" />
        <TweakToggle
          label="Lower-third captions"
          value={t.showCaptions}
          onChange={(v) => setTweak('showCaptions', v)}
        />
        <TweakToggle
          label="Frame chrome (rules + counter)"
          value={t.showChrome}
          onChange={(v) => setTweak('showChrome', v)}
        />
        <TweakToggle
          label="VO subtitles"
          value={t.showSubtitles}
          onChange={(v) => setTweak('showSubtitles', v)}
        />
      </TweaksPanel>
    </>
  );
}

function ConditionalVideo({ showCaptions, showChrome, showSubtitles }) {
  return (
    <div style={{ position: 'absolute', inset: 0 }} className={`${showCaptions ? '' : 'no-captions'} ${showSubtitles ? '' : 'no-vo'}`}>
      <style>{`.no-captions [data-cap]{display:none} .no-vo [data-vo]{display:none}`}</style>
      <Video />
      {!showChrome && (
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <div style={{ position:'absolute', left:0, right:0, top:0, height:80, background:PALETTE.bgDeep }}/>
          <div style={{ position:'absolute', left:0, right:0, bottom:0, height:60, background:PALETTE.bgDeep }}/>
        </div>
      )}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App/>);
