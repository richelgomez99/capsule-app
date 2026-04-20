# Orbit Cloud Storage (v1.1)

**Status**: DRAFT — targets v1.1
**Depends on**: spec 002 complete (EnvelopeStorageBackend abstraction from tasks 002/T025c–T025d)
**Governing document**: `.specify/memory/constitution.md` — implements Principle X Model A, Principles XI and XII
**Created**: 2026-04-17
**Last amended**: 2026-04-20 (renamed from BYOC Cloud Storage to Orbit Cloud Storage under constitution v3.0.0; BYOC moved to spec 009)

---

## Summary

Orbit Cloud Storage is the default cloud tier for Orbit users who opt into
cloud features. It is an Orbit-operated managed PostgreSQL service with a
strict structural-guarantee boundary: each user gets a dedicated schema
(`orbit_<user_id>`), a dedicated database role, and a per-user Data
Encryption Key (DEK) wrapped by a KMS-managed Key Encryption Key. Orbit
operates the infrastructure; the user still owns the data, holds the
right to export or delete at any moment, and retains provenance through a
local-only audit log and consent ledger.

This spec replaces the former v1.2 BYOC-first design. BYOC (Bring Your
Own Cloud) is still supported — it is now spec 009 targeting v1.3 for
power users who want full sovereignty over the cloud tier. For everyone
else, Orbit Cloud is the default opt-in path to multi-device sync, the
full knowledge graph, vector retrieval, and the Orbit agent's long-term
memory.

The core commitment encoded by Principle X Model A: **structural
guarantees (per-tenant schema, per-user DEK, locally held consent ledger,
GDPR-compliant export/delete, no cross-tenant derivatives) make managed
multi-tenancy constitutionally permissible.** Migration to BYOC (spec
009) is lossless by construction — the schemas are identical.

---

## User Stories

### US-006-001 — One-tap cloud onboarding (Priority: P1)

As a user who finishes v1 onboarding, I see an optional "Enable Orbit
Cloud" card in Settings that explains what enabling means in plain
language. Tapping "Enable" signs me in with my Orbit account, provisions
my tenant schema, uploads my device's public key, and flips cloud-backed
features on with zero further configuration.

**Acceptance**:

1. Settings → Cloud Storage shows Orbit Cloud as the default option with
   an "Advanced → Use my own database" entry point to BYOC (spec 009).
2. Enabling Orbit Cloud triggers account sign-in (Orbit identity) and,
   on first enable, provisions an `orbit_<user_id>` schema and role
   server-side within 30 seconds.
3. The device uploads its public key; the server wraps a newly generated
   DEK with the user's KMS KEK; the wrapped DEK is stored server-side,
   the plaintext DEK is pulled on-device only at use.
4. Enable surfaces a clear consent card listing exactly what will sync,
   what will not (audit log, consent ledger, `local_only` facts), and
   where the data is hosted (region).

### US-006-002 — Opt-in per data category (Priority: P1)

As a user with Orbit Cloud enabled, I toggle individual data categories
independently. The audit log and consent ledger are never syncable.

**Acceptance**:

1. Independent toggles: envelopes, embeddings, knowledge graph, profile
   facts, continuation results, agent memory, plans, skill registry.
   Default: envelopes + embeddings + KG + profile facts ON (required for
   spec 004 Ask Orbit); agent memory + plans + skill registry ON when
   spec 008 Agent is enabled.
2. No toggle for `AUDIT_LOG` or `CONSENT_LEDGER`. The UI explicitly
   notes they stay on-device forever.
3. Toggling a category ON triggers a one-time backfill continuation;
   toggling OFF stops future sync (not retroactive delete).
4. Profile facts and KG nodes/edges tagged `local_only` are never
   uploaded regardless of toggle state (enforced by Principle XI in
   `:agent`).

### US-006-003 — Local is authoritative (Priority: P1)

As a user whose cloud connection drops, Orbit keeps working with zero
functional loss.

**Acceptance**:

1. Cloud sync failures do not block local writes. Last-N-days diary
   projection stays on-device for offline use.
2. Cloud unreachable for > 24 hours surfaces a non-blocking banner in
   Settings.
3. Disabling Orbit Cloud removes every cloud-path code branch from
   runtime; subsequent sessions behave exactly like a local-only install.

