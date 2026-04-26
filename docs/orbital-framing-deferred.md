# Orbital Framing — Deferred Brand-Voice Decision

**Captured**: 2026-04-26 (during /office-hours session that produced spec 012)
**Status**: Deferred — decision pending user testing during alpha (April 29 – May 19)
**Decision deadline**: EOD May 19 (3 days before Demo Day, per spec 012 SC-012-6)
**Related**: spec 012 (Resolution Semantics) OQ-012-001

---

## Summary of the deferral

During the office-hours session that produced spec 012 (Resolution Semantics), a strong alternative brand-voice framing emerged: instead of "resolution / sealed / abandoned / snoozed" as the user-facing vocabulary, frame the same mechanism as "things in orbit" — bring closer / let drift / land / let escape.

The metaphor maps the product model 1-to-1 to orbital mechanics. It's the strongest narrative differentiator we identified. **It's also poetic enough to risk twee.** The user (founder, Richel Gomez) chose to defer the decision: ship v1 with resolution language, observe how alpha users naturally describe the feature, decide the brand-voice direction post-evidence rather than pre-evidence.

This document preserves the brainstorm so the option doesn't get lost.

---

## The metaphor mapping (why it works)

Real orbital mechanics:
- Objects in stable orbit persist without falling away or crashing.
- Different orbits = different distances = different relevance.
- Geostationary, low orbit, highly elliptical, escape trajectory — each is a distinct attention pattern.
- Orbits decay without active station-keeping.
- Multiple objects in similar orbits can fly in formation.
- Things in orbit are present, but not always seen.

The product model maps directly:

| Orbital mechanic | Orbit (the app) |
|---|---|
| Object in orbit | IntentEnvelope |
| Stable orbit | Saved state |
| Decaying orbit | Graduated visual fade (60% at 14d, 40% at 30d, archive at 60d) |
| Station-keeping | Resolution acts |
| Geostationary | Reference intent — always there |
| Highly elliptical | Time-bound items — come close at a specific moment |
| Re-entry / landing | Resolved — capture closes its loop |
| Escape trajectory | Abandoned — leaves with reason |
| Formation flying | A cluster |
| Out of sight, not out of system | Auto-archived 60+ day items |

The state model maps to orbital states:
- **Saved** → fresh into low orbit
- **Acknowledged** → orbit decaying slightly
- **In motion** → spiraling inward
- **Resolved** → re-entry
- **Snoozed** → highly elliptical (scheduled return)
- **Abandoned** → escape trajectory

---

## UX vocabulary mapping (if adopted)

| Resolution language | Orbital language |
|---|---|
| Save | (passive — "this is in orbit") |
| Snooze | Send out (push to higher orbit, scheduled return) |
| Resolve / mark done | Bring home (or land) |
| Abandon / dismiss | Let drift (or let escape with reason) |
| Re-encounter | Pulled in by gravity |
| Cluster formed | Formation |
| Auto-archive | Deep orbit |

---

## Constitutional reframe (if adopted)

Instead of Principle XIII *"Resolution Closes the Loop,"* the orbital version:

> **XIII. Things You Save Are in Orbit, Not in a Folder.** Captures are objects in your attention's orbit, not files in a drawer. They have natural distance — close (recent, surfaced), far (older, drifting), in formation (clustered), or escaped (abandoned with reason). Orbit's job is to keep things in the right orbit, not to demand action. Some you'll bring home to land. Some you'll let drift. Some return on their own when factors align. Orbit honors all of these — capture is a beginning, resolution is a return, drift is allowed.

Subsumes:
- Resolution mechanic (landing)
- Anti-guilt commitment (drift is allowed)
- Cluster engine (formation flying)
- State model (orbits at different distances)
- Graduated visual fade (decay)

One principle, six implications.

---

## Demo Day pitch (if adopted)

> *"Apple keeps your screenshots in a folder. Pocket keeps your articles in a folder. Notion keeps your notes in a folder. Orbit keeps things in orbit — close enough to act on when the moment's right, far enough not to nag, always there when you need them."*

Cluster-suggestion card text (if adopted):
- *"Four captures are flying in formation. Want to bring them closer?"* (instead of *"You had a research session. Want a summary?"*)
- *"You brought the flight home."* (instead of *"You added the flight to your calendar."*)
- *"This recipe has been drifting 23 days. Bring closer, or let go?"* (instead of *"This recipe has been saved 23 days. Want to abandon?"*)

