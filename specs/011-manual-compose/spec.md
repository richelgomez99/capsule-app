# Feature Specification: Manual Envelope Composition — "Jot a Note"

**Feature Branch**: `011-manual-compose`
**Created**: 2026-04-21
**Status**: Draft
**Input**: User feedback — "shouldn't a user be able to add or create an envelope in app? I don't see that feature. Also proactively, a user may want to go back to yesterday and seal things I forgot to."
**Governing documents**: [.specify/memory/constitution.md](.specify/memory/constitution.md), [.specify/memory/design.md](.specify/memory/design.md)
**Depends on**: spec 002 (IntentEnvelope schema + seal transaction + diary surface).
**Amends**: constitution.md Principle II (Effortless Capture, Any Path) — adds *deliberate composition* as a first-class capture path alongside passive observation.

---

## Why This Spec Exists

Orbit's v1 capture model is strictly passive — clipboard-observed, screenshot-observed, sealed within 2 seconds. This embodies Principle II ("A user who hesitates before saving has already lost the capture"). But the principle addresses *reactive* capture — what a user does when the thought is already in their head mid-app-use. It does not address two equally real scenarios:

1. **Deliberate composition**: "I want to jot down: 'ask Sara if she's free for lunch Thursday.'" The user opens Orbit intentionally. No clipboard, no screenshot — the thought is the artifact.
2. **Retroactive backfill**: "Yesterday I saw a book at the bookstore and forgot to capture it. I want to add a note to yesterday's page now." The diary's daybook metaphor explicitly invites this — a physical notebook lets you scribble in yesterday's page.

Knowledge workers (our target) expect both. Every journaling app, note app, and to-do app supports manual composition. Not having it is the product hole that users hit within a day of install.

This spec adds manual composition **without** undermining Principle II's passive-capture stance: manual is a second path, not a replacement. The bubble + screenshot observer remain the primary "effortless capture" surface for the common case.

## Non-Goals

- No rich-text editor. Composition is plaintext + optional URL + intent chip.
- No templates, no slash-commands, no tagging. v1 keeps it as close to "a blank ruled line in a daybook" as possible.
- No voice dictation in this spec — that's spec 008 (Orbit Agent) territory.
- No backdated entries before the install date. The diary's history begins at install.
- No reminders, no scheduling. A manual envelope is a capture, not a task. (Task semantics live in spec 003 Orbit Actions.)

---

## User Scenarios & Testing

### User Story 1 — Compose an envelope from inside the diary (Priority: P1)

As a user in the diary, I tap an unobtrusive affordance at the bottom-right (typographic `＋` glyph in a subtle wax-seal-style round, per design.md §4), a compose sheet slides up, I type a short note, optionally paste a URL, tap the wax seal of my choice (▲ WANT_IT / ◆ FOR_LATER / ● REFERENCE), tap Seal, and the envelope appears at the top of today's page under the appropriate thread.

**Why P1**: Core feature. Without it, Orbit feels incomplete to anyone used to any journaling app.

**Acceptance**:
1. **Given** the diary is open, **When** I tap the `＋` affordance, **Then** a compose sheet appears covering the lower 60% of the screen, focus is in the text field, and the keyboard auto-opens.
2. **Given** I've typed ≥1 character, **When** I tap Seal with no intent chosen, **Then** the envelope is sealed with intent `AMBIGUOUS` (matching the auto-dismiss default from FR-003 spec 002).
3. **Given** I've tapped an intent wax seal before Seal, **Then** the envelope is sealed with that intent directly, no ambiguity.
4. **Given** the compose sheet is open, **When** I tap outside the sheet OR swipe it down, **Then** if the text field is empty the sheet dismisses without creating anything; if non-empty a confirmation "Discard note?" is shown.
5. **Given** a URL is pasted into the text field, **When** I tap Seal, **Then** the URL is recognized as a URL capture (triggering continuation hydration per spec 002), and the surrounding typed text is stored as the `userNote` field.
6. **Given** the sheet is open and a sensitivity-scrubber rule matches (SSN, credit card) per spec 002 FR-002a, **Then** on Seal the envelope goes through the same scrub pipeline as clipboard captures — the composition path does NOT bypass redaction.

