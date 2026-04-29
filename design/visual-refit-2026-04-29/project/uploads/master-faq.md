# Master FAQ — Orbit

*The 30 questions you'll get most. Memorize the first 8. The rest are reference.*

*Voice: direct, observational, fragments OK, no marketing superlatives, "agent" not "assistant," "you" not "users."*

---

## The first 8 (memorize these cold)

### 1. What is Orbit?

> A personal AI agent for Android. It catches what you save across apps — screenshot, copy, share sheet, browser — and surfaces patterns proactively. Saturday morning the agent notices you've been circling pricing across Twitter, Safari, and a podcast all weekend, and asks if you want to pull it together. One tap, three cited bullets, calendar block proposed. Most AI assistants wait. This one moves with you.

### 2. How is it different from ChatGPT?

> ChatGPT waits for you to type. The user often doesn't know what to ask. Orbit moves first. It captures across your apps, knows what you intended at the moment of save, and brings the right things back. Different jobs.

### 3. How is it different from Apple Intelligence?

> Apple Intelligence is sandboxed by app. Structurally cannot see across apps. Orbit's wedge is cross-app surfacing — that's the gap. Plus it's Android-first; Android is where the agent surface actually lives (overlay, background clipboard, share sheet). iOS gets a read-only diary in v1.5.

### 4. How is it different from Notion / Mem / Reflect / Roam?

> Those are explicit-capture knowledge management. You still organize by hand. Orbit captures consensually across apps and surfaces without you organizing anything. Different category. They're notes apps with AI on top. Orbit is an agent with capture underneath.

### 5. How is it different from Rewind / Limitless?

> Always-on lifelogging. Wrong consent model — they record everything passively. Orbit is consensual capture. Every save is an active gesture. And those products had bad outcomes. Limitless was acquired by Meta and sunset December 2025. Humane bricked. Rabbit at 5% retention. The pendant category cleared cultural runway specifically for software-only, phone-native capture.

### 6. Why Android?

> The agent surface lives there. Floating bubble overlay, background clipboard observation, background screenshot processing, share-sheet capture from any app. iOS can't do these. iOS gets a read-only diary in v1.5; the live agent surface needs Android. Plus Pixel-class on-device inference makes the privacy toggle credible.

### 7. How do you make money?

> Free tier with capped AI features and unlimited capture. Paid tier at $12/mo opens Q3 2026 — Orbit-managed cloud, premium cluster types, voice agent, third-party integrations. Cost per user is about $1.70/mo at 100 users; that's an 86% gross margin. BYOK at v1.5 ($5/mo with your own key). Never selling data, ever.

### 8. What's your timeline?

> Today: production cloud backend live, end-to-end verified. May 22 Demo Day, v1 ships to alpha cohort. Q3 2026 closed beta on Play Store, $12/mo Pro tier opens. Q1 2027 iOS, voice, watch. 18 to 24 months — 100K paying users at $15 ARPU, $18M ARR.

---

## Product depth (9-15)

### 9. What does "capture across apps" actually mean technically?

> Four capture paths. Floating bubble overlay (always available). Share sheet from any app. Clipboard observation (foreground). Screenshot pipeline. Each capture creates an IntentEnvelope — content + intent + app category + context — sealed and immutable at save. No Accessibility Service (Play Store policy closed that in January 2026).

### 10. What's a "research session cluster"?

> A pattern Orbit detects: at least 3 captures, across at least 2 distinct domains, within a 4-hour window, with sufficient semantic similarity (cosine ≥ 0.7) and topic overlap (token-jaccard ≥ 0.3). When the threshold trips, Orbit surfaces a cluster card. Saturday morning founder-mode: four captures across Twitter, Safari, Apple Podcasts about pricing — that's a cluster. The thresholds are tight on purpose — better to under-surface than over-surface.

### 11. What if Orbit gets it wrong? Hallucinates a citation?

