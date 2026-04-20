# Orbit Constitution

Orbit is the local-first personal memory layer for mobile. This document
encodes the twelve principles that govern every decision in the product —
from architecture to UX to data policy. These principles are non-
negotiable. A design, feature, or implementation that violates any of
them is wrong, regardless of how useful it seems in isolation.

**Version**: 3.0.0
**Ratified**: 2026-04-16
**Last Amended**: 2026-04-20
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

All user content and all state derived from user behavior remain on the
device by default. No captured text, image, audio, embedding, or inferred
state is transmitted to any remote server unless the user explicitly
exports it. The network exists only to hydrate public URLs the user has
already chosen to save.

This is not a marketing claim; it is a structural commitment enforced at
the manifest, the process boundary, the Room access layer, and the lint
rule. It is also Orbit's permanent moat: Google cannot credibly promise
it because their business model opposes it; Apple can but does not ship
on Android; every cloud competitor has already made the opposite choice.

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

Orbit itself never operates LLM keys, never runs a managed cloud LLM on
behalf of users, and never bills for cloud inference. If the user
revokes a key or disables a capability, the system falls back to on-
device Gemini Nano with no feature loss — only quality change.

Every feature Orbit ships must work at full feature coverage with Nano
alone. Cloud is quality, never scope. An unaudited cloud call is a
structural bug, not a feature.

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
