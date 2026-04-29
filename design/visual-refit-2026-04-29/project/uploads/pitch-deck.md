# Orbit — Pitch Deck (Demo Day variant, 12 slides)

*Use this for: Demo Day stage May 22, post-demo investor 1:1s, the deck you send when someone says "send me what you've got."*

*Slide notes are the words you actually say. Build the visual to match — see the visual cue at the top of each slide.*

---

## Slide 1 — Title

**Visual:** Orbit wordmark (Quiet Almanac aesthetic — quiet, paged, no chrome). Subtitle: "A personal AI agent for Android." Bottom-right: your name + canopybyfounders.com Demo Day badge.

**Say (10 sec):**
> "I'm Richel. I'm building Orbit — a personal AI agent for Android."

---

## Slide 2 — The graveyard

**Visual:** Phone home screen with an absurd badge: "2,047 screenshots." Faded copy: "Last opened: never."

**Say (20 sec):**
> "Every modern phone is a graveyard of intentions. Two thousand screenshots most users never reopen. Notes apps with hundreds of items engaged with less than 2% of the time. The structural failure isn't the user. It's that the phone offers infinite saving and zero surfacing."

---

## Slide 3 — The insight

**Visual:** Two sentences, large type, alone on the slide.
> Most AI waits for you to ask.
> The right ones come to you.

**Say (15 sec):**
> "Every other AI tool waits — ChatGPT, Notion AI, Apple Intelligence, Mem. They wait for you to know what to ask. The bet I'm making is that the user often doesn't know what to ask. The right surface is one the agent initiates."

---

## Slide 4 — The wedge

**Visual:** A single Orbit cluster card mockup at hi-res. Card text:
> *"You came back to pricing four times this weekend across Twitter, Safari, and a podcast about pre-seed valuations. Want me to pull it together?"*
> [Summarize] [Open all] [Dismiss]

**Say (20 sec):**
> "This is the moment Orbit owns. Saturday morning you open the app. The agent has noticed you've been circling something across apps all weekend — pricing, in this case. It surfaces it, asks if you want to act. One tap. We call this a research-session cluster."

---

## Slide 5 — Live demo (or recorded fallback)

**Visual:** Phone connected to projector. Orbit open. Pre-staged founder-mode cluster card visible.

**Say (60-90 sec — see `demo-script.md` for the canonical demo flow):**
> [DEMO]
> "[Tap Summarize] In two seconds Orbit produces three cited bullets. Every claim sourced to a capture I made. [Tap a citation] You can audit every fact back to its source. [Calendar suggestion appears] 'You have 10:30 to 12:00 free tomorrow. Block it for pricing?' [Tap] Done."

---

## Slide 6 — How it works

**Visual:** Three columns.
> CAPTURE → INTENT → SURFACE
>
> *(across apps)* → *(sealed at save)* → *(when you can act)*

**Say (20 sec):**
> "Three pieces. Capture — Orbit catches what you save across every app. Intent — at the moment of save you tag what you meant: remind me, inspiration, in orbit. That intent is sealed and immutable. Surface — Orbit clusters captures across apps and brings the right things back when you can act on them."

---

## Slide 7 — Why now

**Visual:** Three icons, one line each.
> Cheap cloud LLMs (~$1.70 per user per month)
> On-device AI (Gemini Nano 4, capable hardware)
> AppFunctions API (Calendar, Tasks, Notes — agentic actions)

**Say (15 sec):**
> "Three things just shipped that make this possible in 2026. Cloud LLMs got cheap enough that intent classification at consumer scale fits inside a $12 subscription with 86% margin. On-device Gemini Nano makes the privacy toggle credible. And AppFunctions just shipped — Calendar, Tasks, Notes are agentically callable."

---

## Slide 8 — Why not the obvious incumbents

**Visual:** Four logos, four bullets.
> Apple Intelligence — sandboxed by app. Cannot cross apps.
> Google AI — ad-conflicted. Won't read your Substack.
> Notion — explicit-capture. You still organize by hand.
> ChatGPT — reactive. Waits for you to know what to ask.

**Say (20 sec):**
> "Apple can't see my Twitter. Google won't read my Substack. Notion isn't watching my phone. ChatGPT waits for me to know what to ask. The cross-app + privacy + proactive combination is structurally only ours."

---

## Slide 9 — The moat

**Visual:** Three layers stacked.
> Data: IntentEnvelopes — intent paired at save time, immutable, compounds with use.
> Behavior: Orbit learns when you act vs dismiss; the agent gets quieter and more right.
> Partner: voice, rhythm, citations, dismissal cooldowns. The relationship is the moat.

**Say (25 sec):**
> "The moat is three layers. The data — IntentEnvelopes pair user intent with capture at save time, sealed and immutable. Incumbents can't synthesize this retroactively. The behavior layer — Orbit learns when you act and when you dismiss; it gets quieter and more right. The partner layer — the rhythm, the voice, the citations on every claim. It's not just memory. It's a relationship."