### US-006-004 — Data export and deletion user-initiated (Priority: P1)

As a user who wants to see, export, or delete my cloud data, I can do so
without opening a support ticket.

**Acceptance**:

1. Settings → Cloud Storage → "Export my data" triggers a GDPR-grade
   export job that produces a ZIP containing JSON for every row in my
   tenant schema; delivery via signed download link within 72 hours.
2. Settings → Cloud Storage → "Delete all my cloud data" prompts for
   explicit confirmation, then fires a `DELETE /tenant` request that
   drops `orbit_<user_id>` schema, revokes the role, and destroys the
   wrapped DEK in KMS within 72 hours.
3. Deletion is confirmed by a local audit log entry with the export
   receipt ID, and the in-app receipt is surfaced when the user opens
   Cloud Storage again.
4. Orbit publishes a quarterly transparency report listing aggregate
   (non-user-identifying) deletion, export, and consent-revocation
   counts.

### US-006-005 — Multi-device sync as a first-class feature (Priority: P1)

As a user with Orbit Cloud on two devices, my envelopes, KG, agent
memory, and profile appear on both devices and stay in sync.

**Acceptance**:

1. A new envelope sealed on device A appears on device B within 30
   seconds when B has network.
2. Conflict resolution: last-writer-wins on non-content fields; content
   is append-only per Principle III.
3. Audit logs and consent ledgers do NOT merge — each device retains its
   own local history.
4. Each device is registered in a `device_registry` table tied to the
   Orbit account; the user can revoke a device from any other device.

### US-006-006 — Migrate to BYOC at any time (Priority: P2)

As a user who later decides they want full sovereignty, I can export my
Orbit Cloud tenant and import it into a BYOC Postgres (spec 009) without
feature loss.

**Acceptance**:

1. Settings → Cloud Storage → "Move to BYOC" initiates a schema-
   identical transfer from Orbit Cloud to the user's configured BYOC
   instance.
2. After successful transfer, Orbit Cloud tenant is placed in read-only
   mode pending confirmation to purge.
3. Confirmed purge deletes the Orbit Cloud tenant within 72 hours and
   records the event in the local audit log.

---

## Non-Goals (v1.1)

- No cross-tenant analytics, training data, or derivatives (Principle
  X Model A condition #3, enforced at the database role level —
  tenants literally cannot see each other).
- No raw screenshot bytes by default. Thumbnails, OCR, and transcripts
  only; originals stay on device unless the user explicitly uploads.
- No peer-to-peer device sync.
- No offline-first conflict resolution beyond last-writer-wins on
  non-content fields.
- No Firestore or non-Postgres backend.

---

## Functional Requirements

### Storage and tenancy

- **FR-006-001**: System MUST expose an `OrbitCloudBackend` implementation
  of `EnvelopeStorageBackend` (002 T025c) that writes to local Room first
  and enqueues an asynchronous cloud mirror job originating from the
  `:net` process.
- **FR-006-002**: System MUST provision exactly one schema per user named
  `orbit_<user_id>` server-side on enable, with a dedicated database
  role that has privileges restricted to that schema.
- **FR-006-003**: System MUST prohibit any server-side query that joins
  across tenant schemas. The database role enforcement is the structural
  guarantee.
- **FR-006-004**: System MUST version the schema and run migrations
  server-side in a way that preserves tenant isolation (one schema
  migrated at a time, not across tenants in a shared table).

### Encryption

- **FR-006-005**: System MUST generate a per-user DEK on enable, wrap
  it with a KMS-managed KEK, and store only the wrapped blob
  server-side.
- **FR-006-006**: Columns containing envelope bodies, media references,
  transcripts, and any profile fact tagged above `public_to_orbit`
  MUST be stored ciphertext encrypted with the user's DEK.
- **FR-006-007**: Metadata columns used for indexing (timestamps,
  tenant id, envelope id, KG edge types, bi-temporal bounds) MAY be
  stored plaintext to support server-side queries; content columns
  MUST NOT be.
- **FR-006-008**: Device key material for decrypting DEK-wrapped
  ciphertext MUST live only on-device in Android Keystore.

### Consent and provenance