Italic resolution line:
- *"landed · added to calendar · Wed 4:42P"* (instead of *"sealed · added to calendar · Wed 4:42P"*)

Marketing taglines:
- *"Orbit. Because the things that matter to you should orbit you, not drift away."*
- *"Orbit. Capture is a beginning. Resolution is a return."*

---

## Sunday review as orbital map (if adopted)

A small typographic visualization at the top of the Sunday digest:
- **Inner ring**: things you brought home this week (resolved)
- **Middle ring**: things still close (active, recent)
- **Outer ring**: things drifting (older, unresolved)
- **Beyond**: things that escaped (abandoned with reason)

Concentric rings, dots representing envelopes, simple typographic rendering (no canvas chart libraries). Scannable in 2 seconds. The Demo Day closer.

---

## Why the metaphor justifies the architecture

Every constitutional principle finds a stronger expression in the orbital frame:

- **Why local-first?** Things in your orbit belong to you. A cloud-first app means your captures are in Apple's orbit or Google's orbit, not yours.
- **Why no notifications?** Things in stable orbit don't beg for attention.
- **Why intent at save?** Different intents = different orbital parameters.
- **Why the four wax seals?** Four canonical orbit types (▲ want close soon, ◆ might bring close later, ● geostationary view, ○ unsure).
- **Why on-device AI?** The agent is your orbital station-keeping crew. Reports only to you.

The metaphor isn't a brand layer. It's the product's actual physics.

---

## The risks (why deferred)

1. **Twee risk**: Knowledge workers (B persona) are pragmatic. Things 3 and Linear succeed partly because they don't make you learn a vocabulary. "Bring home" might feel poetic in a brand moment and twee in a button at 7am.
2. **Cognitive load**: A user who has to translate "let drift" → "abandon" mentally is paying friction every time. Plain words have lower friction.
3. **Adoption ceiling**: Strong-metaphor products (Arc browser with "spaces") tend to be polarizing — beloved by a small cohort, off-putting to mainstream. Orbit's TAM might be larger if the language is plain.
4. **Recoverable failure mode**: If we ship orbital language and it doesn't land, retrofitting to plain language is expensive. Ship plain, retrofit to orbital if signal supports it — cheaper.

---

## The decision criteria

By EOD May 19, decide based on alpha-user signal:

- **Adopt orbital framing** if 3+ of 5 alpha users naturally use orbital language (or analogous metaphors — gravity, drift, formation, landing) when describing the product unprompted in their week-3 check-ins.
- **Stay with resolution framing** if alpha users describe the product in pragmatic terms (save, done, finish, organize). Their words are the right product words.
- **Hybrid (the most likely outcome)**: keep daily UI plain, lean on orbital language at brand-voice moments only — onboarding, Sunday review header, marketing copy, Demo Day pitch. The split commits the metaphor where it earns hardest without imposing it where it might grate.

---

## Hybrid implementation blueprint (if hybrid wins)

If user testing supports a hybrid:

| Surface | Voice |
|---|---|
| Onboarding screens | Orbital — frame the product model in 2-3 sentences |
| Sunday review header | Orbital — *"Your week in orbit"* |
| Marketing site | Orbital — taglines + product narrative |
| App Store copy | Orbital — first paragraph; pragmatic features list |
| Daily UI buttons / chips | Pragmatic — *Save*, *Done*, *Snoozed*, *Dismiss* |
| Settings labels | Pragmatic |
| Notifications | None — but if any future flavor emerges, pragmatic |
| Spec, constitution, engineering language | Orbital — internal coherence pays off |
| Demo Day pitch | Orbital — the metaphor IS the pitch |
| Founders' updates / Twitter / public posts | Orbital — brand voice |

---

## Resources for the decision

- This document (the brainstorm)
- Spec 012 (the resolution mechanism — vocabulary-agnostic)
- Office-hours design doc at `~/.gstack/projects/richelgomez99-capsule-app/richelgomez-spec-002-intent-envelope-and-diary-design-20260425-235407.md`
- Alpha user week-3 check-in transcripts (post-May 13)
- Any unsolicited language patterns observed in alpha-user behavior or feedback

---

*The metaphor is real. The question is whether it serves the product or the founder's affection for it. Test before commit.*
