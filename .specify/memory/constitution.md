<!--
SYNC IMPACT REPORT — 2026-04-28
================================
Version change: 3.1.0 → 3.2.0 (MINOR — scope addition, no guarantee removed)

Modified principles:
- Principle I (Local-First Supremacy): scoped. Network egress for AI
  inference via `:net` is now permitted; local-mode kill switch
  (`RuntimeFlags.useLocalAi`) is a non-negotiable structural invariant;
  `NanoLlmProvider` is preserved for the life of the product.
- Principle VI (Privilege Separation By Design): clarifying amendment
  noting `:net` carries AI inference traffic in addition to URL
  hydration, via `callLlmGateway` AIDL method. Lint rule unchanged.
- Principle IX (User-Sovereign Cloud Escape Hatch — LLM): clarifying
  amendment. Managed Orbit-operated proxy is permitted as the default
  cloud-mode path subject to non-negotiable conditions (Supabase Auth
  JWT, no server-side prompt assembly, kill switch, BYOK + per-
  capability toggles + audit log per call before alpha cutoff,
  multi-user RLS smoke test before alpha).

Added sections: none (no new principles).
Removed sections: none.

Templates / artifacts requiring updates:
- ✅ `.specify/memory/constitution.md` (this file).
- ✅ `specs/013-cloud-llm-routing/plan.md` — Constitution Check
  verdict updated from CONDITIONAL PASS to PASS.
- ✅ `.github/copilot-instructions.md` — no wording change required;
  current template does not embed principle text.
- ✅ `.specify/templates/plan-template.md` — principle-list-driven;
  no wording change required.
- ✅ `.specify/templates/spec-template.md` — no principle wording
  embedded.
- ✅ `.specify/templates/tasks-template.md` — no principle wording
  embedded.
- ⚠ `specs/005-cloud-boost-byok-llm/spec.md` — pending. Must absorb
  BYOK provisioning UI + per-capability cloud opt-out toggles + audit
  log per cloud LLM call. Flagged in amendment log; no edit applied.

Follow-up TODOs:
- TODO(spec-005): expand `specs/005-cloud-boost-byok-llm/spec.md` to
  cover BYOK + per-capability toggles + audit log before external
  alpha install (alpha-install gate).
- TODO(edge-fn-spec): track cloud-LLM audit log entries (provider,
  model, capability, prompt digest, token count) as a precondition
  for the first external alpha.
- TODO(adr-007): land Supabase multi-user RLS smoke test as the
  alpha-install gate.

Cross-references: spec 013-cloud-llm-routing; ADR-003, ADR-006, ADR-007.
-->

# Orbit Constitution

Orbit is the local-first personal memory layer for mobile. This document
encodes the twelve principles that govern every decision in the product —
from architecture to UX to data policy. These principles are non-
negotiable. A design, feature, or implementation that violates any of
them is wrong, regardless of how useful it seems in isolation.

**Version**: 3.2.0
**Ratified**: 2026-04-16
**Last Amended**: 2026-04-28
**Domain**: orbitassistant.com

**Companion documents**:

- `.specify/memory/PRD.md` — product requirements (what Orbit ships in v1
  and the post-v1 roadmap).
- `.specify/memory/design.md` — visual architecture: typography, color,
  motion, spatial composition, every v1–v1.3 surface. The principles
  below set the *what* and *why*; `design.md` sets the *how it looks*.

---

## Core Principles

### I. Local-First Supremacy

All user content — captured text, images, audio, OCR/transcripts, and
the SQLCipher corpus that holds them — remains on the device as the
source of truth. The corpus and the audit log and the consent ledger
are never authoritative anywhere but on the device. Network egress of
user content is permitted only along structurally-bounded paths
(Principle VI) for the following purposes, each justified by an
explicit principle below:

1. Hydrating public URLs the user has already chosen to save
   (URL fetch via `:net`).
2. **AI inference** routed through `:net` to a cloud LLM provider —
   either an Orbit-operated managed proxy (Principle IX, Model A of
   Principle X) or a user-supplied BYOK endpoint (Principle IX). Cloud
   inference MAY be the default routing for AI calls so long as a
   user-facing kill switch (`RuntimeFlags.useLocalAi`) is always
   available and, when set, structurally prevents any network call
   originating from an `LlmProvider`.