- **FR-006-009**: System MUST NEVER sync the audit log to cloud.
- **FR-006-010**: System MUST NEVER sync the consent ledger to cloud.
- **FR-006-011**: System MUST enforce Principle XI on every cloud
  write — prompts and payloads assembled in `:agent` are filtered for
  `local_only` facts, fresh consent, sensitivity, and necessity before
  crossing into `:net`.
- **FR-006-012**: System MUST enforce Principle XII on every cloud
  write — derived facts without an `episode_id` reference are
  rejected at write time.
- **FR-006-013**: System MUST audit-log every cloud read, every cloud
  write, every failed attempt, and every schema migration, keeping
  the audit log on-device forever.

### Lifecycle and rights

- **FR-006-014**: System MUST provide a GDPR-grade export job producing
  a ZIP of every row in the tenant schema, deliverable within 72 hours
  of request, with a signed download link.
- **FR-006-015**: System MUST provide a full-tenant deletion flow that
  drops `orbit_<user_id>`, revokes the role, destroys the wrapped DEK
  in KMS, and completes within 72 hours with a local audit confirmation.
- **FR-006-016**: System MUST surface per-device revocation so a user
  can sign out a lost device from any other device.
- **FR-006-017**: Orbit MUST publish a quarterly transparency report
  listing aggregate (non-identifying) counts of deletions, exports,
  and consent revocations.

### Migration to BYOC

- **FR-006-018**: System MUST provide a lossless Tier 1 → Tier 2
  migration path. The schemas match by construction; the migration
  rewraps the DEK for the target environment.
- **FR-006-019**: After successful BYOC transfer confirmation, the
  Orbit Cloud tenant MUST be deleted within 72 hours per FR-006-015.

---

## Storage model

Schema inside each `orbit_<user_id>` (Postgres 15+, pgvector required):

```sql
-- All ciphertext columns suffixed _ct are encrypted by the user's DEK.
-- Metadata columns are plaintext to support indexing.

CREATE TABLE envelopes (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    day_local DATE NOT NULL,
    content_type TEXT NOT NULL,          -- plaintext (index)
    intent TEXT NOT NULL,                -- plaintext (index)
    intent_source TEXT NOT NULL,         -- plaintext (audit)
    body_ct BYTEA,                       -- ciphertext
    media_ref_ct BYTEA,                  -- ciphertext
    ocr_ct BYTEA,                        -- ciphertext
    transcript_ct BYTEA,                 -- ciphertext
    state_snapshot JSONB NOT NULL,
    intent_history JSONB NOT NULL,
    archived_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE embeddings (
    envelope_id UUID REFERENCES envelopes(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    vector VECTOR(768),                  -- computed on-device, uploaded plaintext
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (envelope_id, chunk_index)
);

CREATE TABLE continuation_results (
    envelope_id UUID REFERENCES envelopes(id) ON DELETE CASCADE,
    continuation_type TEXT NOT NULL,
    result_ct BYTEA NOT NULL,            -- ciphertext
    model_provenance TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (envelope_id, continuation_type)
);

-- Knowledge graph (spec 007)
CREATE TABLE kg_nodes (
    id UUID PRIMARY KEY,
    type TEXT NOT NULL,                  -- Person|Place|Project|Topic|Item|Event|State|Skill|Envelope|Pattern
    canonical_name_ct BYTEA,             -- ciphertext
    aliases_ct BYTEA,
    embedding VECTOR(768),
    sensitivity TEXT NOT NULL,           -- public_to_orbit|local_only|ephemeral
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE kg_edges (
    id UUID PRIMARY KEY,
    from_node UUID NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
    to_node UUID NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    episode_id UUID NOT NULL REFERENCES episodes(id) ON DELETE RESTRICT,
    sensitivity TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE episodes (
    id UUID PRIMARY KEY,
    source_envelope_id UUID REFERENCES envelopes(id) ON DELETE SET NULL,
    source_kind TEXT NOT NULL,           -- capture|continuation|agent_action|user_edit|import
    raw_payload_ct BYTEA NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);

-- Profile as KG subgraph: profile facts are stored as kg_edges where
-- from_node points to the user_id node. Table below is a convenience
-- materialised view for hot-path reads.
CREATE TABLE profile_facts (
    id UUID PRIMARY KEY,
    predicate TEXT NOT NULL,             -- e.g. "works_with", "prefers_tone"
    object_ct BYTEA NOT NULL,            -- ciphertext
    weight DOUBLE PRECISION NOT NULL,
    sensitivity TEXT NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    episode_id UUID NOT NULL REFERENCES episodes(id) ON DELETE RESTRICT
);

-- Agent (spec 008)
CREATE TABLE agent_state (
    session_id UUID PRIMARY KEY,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    context_jsonb_ct BYTEA NOT NULL      -- ciphertext, high churn
);

CREATE TABLE agent_patterns (
    -- Long-term patterns live as kg_nodes of type='Pattern'.
    -- This table is a read-optimized view.
    node_id UUID PRIMARY KEY REFERENCES kg_nodes(id) ON DELETE CASCADE,
    usage_count INT NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE plans (
    id UUID PRIMARY KEY,
    goal_ct BYTEA NOT NULL,
    status TEXT NOT NULL,                -- pending|running|awaiting_user|succeeded|failed|cancelled
    steps_jsonb_ct BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    outcome_episode_id UUID REFERENCES episodes(id)
);

-- Skills (installed AppFunctions)
CREATE TABLE skills (
    id UUID PRIMARY KEY,
    app_package TEXT NOT NULL,
    function_id TEXT NOT NULL,
    schema_version INT NOT NULL,
    schema_jsonb JSONB NOT NULL,
    installed_at TIMESTAMPTZ NOT NULL,
    user_default BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (app_package, function_id)
);

CREATE TABLE skill_usage (
    skill_id UUID REFERENCES skills(id) ON DELETE CASCADE,
    invoked_at TIMESTAMPTZ NOT NULL,
    outcome TEXT NOT NULL,               -- success|fail|cancelled|undone
    latency_ms INT NOT NULL,
    episode_id UUID REFERENCES episodes(id)
);

-- Ontology and operational tables (abbreviated)
CREATE TABLE ontology (
    node_type TEXT PRIMARY KEY,
    schema_jsonb JSONB NOT NULL,
    edited_by TEXT NOT NULL,             -- 'system' | 'user'
    edited_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE device_registry (
    device_id UUID PRIMARY KEY,
    registered_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE usage_meter (
    period_start DATE PRIMARY KEY,
    llm_tokens_in BIGINT NOT NULL,
    llm_tokens_out BIGINT NOT NULL,
    storage_bytes BIGINT NOT NULL
);

CREATE TABLE export_requests (
    id UUID PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    signed_url_ct BYTEA
);
```