> Citation is enforced at the database layer, not the prompt layer. The summary's cited envelope-ID must exist in the cluster's `cluster_member` table — Postgres CHECK constraint. If the model hallucinates an ID, the database rejects the row. Strictly stronger than prompting hope. That's Constitutional Principle XII: Provenance Or It Didn't Happen.

### 12. Is there a privacy story?

> Yes. Constitutional. Default Privacy is Principle I. Cloud LLM by default with strong terms — no training on user content, no human review, encrypted in transit and at rest, deletable on request, enterprise terms with OpenAI/Anthropic. Plus a Settings toggle: "Use local AI" runs on-device Gemini Nano on capable hardware, zero cloud calls. The forget-everything button is real and works.

### 13. What's the on-device part vs cloud part?

> Capture, IntentEnvelope storage, the daily diary, the bubble UI, all the actions — all on-device. The cloud is the LLM provider for summarization, intent classification, and embeddings. The toggle moves the LLM to on-device Nano; nothing else changes. Local mode is the future, not the past — Nano is broadening to all flagship Android. We built for that future.

### 14. Why isn't this just a feature inside Notion / Apple / Google?

> Each of them has structural blockers. Apple is sandboxed by app. Google is ad-conflicted — they won't read your Substack. Notion is explicit-capture-by-design — adding cross-app capture would break their model. ChatGPT could add memory profiles, but they're a chat product; the surface is reactive. The cross-app + privacy + proactive combination is structurally only ours.

### 15. How does Orbit decide when to surface vs stay quiet?

> Hard ceiling: 5 proactive surfaces per 24 hours. Default cadence: morning card, evening reflection, Sunday digest, plus event-triggered cluster cards. Quiet hours 9pm-7am default. Per-pattern dismissal cooldown: 7 days. If you dismiss a notification category 5 times in a row, the agent suggests turning it off. Constitutional principle V: Under-Deliver on Noise.

---

## Business / market (16-22)

### 16. What's the TAM?

> Top-down: 600M Android knowledge workers globally. SAM around 60M reachable Android KWs willing to pay for personal AI. At $12/mo, that's an $8.6B/yr SAM inside an ~$86B/yr TAM. Anchor cohort: ~25M ADHD-diagnosed Android knowledge workers with documented productivity loss. Comparables: ChatGPT Plus is 10M+ at $20, Notion is $600M ARR. The category is real.

### 17. Who's the alpha customer?

> ADHD-tagged knowledge workers and productivity-tool refugees. High pain density, repeat memory-tool buyers, loud evangelists. First 12 cold DMs going out this week from my closest network — Canopy peers and ADHD-tagged folks I already know. Closest network for fastest cold-start. Adjacent professional in 6-12 months, mainstream in 12-24 months.

### 18. What's the moat?

> Three layers. Data: IntentEnvelopes — intent paired with capture at save time, sealed and append-only. Compounds with use. Incumbents can't synthesize this retroactively. Behavior: the agent learns when you act vs dismiss; gets quieter and more right. Partner relationship: rhythm, voice, citations, dismissal cooldowns, opt-in notifications. Not memory. Relationship.

### 19. What's your acquisition strategy?

> Build-in-public on Twitter and LinkedIn through Demo Day and beyond. Cold DMs to closest network for alpha. Demo Day at Canopy by Founders, Inc. for warm investor + peer signal. Q3 2026 launch: founder-led content, podcasts, ADHD-creator partnerships, Reddit r/productivity adjacent. Distribution is a question for Q4 2026 — for now, the work is product velocity.

### 20. How big does this need to be to matter?

> 100K paying users at $15 ARPU is $18M ARR — Series-B-credible. SOM ceiling at category-leader scale is $144-288M ARR (1-2M paying users in 5 years). VC math: 100K paying users at $12/mo = $14.4M ARR, healthy SaaS with 86% gross. Big enough.