3. Sovereign cloud storage that the user has opted into per-category
   under Principle X.
4. User-initiated export.

Nothing else leaves the device. Inferred state, embeddings, profile
facts, KG nodes/edges, and continuation results follow Principle X for
storage and Principle XI for any prompt assembly that crosses `:net`.

This is not a marketing claim; it is a structural commitment enforced
at the manifest, the process boundary, the Room access layer, and the
lint rule. It is also Orbit's permanent moat: Google cannot credibly
promise it because their business model opposes it; Apple can but does
not ship on Android; every cloud competitor has already made the
opposite choice.

**Local-mode invariant (non-negotiable)**: When the user sets
`RuntimeFlags.useLocalAi = true`, every `LlmProvider` implementation
resolved by `LlmProviderRouter` MUST be a fully on-device implementation
(e.g., `NanoLlmProvider`) and MUST NOT open any network socket. The
local-mode path is verifiable, end-to-end, with the device in airplane
mode. `NanoLlmProvider` is preserved for the life of the product as
the local-mode option; the strangler-fig migration in spec
`013-cloud-llm-routing` does not retire it.

**Cloud-mode invariant (non-negotiable)**: When cloud routing is
active, AI inference MUST originate from the `:net` process via the
`callLlmGateway` AIDL surface. Direct HTTPS from `:ml`, `:capture`, or
`:default` for AI inference is structurally forbidden — the same
lint rule that bans HTTP clients outside `:net` for URL hydration
bans them for AI inference. The boundary is the process, not the
purpose.

### II. Effortless Capture, Any Path

Capture friction is the enemy. Every path Android exposes — silent
clipboard observation, the floating bubble, the share sheet, voice input,
the MediaStore observer for screenshots — converges on the same two-
second experience: chip-row disambiguation, with silent-wrap for high-
confidence cases and graceful auto-dismiss to AMBIGUOUS if the user
ignores the chip.

A user who hesitates before saving has already lost the capture. The
mental cost of using Orbit must be lower than the mental cost of
re-finding what they wanted to remember.

**Amendment 2026-04-21 (spec 011 Manual Compose)**: Capture is
*effortless* AND *deliberate*. The reactive paths (bubble, screenshot
observer) minimize friction for thoughts already surfaced by another
app. The deliberate path (manual compose, share sheet, and — later —
voice) serves thoughts the user initiates from outside any observed
surface, including retroactive backfill onto a past day in the diary.
Both families of paths converge on the same seal contract, the same
sensitivity-scrub pipeline, and the same audit obligation. Composition
is never *required* — a user who only ever uses the bubble gets a
complete Orbit. Backfill is always stamped with wall-clock honesty
(`createdAt = now()`) while its `dayLocal` reflects the page the user
was writing onto, and an `ENVELOPE_BACKFILLED` audit row records the
temporal displacement so the user (and the knowledge graph, per
Principle XII) can tell reflective notes from in-the-moment notes.

### III. Intent Before Artifact

The unit Orbit organizes is not the screenshot or the URL. It is the
**IntentEnvelope** — a sealed object of *what* was captured and *why it
was captured*. Intent is captured in the moment, at the point of save,
and can only be sealed forward (never rewritten retroactively without
audit).

Without intent, Orbit is another screenshot folder. With intent, Orbit
is the memory of a person.

### IV. Continuations Grow Captures

A capture is a seed, not a harvest. Every envelope has continuations —
hydrations, extractions, summaries, topic tags — that run quietly in the
background on charger + wifi and return to the user already processed.
Continuations must never consume foreground attention.

If opening Orbit ever feels like homework, the product has failed.

### V. Under-Deliver on Noise

A Ripe Nudge that was not acted on is a strike against trust. Orbit
should fire fewer, better nudges than any competitor, and the ratio of
nudges-sent to nudges-acted-on is a first-class product metric.

Silence is a feature. Notification shame is the failure mode we never
allow.

### VI. Privilege Separation By Design

No single process in Orbit holds both user content and network access:

- `:capture` process: clipboard, MediaStore observer, overlay service.
  No network permission. No corpus write.
- `:ml` process: corpus read/write, AICore/Gemini Nano access. No
  network.
