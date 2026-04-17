# Cloud Storage — BYOC (v1.2)

**Status**: DRAFT — targets v1.2
**Depends on**: spec 002 complete (EnvelopeStorageBackend abstraction from tasks 002/T025c–T025d)
**Governing document**: `.specify/memory/constitution.md` — implements Principle X
**Created**: 2026-04-17

---

## Summary

BYOC (Bring Your Own Cloud) Storage lets users mirror their envelope corpus, embeddings, knowledge graph, and continuation results to their own cloud database — Supabase, Neon, a self-hosted Postgres, or Firestore — while local SQLCipher remains the source of truth.

The core commitment encoded by Principle X: **the data and the account both belong to the user, not to Orbit.** Orbit provides the schema, the migration script, and the sync logic; the user provides the infrastructure. Costs are billed to the user by their provider. Orbit holds no copy.

This is the privacy-respecting path to multi-device sync and richer cloud-side features (full-corpus chat, multi-hop knowledge graph, remote-rendered digests).

---

## User Stories

### US-006-001 — Paste-a-connection-string onboarding (Priority: P1)

As a user who created a free Neon or Supabase project, I open Settings → Cloud Storage, pick my provider type, paste my Postgres connection string, and tap "Set up." Orbit verifies the connection, runs the migration, and I can now opt into syncing specific data categories.

**Acceptance**:
1. Settings → Cloud Storage shows a guided flow with a "Create a free Neon account" / "Create a free Supabase project" link and a connection-string field.
2. Submitting a valid connection string verifies the connection, runs schema migrations, and confirms success with a "Your Orbit database is ready" card showing provider, project name (if derivable), and row count (0 initially).
3. Submitting an invalid connection string shows a clear error and does not persist.
4. Connection string is stored encrypted in the `:net` process scope only.

### US-006-002 — Opt-in per data category (Priority: P1)

As a user with cloud storage configured, I toggle individual data categories independently: envelope text, embeddings, knowledge graph, continuation results. The audit log is never syncable — it stays local forever.

**Acceptance**:
1. Four independent toggles: `ENVELOPES`, `EMBEDDINGS`, `KNOWLEDGE_GRAPH`, `CONTINUATION_RESULTS`. Default: all off.
2. No toggle for `AUDIT_LOG`. The UI explicitly notes "The audit log always stays on this device."
3. Toggling a category ON triggers a one-time backfill continuation that uploads existing data; toggling OFF stops future sync (does not delete cloud rows — that requires a separate "Delete from cloud" action).
4. First time any toggle is flipped on, a one-time dialog explains what will be synced and where it's going.

### US-006-003 — Local is authoritative (Priority: P1)

As a user whose cloud goes down, whose connection string is revoked, or who deletes their cloud project, Orbit keeps working with zero functional loss.

**Acceptance**:
1. Cloud sync failures do not block local writes. The local envelope is sealed; the cloud write is retried in the background.
2. Cloud unreachable for > 24 hours surfaces a non-blocking banner in Settings.
3. Removing the cloud configuration removes every cloud-path code branch from runtime — subsequent sessions behave exactly like a local-only install.

### US-006-004 — Data export and deletion user-initiated (Priority: P1)

As a user who wants to see my cloud data or delete it, I can do both without Orbit's involvement.

**Acceptance**:
1. Settings → Cloud Storage → "Open my database" deep-links to the user's provider dashboard (Supabase/Neon project URL where derivable).
2. Settings → Cloud Storage → "Delete all my cloud data" prompts for explicit confirmation, then runs `TRUNCATE` on all Orbit-owned tables in the user's database (not the user's other data).
3. "Disconnect cloud" removes the Orbit connection string from the device but leaves cloud data intact for the user to inspect or delete at their leisure via their provider.

### US-006-005 — Multi-device sync as a side effect (Priority: P2)

As a user with Orbit on two devices both configured against the same cloud, my envelopes appear on both devices and stay in sync.

**Acceptance**:
1. A new envelope sealed on device A appears on device B within 30 seconds when B has network.
2. Conflict resolution: last-writer-wins on non-content fields (intent reassignment); content itself is never mutated post-seal (constitution Principle III).
3. Audit logs do NOT merge — each device retains its own local audit log.