### User Story 2 — Backfill into yesterday (or any past day in history) (P2)

As a user realizing I forgot to capture something, I navigate to an older day via the diary's day pager, and within that day's header area I see a subtle affordance "＋ Jot a note on this day" (only visible when viewing a non-today page). I tap it, compose a note, and the envelope is sealed **with today's timestamp** but **dated to the page I was on** for diary display purposes.

**Why P2**: High-value for trust and retention; not blocking v1 release. P2 lets us ship User Story 1 alone if time pressures demand it.

**Acceptance**:
1. **Given** I am viewing 2026-04-18 in the pager, **When** I tap the day-scoped compose affordance, **Then** a compose sheet opens with a subtle header "Adding to April 18" so I understand the temporal context.
2. **Given** I compose a backfill envelope on 2026-04-18, **When** it is sealed, **Then** `envelope.createdAt = now()` (true wall-clock honesty), `envelope.dayLocal = 2026-04-18` (diary placement), and a NEW field `envelope.backfillFor = 2026-04-18` is set so the UI can annotate the card with a small "Added on April 21" note.
3. **Given** the backfill envelope is on the older page, **Then** the day-header generator (spec 002 T048) RE-runs for that day so the paragraph can reflect the new entry on next open.
4. **Given** backfill envelopes exist, **When** an audit-log entry is written for the seal, **Then** the audit row's `action = ENVELOPE_BACKFILLED` (a new enum value) and the `extraJson` carries `{originalDay, backfillFor}` for transparency.
5. **Given** spec 007 Knowledge Graph is live in the future, **Then** a backfill envelope is linked to its `backfillFor` day-node with an edge `BACKFILLED_ONTO` so the graph distinguishes "what the user noted in the moment" from "what they reconstructed later."

### User Story 3 — Compose from anywhere via system share sheet (P3)

As a user reading a webpage or in any app, I tap the system share button, choose Orbit, and the shared text/URL arrives as a pre-filled compose sheet I can annotate before sealing.

**Why P3**: Large convenience win but partially overlapping with the clipboard bubble. Moves to v1.1 unless trivial.

**Acceptance**:
1. Orbit registers a `SEND` intent filter for `text/plain` and `text/*; S.android.intent.extra.TEXT`.
2. Activating share opens the compose sheet pre-populated with the shared payload.
3. Sealing a shared-sheet envelope writes `envelope.captureSource = SHARE_SHEET` (new enum) in place of `CLIPBOARD` / `SCREENSHOT_OBSERVER`.

---

## Functional Requirements

**Schema / data model** (amendments to [data-model.md](specs/002-intent-envelope-and-diary/data-model.md)):
- **FR-011-001**: System MUST add `captureSource: CaptureSource` enum to `IntentEnvelope` with values `{CLIPBOARD_BUBBLE, SCREENSHOT_OBSERVER, MANUAL_COMPOSE, SHARE_SHEET, AGENT}`. Existing rows default to `CLIPBOARD_BUBBLE` (safe because screenshot rows already carry a distinct `isScreenshot` flag; migration preserves that).
- **FR-011-002**: System MUST add `backfillFor: LocalDate?` to `IntentEnvelope`. Null for all existing rows.
- **FR-011-003**: System MUST add `AuditAction.ENVELOPE_BACKFILLED` to the audit-log enum.
- **FR-011-004**: Room migration from the current schema is additive-only (two nullable columns + enum extension). No data rewrite.

**Compose UI**:
- **FR-011-005**: System MUST ship `ComposeSheet` as a Compose modal bottom sheet rendered above DiaryScreen, styled per design.md §4 (to be coded after spec 010). Until 010 lands, ship with palette + typography tokens even if primitives aren't final.
- **FR-011-006**: System MUST pipe the composed payload through the `SealOrchestrator` (spec 002) using the same scrub → intent-classify → seal path as clipboard capture. No new seal path.
- **FR-011-007**: System MUST expose a `ComposeViewModel` with states `{Idle, Composing(text, url?, intent?, backfillFor?), Sealing, Sealed, Error}`. Standard sealed-class UI pattern.
- **FR-011-008**: System MUST allow backfill only for days between the first envelope's dayLocal and yesterday (inclusive). Today's affordance writes directly to today (no backfill marker).