- `:net` process: internet access. No corpus access. Single narrow
  entry point `fetchPublicUrl(url)` with referer/cookie stripping and
  HTTPS-only enforcement.
- `:ui` process: user interface surfaces. No direct corpus access;
  reads through `:ml` process binder only.

These boundaries are enforced at the `AndroidManifest.xml` level
(process splits, `INTERNET` permission on `:net` alone), at the Room
access layer (corpus opens only in `:ml`), and at the lint-rule level
(HTTP clients blocked outside the `:net` package).

**Amendment 2026-04-28 (spec 013 Cloud LLM Routing)**: `:net` is the
sole egress process for AI inference as well as URL hydration. The
existing AIDL channel between `:capture`/`:ml` and `:net` is extended
with `callLlmGateway(LlmGatewayRequestParcel) → LlmGatewayResponseParcel`.
No new cross-process surface and no new socket from `:capture` or
`:ml`. The lint rule "no OkHttp/HTTP clients outside `:net`" is
unchanged — the network restriction was always about the process
boundary, not the absence of network. `:capture` retains zero network
permission; `:ml` retains zero network permission.

User data leaving the device through a bug must be structurally
impossible, not procedurally discouraged.

### VII. Context Beyond Content

Orbit reads more than what the user captures. On-device context — time
of day, foreground app, activity state, and (in future phases) calendar,
wearables, and behavioral rhythms — tells Orbit not just what the user
cares about but the state they are in when they care.

A capture without context is half-captured. A nudge without context-
awareness fires blind. Context signals never leave the device;
inferences never leave the device. This commitment is the moat Google
and cloud competitors structurally cannot match.

### VIII. Collect Only What You Use

A signal enters the system only when it powers a concrete feature the
user can see or benefit from today. "Because we might need it later" is
never sufficient justification.

We accept cold-start costs on later phases in exchange for a data system
with integrity. This is the strongest possible version of Principle V:
applied not to notifications, but to the data itself. A user who audits
Orbit's data collection should find that every byte earns its place.

### IX. User-Sovereign Cloud Escape Hatch (LLM)

Orbit MAY call cloud LLMs, but only when three conditions hold
simultaneously:

1. The user has explicitly provided their own API key for a provider of
   their own choice (Google Gemini, OpenAI, Anthropic, OpenRouter, or any
   OpenAI-compatible endpoint).
2. The user has explicitly enabled cloud routing for each capability
   (intent classification, day summaries, URL summaries, sensitivity
   scan, action extraction, Ask Orbit) on a per-capability basis. There
   is no global "go cloud" switch.
3. The call originates from the `:net` process and is recorded to the
   local audit log with provider, model, capability, prompt digest, and
   token count.

Orbit MAY operate a managed cloud LLM proxy (Model A of Principle X)
as the default cloud-mode path when all of the following are true:

- The proxy is reachable only from `:net` (Principle VI), authenticated
  by a per-user Supabase Auth JWT, and never assembles prompts from
  server-held user data (Principle XI).
- A user-facing kill switch (`RuntimeFlags.useLocalAi`) is always
  available and, when set, structurally routes every `LlmProvider`
  call to an on-device implementation with no network egress
  (Principle I, local-mode invariant).
- BYOK and per-capability cloud opt-out toggles ship before the
  product is presented as honoring Principle IX in full. Until they
  ship, the gap is documented in this constitution's amendment log
  (see 2026-04-28) and tracked in spec `005-cloud-boost-byok-llm`.
- An audit log entry per cloud LLM call (provider, model, capability,
  prompt digest, token count) lands before the first external alpha
  install — this is a non-negotiable precondition for alpha and is
  cross-referenced from the spec 013 plan.

Orbit never bills for cloud inference. If the user revokes a key,
disables a capability, or sets `useLocalAi = true`, the system falls
back to on-device Gemini Nano (`NanoLlmProvider`) with no feature loss
— only quality change. `NanoLlmProvider` is preserved permanently as
the local-mode option.

Every feature Orbit ships must work at full feature coverage with Nano
alone. Cloud is quality, never scope. An unaudited cloud call after
the alpha cutoff is a structural bug, not a feature.

**Alpha-install gate (ADR-007)**: No external alpha install ships
until the multi-user RLS smoke test on Supabase passes (cross-tenant
isolation proven end-to-end against `auth.uid()`).