---

## Non-Goals (v1.2)

- No Orbit-hosted cloud tier. Every user brings their own cloud. (Orbit Cloud managed tier is spec 008, future.)
- No sync for raw screenshot bytes (too large, too risky). Only extracted OCR text and thumbnails, configurably.
- No peer-to-peer device sync.
- No offline-first conflict resolution beyond last-writer-wins on intent. Content is append-only per constitution.

---

## Functional Requirements (initial)

- **FR-006-001**: System MUST expose a `LocalRoomPlusCloudMirrorBackend` implementation of `EnvelopeStorageBackend` (002 T025c) that writes to local Room first, returns success to the caller, and enqueues an asynchronous cloud mirror job.
- **FR-006-002**: Cloud mirror jobs MUST originate from the `:net` process; `:ml` never directly touches cloud connection strings.
- **FR-006-003**: System MUST provide Postgres (via Supabase, Neon, self-hosted) as the first cloud backend. Firestore backend is a second FR once the Postgres path is stable.
- **FR-006-004**: System MUST include a migration tool that runs Orbit's schema on the user's database at setup time and during upgrades. Schema is versioned.
- **FR-006-005**: System MUST NEVER sync the audit log to cloud.
- **FR-006-006**: System MUST audit-log every cloud read, every cloud write, every failed attempt, and every schema migration, keeping the audit log on-device per FR-006-005.
- **FR-006-007**: System MUST handle cloud connection strings as secret material — stored encrypted in Keystore-wrapped `EncryptedSharedPreferences` scoped to `:net`, never logged in plain text, never included in crash reports.
- **FR-006-008**: System MUST default every sync toggle OFF.
- **FR-006-009**: System MUST provide a one-tap "Delete all cloud data" destructive action that runs `TRUNCATE` on Orbit tables in the user's database.

---

## Storage model (initial)

Postgres schema (v1.2 initial):

```
-- All tables owned by the user's role (typically `postgres` or `authenticator`);
-- Orbit never takes ownership.
CREATE TABLE orbit_envelope (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    day_local DATE NOT NULL,
    content_type TEXT NOT NULL,
    intent TEXT NOT NULL,
    intent_source TEXT NOT NULL,
    text_content TEXT,
    state_snapshot JSONB NOT NULL,
    intent_history JSONB NOT NULL,
    archived_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE orbit_continuation_result (
    envelope_id UUID REFERENCES orbit_envelope(id),
    continuation_type TEXT NOT NULL,
    result JSONB NOT NULL,
    model_provenance TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (envelope_id, continuation_type)
);

CREATE TABLE orbit_embedding (
    envelope_id UUID REFERENCES orbit_envelope(id),
    chunk_index INT NOT NULL,
    vector VECTOR(768),  -- pgvector extension required
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (envelope_id, chunk_index)
);

-- Knowledge graph tables added in spec 007.
```

Firestore backend uses equivalent collection structure; knowledge graph support in Firestore is limited (no recursive queries) and documented as a quality tradeoff.

---

## Dependencies

- 002 T025c–T025d (EnvelopeStorageBackend abstraction)
- 002 T025 (NetworkGatewayService in `:net`)
- New: `com.capsule.app.net.cloud.PostgresClient` with pgvector support
- New: `com.capsule.app.net.cloud.FirestoreClient` (second iteration)
- New: `com.capsule.app.settings.CloudStorageScreen`
- New: Migration tool `com.capsule.app.net.cloud.SchemaMigrator`

---

## Open Questions

- For Supabase/Neon OAuth flow (vs. paste-connection-string), do we register Orbit as a partner app with each provider? (Deferred to v2.0 unless a partner reaches out.)
- Do we allow cloud-only mode for users who explicitly opt out of local storage? Constitutional answer: **no**. Local is always the source of truth.
- How do we handle device rotation (user replaces phone)? Do they re-seed local from cloud on first launch? (Leaning yes; add a "Restore from cloud" flow for new devices with existing cloud data.)

---

*Targeted for v1.2 after v1.1 stabilizes. Constitutionally bounded by Principle X.*