**Affordance placement**:
- **FR-011-009**: DiaryScreen MUST show a compose affordance that is discoverable but not visually loud — a small typographic `＋` glyph in the bottom-right of the scaffold, sized to 56 dp (Material FAB baseline) but styled per design.md (no circle-with-shadow).
- **FR-011-010**: When viewing a past day (day pager index > 0), the affordance label extends to `＋ Add to this day` so the user understands the context before tapping.

**Audit + telemetry**:
- **FR-011-011**: Every manual seal writes `AuditAction.ENVELOPE_CREATED` with `extraJson = {"source":"manual_compose","hasUrl":<bool>,"intent":"<chosen or ambiguous>"}`.
- **FR-011-012**: Every backfill seal writes BOTH `ENVELOPE_CREATED` and `ENVELOPE_BACKFILLED` rows in one transaction. The backfill row carries `{originalDay, backfillFor}`.

**Principle compliance**:
- **FR-011-013**: Manual composition MUST NOT bypass the sensitivity scrubber (spec 002 FR-002a). Same rules, same redaction, same audit.
- **FR-011-014**: Manual composition MUST NOT trigger any network call. If the user composes a URL, the URL is queued into the existing continuation pipeline (spec 002), which independently respects charger+wifi rules.
- **FR-011-015**: Composition is an on-device-only path: no prompt, no classification, no inference crosses the process boundary. (Intent classification runs in `:ml` per Principle VI.)

---

## Success Criteria

- **SC-011-1**: A new user can find the compose affordance within 60 seconds of first opening the diary (A/B test or moderated usability session).
- **SC-011-2**: Manual envelopes round-trip through SealOrchestrator with identical audit trail to clipboard captures (excepting the `source` discriminator).
- **SC-011-3**: Backfill envelopes appear on the targeted day AND carry a visible "Added on <date>" annotation so the user can distinguish in-the-moment capture from retroactive capture at a glance.
- **SC-011-4**: Sensitivity scrubber rules applied identically to manual payloads (unit-test parity with clipboard path).
- **SC-011-5**: Zero regressions on existing clipboard capture latency (the manual path is additive, not a rewrite).

---

## Open Questions

- **Q1**: Do we expose a "compose FAB" on EnvelopeDetailScreen too (compose a reply / follow-up envelope linked to the current one)? Defer to v1.2 after the threading model crystallizes.
- **Q2**: Should backfill count against the daily cap (if we have one)? Recommendation: no cap on backfill to encourage retroactive honesty; cap is for anti-spam against live-capture, not reflection.
- **Q3**: Should the compose sheet remember a draft across app-close? Recommendation: yes, in-memory only — if the OS kills the process, the draft is lost. Persisting drafts to disk crosses into "Orbit holds untrusted input," which Principle III implicitly discourages (unsealed content shouldn't pile up on disk).
- **Q4**: Does the backfill affordance need a confirmation step ("Are you sure you want to add to April 18?")? Recommendation: no — the header "Adding to April 18" in the sheet is sufficient, and an extra confirmation dilutes the "one daybook, write freely" feel.

---

## Constitutional Alignment

This spec requires one constitution amendment, added by a companion diff in the same PR:

> **Principle II — amendment (2026-04-21)**: Capture is *effortless* AND *deliberate*. The primary paths (bubble, screenshots) minimize friction for reactive thoughts. The secondary path (manual composition, share sheet, future voice) serves deliberate thoughts the user initiates from outside an observed surface. Both paths converge on the same seal contract, the same scrub pipeline, the same audit obligation. Composition is never *required* — a user who only ever uses the bubble gets a complete Orbit.

All other principles are respected unchanged:
- **I (Local-First Supremacy)**: manual composition never touches the network; the only network path is the post-seal URL continuation, identical to clipboard.
- **III (Intent Before Artifact)**: the wax-seal chooser in the compose sheet enforces intent selection at seal time; ambiguous is still the default.
- **VI (Privilege Separation)**: the compose sheet lives in the `:ui` process and hands the payload to `:ml` via the existing seal binder. No new process boundary, no new permission.
- **VIII (Collect Only What You Use)**: `backfillFor` is a single nullable date — it earns its place by enabling the day-annotation affordance in User Story 2.