### 21. What if Apple ships a competing partner-noticer?

> They might in 12-18 months. By then Orbit has the data layer (intent-paired captures), behavior data (when do you act vs dismiss), and brand. Apple Intelligence is also iOS-only and sandboxed — even if they ship something similar, it won't cross apps. We compete with our cross-app surface, not against them.

### 22. What if Google ships it?

> Google has Magic Cue on Pixel — pattern-shallow by surveillance-optics design. They'll ship something Orbit-adjacent eventually. Two answers. (a) The cross-app + privacy + agent-rhythm combination is harder than it looks; large-company politics around the ad business will keep the privacy story compromised. (b) By the time they ship, our beachhead has converted; we're not fighting on Pixel-default features, we're fighting on already-installed-and-loved.

---

## Founder / execution (23-30)

### 23. Are you solo? Why solo?

> Solo for v1. Strangler-fig migration, locked decisions, hard cuts in scope let me ship in 24 days. After Demo Day I'm raising and hiring 1-2 senior engineers. The product before money is a feature, not a bug — every line in the codebase exists because I wrote it, every decision is mine to defend.

### 24. What's your background?

> [Fill in: 2-3 sentences. Previous role, specific skill stack, why this product.]

### 25. Have you talked to users?

> Honestly, the cold DM cohort is going out this week. The pitch through May 5 rests on theory — and that self-honesty is itself the point. By Demo Day we'll have 5+ alpha users actively using ≥4 of 7 days for ≥7 days. That's the gate the product earned, not pre-flight bravado about user discovery.

### 26. What if you don't get the alphas?

> Two fallbacks. (a) Expand the cohort from 12 closest-network DMs to 20 with cooler-network outreach by May 1. (b) "I am the user" pitch — 100 captures over 4 weeks of my own life as the credibility doc. Both fallbacks are pre-written by May 1. The Demo Day pitch downgrades but doesn't break.

### 27. What does the demo break look like?

> The recorded fallback is cut by May 18 against pinned firmware. Air-gapped backup phone in the bag. If the live demo dies on stage I switch to the recording and re-narrate calmly. The single recovery line: "Live demos sometimes do this. Let me show you the recorded version — same flow, last week." No apologies. No explanations.

### 28. How are you funded?

> Bootstrapped to Demo Day. Post-Demo I'm raising $1.5M pre-seed. Cost per user is $1.70/mo at 100 users; runway extends with paying users at Q3 launch even if the round takes longer.

### 29. What's the biggest risk?

> Two real ones. (1) Source-attribution drift — Orbit hallucinates a citation outside the cluster pool. Pitch-killing bug. Mitigation: structured-output schema + Postgres CHECK + sanitizer + regression corpus. (2) "Smart inbox feel" — the existential brand risk. If Orbit feels like fancy bookmarks instead of a partner, the product has failed. Mitigation: morning/evening cards must feel partner-like, action proposals not just summaries, voice consistency, the partner-not-inbox proof on stage.

### 30. Why now? Why you?

> Three forces converging. Cloud LLMs got cheap enough. On-device AI is real. AppFunctions just shipped. The cross-app + privacy + proactive combination is structurally only ours.
>
> Why me: I'm the user. Productivity-tool refugee, builder who saves things across apps and loses them, founder who comes back to the same thought four times in a weekend. I shipped the capture overlay April 18 and have been using it daily since. The diary in my phone has my last 100+ captures. The first real research-session cluster surfaced two weeks ago. That moment is the entire pitch.

---

## Where to go for more depth

| Topic | File |
|---|---|
| Specific objection responses | `objection-handling.md` |
| Numbers to memorize | `09-confidence/numbers-to-memorize.md` |
| Competitor-specific positioning | `04-market-and-customer/competitive-landscape.md` |
| TAM math | `04-market-and-customer/market-sizing.md` |
| Investor target list | `06-investor/investor-target-list.md` |