### Bi-temporal traversal (no AGE)

Multi-hop graph traversal uses recursive CTEs over `kg_edges` with
`valid_from`/`valid_to`/`invalidated_at` as filter predicates. Apache
AGE is intentionally not used in v1.1 — it adds operational complexity
without enough payoff for the traversal patterns Orbit cares about.
Example pattern appendix to be maintained alongside this spec.

---

## Dependencies

- 002 T025c–T025d (EnvelopeStorageBackend abstraction)
- 002 T025 (NetworkGatewayService in `:net`)
- 005 Orbit-managed LLM proxy (shared `:net` transport and identity)
- 007 Knowledge Graph (shared schema)
- 008 Orbit Agent (shared schema; agent state and patterns live here)
- New: `com.capsule.app.net.cloud.OrbitCloudClient`
- New: `com.capsule.app.net.cloud.SchemaMigrator` (shared with 009)
- New: `com.capsule.app.settings.OrbitCloudScreen`
- New: Orbit Cloud API contract (see
  `specs/contracts/orbit-cloud-api-contract.md`)
- New: Envelope content encryption contract (see
  `specs/contracts/envelope-content-encryption-contract.md`)

---

## Open Questions

- Hosting choice: Neon managed Postgres (lower ops) vs self-hosted on
  Hetzner (lower cost, higher ops). Leaning Neon for launch, reserving
  Hetzner as a cost-cut lever.
- Region placement: launch US-only or offer region pick on enable?
  Leaning US + EU at launch; add regions as demand warrants.
- Do we expose the transparency report from within Settings, or only on
  the Orbit website? Leaning both (in-app link + web).

---

*Targeted for v1.1 after v1 stabilizes. Constitutionally bounded by
Principle X Model A, Principle XI (consent-aware prompt assembly),
and Principle XII (provenance-or-it-didn't-happen).*