---

## Slide 10 — Numbers

**Visual:** A small table.

| | |
|---|---|
| Cost per user (100 users) | ~$1.70/mo |
| Gross margin at $12/mo | 86% |
| Capture-to-cluster precision target | ≥0.6 (May 4 gate) |
| Capture-to-acknowledgment latency | <10s |
| Demo length | 90 seconds |
| Constitutional principles | 16 |

**Say (15 sec):**
> "The numbers. Cost per user is $1.70 a month at 100 users. 86% gross margin at $12. Capture lands in under 10 seconds. The principles I designed against — 16 of them, including bounded observation, partner-not-parent, default privacy."

---

## Slide 11 — Where we are / what's next

**Visual:** A timeline.
> Today (Apr 29) — Cloud pivot complete. Production live.
> May 22 — Demo Day. v1 ships to alpha cohort.
> Q3 2026 — Closed beta on Play Store. $12/mo Pro tier.
> Q1 2027 — iOS, voice agent, watch app.
> 18-24 months — 100K paying users. $18M ARR.

**Say (20 sec):**
> "Today, the cloud backend is live and verified end-to-end. May 22, v1 ships to the alpha cohort and we demo. Q3 2026, closed beta on Play Store and the $12 Pro tier opens. Q1 2027, iOS and voice. 18 to 24 months out, the path to 100K paying users — $18M ARR — is sized for venture economics."

---

## Slide 12 — Ask

**Visual:** Two columns, no extra prose.

**Right now:**
- 5 alpha users actively using by May 15
- Warm intros to consumer-AI seed funds
- Product feedback from anyone in the room

**Soon:**
- $1.5M pre-seed round
- 1-2 senior engineers post-Demo
- Design partner conversations with productivity tool teams

**Say (20 sec — close):**
> "Right now I'm looking for five alpha users actively using by May 15, warm intros to consumer-AI seed funds, and product feedback from anyone in this room. After Demo Day I'm raising a $1.5M pre-seed.
>
> Most AI assistants wait. This one moves with you.
>
> Thank you."

---

## Backup slides (don't show unless asked)

### B1 — The pivot (don't lead with this; have it ready)
**Visual:** Two boxes side by side.
> Was: Local-first supremacy. On-device only.
> Now: Default privacy. Cloud-default with on-device toggle.

**Say:**
> "We pivoted before launch. The local-first marketing wasn't shipped externally. Cloud-default unlocks 10x the addressable market and the on-device toggle keeps the privacy promise for users who want zero cloud calls. Local mode is the future, not the past — Nano is broadening to all flagship Android. We built for that future."

### B2 — Tech stack (for the technical investor)
> Supabase Pro — Postgres + pgvector + auth + realtime
> Vercel AI Gateway — routes Sonnet 4.6, Haiku 4.5, OpenAI embeddings
> 4 Android processes — :ui :capture :ml :net (only :net has INTERNET)
> Letta rejected — wrong shape for structured intent data

### B3 — Constitution (the 6 load-bearing principles)
> I. Default Privacy
> II. Effortless Capture
> III. Intent Before Artifact (the moat)
> XII. Provenance Or It Didn't Happen
> XIII. Partner, Not Parent
> XIV. Bounded Observation

### B4 — Risk register (for the diligence-minded investor)
> Apple Intelligence on iOS at WWDC 2026 → sandboxed, cannot cross-app
> Google ships partner-noticer → 12-18 months out, by then we have data + behavior moat
> Cloud LLM cost at scale → 80-95% gross at $12/mo, healthy SaaS
> Solo-founder bandwidth → strangler-fig migration, locked decisions, hard cuts

### B5 — Roadmap detail
| | |
|---|---|
| v1 (May 22) | Research-session clusters, Summarize, Calendar block, Sunday digest, sideload to alpha |
| v1.1 (Q3 2026) | Web read-only diary, Chrome extension, $12/mo Pro tier |
| v1.5 (Q4 2026) | BYOK, screen-time partner toggle, multi-cluster types |
| v2 (Q1 2027) | iOS, voice agent, watch app, trust-graduated autonomous actions |

---

## Stage delivery notes

- **8 minutes total.** Slides 1-4: 75 sec. Demo: 90 sec. Slides 6-12: 195 sec. Total: 6 min, 30 sec stage time. Buffer for stage walks + applause.
- **Stand stage left if there's a podium.** Demo phone in your dominant hand. Mirror to projector.
- **Resist filling silence.** Slide 4-to-demo transition has a 4-second beat where you tap the card. Don't talk over the tap.
- **Eye contact pattern: 1/3 left, 1/3 center, 1/3 right.** Pick one face per third.
- **Slow down on slide 9 (the moat).** Investors are listening for defensibility on this slide.
- **Land slide 12 hard.** Stop talking. Wait for the room.