### X. Sovereign Cloud Storage (Orbit-Hosted or User-Hosted)

Orbit MAY mirror envelopes and their derived structures (embeddings,
knowledge graph, profile facts, agent memory, continuation results) to
remote storage under one of two user-chosen models. Both models are
bound by the same non-negotiable conditions.

**Non-negotiable conditions (apply to both models)**:

1. Local device storage (SQLCipher corpus) remains the source of truth
   for capture and continues to function if cloud is unreachable,
   disabled, or deleted. Cloud is never authoritative.
2. The user opts in to each data category separately: envelope text,
   embeddings, knowledge graph nodes, profile facts, continuation
   results, agent memory. The **audit log** and the **consent ledger**
   are explicitly excluded from every cloud model — they never leave
   the device, ever. These are the receipts; if receipts can be
   rewritten by a server, the receipts are worthless.
3. Every cloud read and every cloud write produces an audit log entry
   visible to the user in *What Orbit did today*, with provider,
   endpoint, payload digest, and outcome.
4. The user can trigger a full export (GDPR-grade, machine-readable)
   and a full delete from settings without Orbit's involvement. Delete
   propagates to every downstream system within 72 hours and is
   recorded in the local audit log.
5. Every derived fact (profile inference, KG edge, agent pattern,
   continuation suggestion) stored in cloud MUST carry a reference to
   at least one source episode on-device. Facts without provenance
   are structurally rejected at write time. (See Principle XII.)

**Model A — Orbit-Hosted Managed Cloud** (default for users who opt in):

Orbit MAY operate a multi-tenant storage service ("Orbit Cloud") on
user data, subject to ALL of the following structural isolation and
encryption guarantees:

- **Schema-per-tenant isolation**: each user's data lives in a
  dedicated PostgreSQL schema (`orbit_<user_id>`) with a dedicated
  database role. Cross-tenant joins are structurally impossible, not
  merely policy-prohibited. Row-level security alone is NOT sufficient
  isolation.
- **Per-user data encryption key (DEK)**: envelope bodies, media,
  transcripts, OCR outputs, and profile facts tagged `local_only` are
  stored ciphertext-only, encrypted with a per-user DEK that is itself
  wrapped by a KMS-managed root key. The DEK is never logged, never
  backed up in plaintext, and rotated on user request.
- **Consent-scoped fields**: only fields the user has consented to
  share at the category level are uploaded. Profile facts tagged
  `local_only` never leave the device under any code path; this is
  enforced on-device by the `:agent` process (Principle XI), not
  trusted to the cloud.
- **No cross-tenant derivatives**: Orbit does not train, fine-tune,
  aggregate, or derive any model, embedding, or statistic from user
  data across tenants without explicit opt-in consent recorded in the
  local consent ledger, per category, per purpose.
- **Quarterly transparency report**: access counts, KMS key
  operations, subprocessor list, and breach history published publicly.
- **Revocation**: the user can at any moment disable Orbit Cloud, after
  which no new writes are accepted and existing data is exported and
  purged on the user's timeline. Device operation continues
  uninterrupted.

**Model B — User-Hosted Sovereign Cloud (BYOC)**:

Orbit MAY write to remote storage legally owned by the user (e.g., a
Postgres instance at a provider of the user's choosing), subject to:

- The user holds admin access to inspect, export, and delete all data
  at any time without Orbit's involvement.
- Connection credentials are stored in Android Keystore on-device and
  never uploaded.
- The schema matches Model A so that a user may migrate in either
  direction without feature loss.

**What is permanently prohibited under both models**:

- Multi-tenant storage where users are isolated only by row-level
  security without schema-per-tenant separation.
- Uploading the audit log, the consent ledger, OAuth tokens for
  external integrations, or any profile fact tagged `local_only`.
- Server-side assembly of LLM prompts from user data. Prompts are
  assembled on-device per Principle XI and only the redacted final
  prompt crosses `:net`.
- Deriving facts about a user in the cloud without on-device episode
  provenance attached. See Principle XII.

### XI. Consent-Aware Prompt Assembly

Every prompt that leaves the device — whether to a BYOK LLM, an Orbit-
managed LLM proxy, or any cloud continuation service — is assembled
on-device by the `:agent` process and passes through a consent filter
before reaching `:net`.

The consent filter enforces, at minimum:

1. Profile facts, KG nodes, and KG edges tagged `local_only` are never
   included in outbound prompts, regardless of how the agent reasons
   about them internally.
2. Profile facts and KG structures tagged `ephemeral` are included
   only within their validity window and are excluded thereafter.
3. Sensitivity tags (e.g., financial, medical, credentials) redact or
   exclude matching content per user preference, with the redaction
   pattern and count recorded to the local audit log.
4. The final prompt digest, the categories of included content, and
   the destination provider are recorded to the local audit log
   before the prompt is handed to `:net`.

The server cannot decide what is safe to include in a prompt because
the server does not hold the consent ledger. Prompt assembly is a
device-side responsibility, structurally — not a cloud-side policy.

### XII. Provenance Or It Didn't Happen

Every derived fact stored anywhere in Orbit — a profile inference
("prefers 24h time"), a KG edge ("works with Sara"), an agent pattern
("always adds flights to calendar"), a continuation suggestion — MUST
carry a reference to at least one source episode on-device. Facts
without provenance are structurally rejected at write time in both
on-device storage and cloud storage.

An **episode** is the raw, immutable source data that produced a
derived fact: an envelope, an explicit user statement, a user
correction, or an external-integration read (e.g., a calendar event
read through an on-device OAuth token).

Provenance unlocks three guarantees that Orbit makes to the user:

1. **Auditable personalization**: for any claim Orbit makes about the
   user, the user can ask "why do you think this?" and Orbit answers
   with the episodes and the derivation path.
2. **Cascaded deletion**: when a user deletes an envelope, every
   derived fact that traces back to that envelope is reconsidered.
   Facts with surviving provenance remain; facts that lose their
   only provenance are invalidated with `invalidated_at = now()`.
3. **Correction propagation**: when a user rejects or corrects an
   inferred fact, the correction creates a negative signal at the
   provenance chain; similar future inferences are suppressed or
   down-weighted at the same chain.

A fact that cannot answer "where did this come from?" is a bug, not a
feature, regardless of how useful it seems.

---

## Phase 1 Scope (Application of the Principles)

This section is the authoritative v1 scope. Anything not explicitly
listed here is out of scope for Phase 1.

### Captures (v1)

- **Clipboard** via the floating bubble (existing primitive from spec
  001, preserved).
- **Screenshots** via `MediaStore` `ContentObserver` on the
  Screenshots folder, silently creating AMBIGUOUS envelopes.

Share sheet and voice capture are deferred to Phase 2.

### Per-Envelope Signals at Capture

Every IntentEnvelope carries exactly three context signals, captured at
seal time:

1. **Timestamp** — hour, day-of-week, timezone.
2. **Foreground app**, categorized into `{work_email, messaging,
   social, browser, video, reading, other}`. Raw package names are
   not stored.
3. **Activity Recognition state** — `{still, walking, running,
   in_vehicle, on_bicycle, unknown}`.

### Scheduling-Only Signals (Not Persisted on Envelopes)

These signals are read at runtime to decide when to execute
continuations; they are never saved to envelopes or the audit log:

- Battery / charging state.
- Network state (wifi / cellular / metered / offline).

### Explicitly Deferred (Principle VIII)

These signals do not enter the system in v1 because they do not yet
power a v1 feature. We accept the cold-start cost to honor the
principle.

- App-switching rate and screen engagement metrics — Phase 3
  (state-aware returns).
- Do-Not-Disturb / interruption filter state — Phase 2 (Ripe Nudges).
- Location and geofences — Phase 2 (place-aware Ripe Nudges).
- Calendar integration — Phase 2 (free-time Ripe Nudges).
- Health Connect, wearables — Phase 3+, optional.
- Keystroke and tap dynamics — Phase 3+, and only if Orbit has
  first-party typing surfaces.

### Explicitly Rejected (For the Life of the Product)

- **Accessibility Service** — Google Play policy (Oct 30, 2025)
  prohibits the autonomous-action use that Orbit would require.
- **Notification Listener** — utility-to-trust-cost ratio fails
  Principle V.
- **SMS and call logs** — toxic permissions.
- **Raw microphone / transcription** — trust cost too high; revisit
  only if a user-requested voice surface makes it earn its place
  under Principle VIII.

### Permissions Asked at Onboarding

Exactly four, in order, each with a one-sentence plain-language
justification:

1. **Overlay** (`SYSTEM_ALERT_WINDOW`) — the floating bubble.
2. **Notifications** — foreground service and future nudges.
3. **Usage Access** (`PACKAGE_USAGE_STATS`) — via Settings deep-link,
   to power Principle VII context labels.
4. **Activity Recognition** (`ACTIVITY_RECOGNITION`) — runtime, to
   power Principle VII context labels.

Permissions 3 and 4, if declined, cause graceful degradation — not
failure. Envelopes are still captured; context labels are thinner.

---

## Governance

This constitution supersedes all other practices in the Orbit codebase,
including feature specs, plans, task lists, code review conventions, and
deployment policies. Any tension between a principle stated here and a
decision made elsewhere in the repository is resolved in favor of this
document.

### Amendment Process

This constitution changes through a deliberate edit committed to the
repository at `.specify/memory/constitution.md`, not through inline
revision during feature work. A principle that is inconvenient in a
given sprint is a principle working correctly; removing it requires:

1. A named decision documented in the amendment log below.
2. A corresponding version bump (semver: MAJOR for principle changes,
   MINOR for scope additions, PATCH for clarifications).
3. A date stamp in the metadata at the top of this document.
4. A review of downstream artifacts (`PRD.md`, feature specs, plans,
   tasks) for conflicts introduced by the amendment, with those
   conflicts resolved before the amendment is merged.

### Compliance

Every feature spec, plan, code review, and implementation task must be
checkable against these twelve principles. If a proposed change would
violate a principle, the change is wrong by default; only an explicit
amendment (per the process above) can make it right.

### Amendment Log

#### 2026-04-20 — v3.0.0: Rewrite Principle X, add Principles XI and XII (Orbit-Hosted Cloud, Consent-Aware Prompts, Provenance)

**Rationale**: Principle X v2.0.0 required user-owned cloud infrastructure
(BYOC) as the only permitted cloud model and explicitly prohibited
Orbit-hosted multi-tenant storage. In practice, requiring every early
adopter to provision and administer their own Postgres instance is a
cold-start barrier that prevents the knowledge graph (spec 007) and the
agent layer (spec 008) from ever earning their reach. Users who want
the product's higher-order features cannot get them until they become
part-time DBAs. This is incompatible with Orbit's reach goals.

We therefore permit Orbit to operate a managed cloud (Model A) under
structural guarantees strong enough that the product remains honest
about "sovereign": schema-per-tenant isolation (not RLS alone),
per-user DEKs wrapped by KMS, consent ledger stays on-device, GDPR
export/delete, no cross-tenant derivatives, quarterly transparency.
BYOC (former Principle X) is preserved as Model B for power users and
retargeted to v1.3; the schemas are shared so migration in either
direction is lossless.

Principle XI (Consent-Aware Prompt Assembly) is added because, with an
Orbit-hosted backend, the server cannot be trusted to decide what is
safe to include in a prompt — the server does not hold the consent
ledger. Prompt assembly must happen on-device, in the `:agent`
process, with a consent filter gate between `:agent` and `:net`.

Principle XII (Provenance Or It Didn't Happen) is added because the
knowledge graph, user profile, and agent memory all accumulate derived
facts about the user. Without mandatory episode provenance, the user
cannot audit what Orbit believes, cannot reliably delete, and cannot
reliably correct. Provenance is the mechanism that makes the other
principles enforceable against derived structures, not just raw
captures.

**Downstream artifact review**:
- `.specify/memory/PRD.md` — rewrite three-tier storage section; add
  "storage scope" inventory (captures, intent/actions, user profile as
  KG subgraph, agent memory, KG structure, system/ops, external
  integrations); update post-v1 roadmap.
- `specs/006-cloud-storage-byok/` — rename to
  `specs/006-orbit-cloud-storage/` and rewrite for Orbit-hosted
  Postgres + pgvector + schema-per-tenant + per-user DEK.
- `specs/009-byoc-sovereign-storage/` — NEW; move former spec 006
  content here, retarget to v1.3.
- `specs/007-knowledge-graph/` — rewrite storage strategy; add five
  unique FRs (intent-typed edges, temporal decay + reinforcement,
  state-anchored nodes, continuation-lineage edges, user-editable
  ontology with audit); user profile explicitly modeled as KG
  subgraph where `subject = user_id`.
- `specs/005-cloud-boost-byok-llm/` — add Orbit-managed LLM proxy as
  default path; BYOK preserved; consent-aware prompt assembly
  requirement added.
- `specs/008-orbit-agent/` — NEW; `:agent` process; planner/executor
  using AppFunctions (spec 003); agent memory taxonomy
  (session/long-term patterns/plans/skill registry); consent filter
  requirement.
- `specs/003-orbit-actions/` — add AppFunctions schema registration
  FR; tool runs on-device, derived facts flow to cloud as episodes.
- `specs/contracts/orbit-cloud-api-contract.md` — NEW.
- `specs/contracts/envelope-content-encryption-contract.md` — NEW.
- `specs/002-intent-envelope-and-diary/tasks.md` — T025c
  `EnvelopeStorageBackend` contract confirmed unchanged; add note
  that it now routes to one of `LocalRoomBackend`, `OrbitCloudBackend`,
  or `ByocPostgresBackend`.

**Semver reasoning**: MAJOR. Principle X changes from "Orbit-hosted
multi-tenant storage is prohibited" to "Orbit-hosted multi-tenant
storage is permitted under structural guarantees". This is a change
to a principle, not a scope addition, and therefore MAJOR per the
amendment policy. Two new principles (XI, XII) are also added;
existing principles I–IX are unchanged.

#### 2026-04-17 — v2.0.0: Add Principles IX and X (User-Sovereign Cloud)

**Rationale**: The original 8 principles defined Orbit as local-first
and left "cloud" entirely outside the system. In practice, some users
will want cloud capabilities (richer LLM quality, multi-device sync,
cross-envelope knowledge graph) and will accept the privacy trade-off
explicitly. Rather than leaving this space undefined (and thereby open
to drift), we codify a *user-sovereign* cloud model: the user brings
their own keys and their own accounts, Orbit never operates
infrastructure on users' behalf without the user's legal ownership of
that infrastructure, and every cloud call is auditable.

This amendment preserves Principle I (Local-First Supremacy) as the
default: no user opts in by default, and fully-offline operation
remains the reference path every feature must support. Principles IX
and X define the escape hatch that users can *choose* to take.

**Downstream artifact review**:
- `.specify/memory/PRD.md` — add post-v1 roadmap section pointing to
  specs 003–007, clarify that v1 itself remains local-only.
- `specs/002-intent-envelope-and-diary/tasks.md` — add `LlmProvider`
  abstraction + storage-backend abstraction + sensitivity-scrub tasks.
  The abstractions cost ~1 day now and unblock v1.1+ cleanly.
- New feature specs created as part of this amendment:
  `specs/003-orbit-actions/` (v1.1 — calendar/todo extraction + weekly
  digest), `specs/004-ask-orbit/` (v1.2 — local RAG + optional BYOK
  cloud), `specs/005-cloud-boost-byok-llm/` (v1.1 — BYOK LLM),
  `specs/006-cloud-storage-byok/` (v1.2 — BYOK cloud storage),
  `specs/007-knowledge-graph/` (v1.3 — entity extraction + graph).

**Semver reasoning**: MAJOR. Adding non-negotiable principles is a
principle change per the amendment policy, even though the new
principles are escape hatches rather than restrictions on existing
behavior.

#### 2026-04-28 — v3.2.0: Scope Principle I for cloud LLM routing as default; clarify Principle VI and Principle IX (spec 013-cloud-llm-routing)

**Rationale**: Spec `013-cloud-llm-routing` introduces cloud LLM
routing as the **default** path for AI inference (intent
classification, summaries, day headers, action extraction,
embeddings, sensitivity scan). The previous wording of Principle I
("the network exists only to hydrate public URLs") encoded a
structural ban on AI-inference network egress that the cloud-pivot
product decision (orbit-pivot-plan-2026-04-28) explicitly retires.
Leaving the wording unchanged would put every cloud-mode `LlmProvider`
call in structural violation of the constitution.

The spirit of local-first is preserved, not removed. Specifically:

1. **Local-mode remains a first-class option**, gated by
   `RuntimeFlags.useLocalAi`. When the user sets it, every
   `LlmProvider` resolved by `LlmProviderRouter` MUST be a fully
   on-device implementation (`NanoLlmProvider`) and MUST NOT open any
   network socket. This is verifiable in airplane mode.
2. **`NanoLlmProvider` is preserved for the life of the product**
   as the local-mode option. The strangler-fig migration in spec 013
   does not retire it.
3. **The process boundary is unchanged**. AI inference MUST originate
   from `:net` via the new AIDL method `callLlmGateway`. The lint
   rule banning OkHttp/HTTP clients outside `:net` survives unchanged
   — the restriction was always about the process boundary, not the
   absence of network.
4. **Sovereignty is unchanged**. All AI inference still passes
   through the `LlmProvider` interface (Principle IX). Only the
   binding changes: cloud-mode resolves to `CloudLlmProvider`, which
   proxies through `:net` to a managed Vercel AI Gateway endpoint
   authenticated by a per-user Supabase Auth JWT. Server-side
   prompts are never assembled from server-held user data
   (Principle XI).

**Honestly-documented gaps (deferred, tracked)**:

- **BYOK provisioning UI** is not in scope for spec 013 and is
  deferred to spec `005-cloud-boost-byok-llm`. Day-1 cloud routing
  ships with managed Vercel AI Gateway + server-side provider keys.
- **Per-capability cloud opt-out toggles** are deferred to the same
  spec.
- **Audit log entry per cloud LLM call** (provider, model, capability,
  prompt digest, token count) is a precondition for the first external
  alpha install. Spec 013 ships the routing skeleton; the audit
  entries land in the Edge Function spec before alpha. Until then, no
  external alpha install.
- **Multi-user RLS smoke test** (ADR-007) is the alpha-install gate.
  Cross-tenant isolation must be proven end-to-end on Supabase
  before any external user installs the app.

**Cross-references**:

- Spec: `specs/013-cloud-llm-routing/spec.md` (and plan, research,
  data-model, quickstart, contracts under the same directory).
- ADR-003: Vercel AI Gateway + direct provider fallback.
- ADR-006: Cluster engine cloud migration as gating work for Phase 11
  Block 4 (`ClusterDetectionWorker` is carved out of the spec 013
  router migration and pinned to local-mode until that block lands;
  see spec 013 FR-013-028 and the `// CLUSTER-LOCAL-PIN` comment in
  `app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt`).
- ADR-007: RLS + multi-user smoke test prerequisite for alpha.

**Downstream artifact review**:

- `specs/013-cloud-llm-routing/plan.md` — Constitution Check moves
  from CONDITIONAL PASS to PASS for Principles I, VI, and IX as
  amended here. Items XI (deferred consent filter) and the audit-log
  precondition remain tracked in Complexity Tracking; they are now
  deferred-with-deadline (alpha-install gate) rather than
  unresolved-ambiguity.
- `.github/copilot-instructions.md` — auto-generated agent context;
  re-run `update-agent-context.sh` after this amendment if its
  template surfaces principle wording (current version does not, so
  no change required).
- `.specify/templates/plan-template.md` — Constitution Check template
  is principle-list-driven; no wording change required. Future plans
  will evaluate against the amended Principle I.
- `.specify/templates/spec-template.md` — no principle wording
  embedded; no change required.
- `.specify/templates/tasks-template.md` — no principle wording
  embedded; no change required.
- `specs/005-cloud-boost-byok-llm/spec.md` — must absorb the BYOK +
  per-capability toggles + audit-log work that this amendment defers.
  Flagged here; no edit applied in this amendment.

**Semver reasoning**: MINOR. No existing guarantee is removed —
local-mode remains structurally enforceable via `useLocalAi`, the
process boundary is unchanged, the lint rule survives, the sovereignty
principle is unchanged. A new permission (cloud LLM inference via
`:net` as default routing) is added, with explicit non-negotiable
conditions (kill switch, process boundary, audit-log precondition for
alpha, RLS smoke-test gate). Per the constitution's own amendment
policy: "MINOR for scope additions, PATCH for clarifications." This
is a scope addition that scopes an invariant rather than removing it,
so MINOR is the correct bump.
