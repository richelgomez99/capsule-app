# BYOC Sovereign Storage (v1.3)

**Status**: DRAFT — targets v1.3
**Depends on**: spec 006 (Orbit Cloud) shipped and schema-stable
**Governing document**: `.specify/memory/constitution.md` — implements Principle X Model B
**Created**: 2026-04-17
**Last amended**: 2026-04-20 (renumbered from 006 to 009 under constitution v3.0.0; retargeted from v1.2 to v1.3)

---

## Summary

BYOC ("Bring Your Own Cloud") Sovereign Storage lets a power user point Orbit
at a Postgres database they legally own — Supabase, Neon, a self-hosted
instance, Hetzner, etc. — and have Orbit write the same schema and the same
data it would write to Orbit Cloud, except that the user holds the admin
credentials and pays the provider bill directly.

The schema matches spec 006 (Orbit-hosted Cloud) exactly so that migration
in either direction (Tier 1 ↔ Tier 2) is lossless. A user who starts on
Orbit Cloud and later wants full sovereignty can flip a switch and run a
schema-identical backfill into their own instance; a user who starts on
BYOC and wants Orbit to take over ops can do the reverse.

The core commitment encoded by Principle X Model B: **the data and the
account both belong to the user, not to Orbit.** Orbit provides the schema,
the migration script, and the sync logic; the user provides the
infrastructure.

This spec replaces the v1.2-targeted former spec 006 (paste-connection-
string design). It is now a post-v1 power-user feature that exists for users
who explicitly want sovereignty over the cloud tier and are willing to
administer a Postgres instance.

---

## User Stories

### US-009-001 — Paste-a-connection-string onboarding (Priority: P1)

As a user who created a free Neon or Supabase project, I open Settings →
Cloud Storage → Advanced → BYOC, pick my provider type, paste my Postgres
connection string, and tap "Set up." Orbit verifies the connection, runs
the migration, and I can now opt into syncing specific data categories to
my own database instead of Orbit Cloud.

**Acceptance**:

1. Settings → Cloud Storage shows the Orbit-managed option as the default,
   with an "Advanced → Use my own database" path for BYOC.
2. The BYOC flow includes a "Create a free Neon account" / "Create a free
   Supabase project" link and a connection-string field.
3. Submitting a valid connection string verifies the connection, confirms
   the Postgres version and extensions available (pgvector required),
   runs schema migrations, and confirms success.
4. Submitting an invalid connection string shows a clear error and does
   not persist.
5. Connection string is stored encrypted in Android Keystore, accessible
   only inside the `:net` process.

### US-009-002 — Opt-in per data category (Priority: P1)

As a user with BYOC configured, I toggle individual data categories
independently: envelopes, embeddings, knowledge graph, profile facts,
continuation results, agent memory. The audit log and consent ledger are
never syncable — they stay local forever (Principle X non-negotiable
condition #2).

**Acceptance**:

1. Independent toggles per data category. Default: all off.
2. No toggle for `AUDIT_LOG` or `CONSENT_LEDGER`. The UI explicitly notes
   "Your audit log and consent ledger always stay on this device."
3. Toggling a category ON triggers a one-time backfill continuation that
   uploads existing data; toggling OFF stops future sync (does not delete
   cloud rows — that requires a separate "Delete from cloud" action).
4. Profile facts and KG structures tagged `local_only` are never uploaded
   regardless of toggle state (enforced on-device per Principle XI).

### US-009-003 — Local is authoritative (Priority: P1)

As a user whose cloud goes down, whose connection string is revoked, or
who deletes their cloud project, Orbit keeps working with zero functional
loss.

**Acceptance**:

1. Cloud sync failures do not block local writes.
2. Cloud unreachable for > 24 hours surfaces a non-blocking banner in
   Settings.
3. Removing the BYOC configuration removes every cloud-path code branch
   from runtime.

### US-009-004 — Migrate from Orbit Cloud to BYOC (Priority: P1)

As a user currently on Orbit Cloud who wants full sovereignty, I can
export my Orbit Cloud data and import it into my own Postgres instance
without feature loss.

**Acceptance**:

1. Settings → Cloud Storage → "Export to BYOC" triggers a one-shot
   schema-identical dump from Orbit Cloud to the user's connected BYOC
   instance.
2. After successful import, Orbit Cloud is automatically placed in
   read-only mode pending the user's confirmation to purge.
3. Confirmed purge deletes the Orbit Cloud tenant schema within 72 hours
   and records the event in the local audit log.

### US-009-005 — Multi-device sync as a side effect (Priority: P2)

As a user with Orbit on two devices both configured against the same
BYOC database, my envelopes and agent memory appear on both devices and
stay in sync.

**Acceptance**:

1. A new envelope sealed on device A appears on device B within 30
   seconds when B has network.
2. Conflict resolution: last-writer-wins on non-content fields (intent
   reassignment); content itself is never mutated post-seal (Principle
   III).
3. Audit logs and consent ledgers do NOT merge — each device retains its
   own.

---

## Non-Goals (v1.3)

- No Firestore or non-Postgres backend. The schema leans on pgvector and
  recursive CTEs; porting to NoSQL costs feature parity with Tier 1.
- No automatic BYOC provisioning (no OAuth "create a Neon project for me"
  flow). If a user wants BYOC, they create the database themselves.
- No sync for raw screenshot bytes by default. Thumbnails and OCR text
  only (matches spec 006 default).
- No peer-to-peer device sync.

---

## Functional Requirements

- **FR-009-001**: System MUST expose a `ByocPostgresBackend` implementation
  of `EnvelopeStorageBackend` (002 T025c) that writes to local Room first,
  returns success to the caller, and enqueues an asynchronous BYOC mirror
  job.
- **FR-009-002**: BYOC mirror jobs MUST originate from the `:net` process;
  `:ml` never directly touches connection strings.
- **FR-009-003**: System MUST provide Postgres (Supabase, Neon,
  self-hosted, Hetzner) as the only supported BYOC backend. Required
  extensions: pgvector.
- **FR-009-004**: System MUST include a migration tool that runs the spec
  006 schema on the user's database at setup time and during upgrades.
- **FR-009-005**: System MUST NEVER sync the audit log or the consent
  ledger, regardless of user toggle state.
- **FR-009-006**: System MUST audit-log every BYOC read, every BYOC write,
  every failed attempt, and every schema migration.
- **FR-009-007**: System MUST handle BYOC connection strings as secret
  material — stored encrypted in Keystore-wrapped
  `EncryptedSharedPreferences` scoped to `:net`, never logged, never in
  crash reports.
- **FR-009-008**: System MUST default every BYOC sync toggle OFF.
- **FR-009-009**: System MUST provide a one-tap "Delete all cloud data"
  destructive action that runs `DROP SCHEMA orbit CASCADE` on Orbit tables
  in the user's database.
- **FR-009-010**: System MUST enforce Principle XI consent filtering on
  every BYOC write; `local_only` facts are structurally blocked.
- **FR-009-011**: System MUST enforce Principle XII provenance on every
  BYOC write; derived facts without episode references are rejected.
- **FR-009-012**: System MUST support a lossless Tier 1 → Tier 2 migration
  path (Orbit Cloud → BYOC) and a Tier 2 → Tier 1 migration path (BYOC →
  Orbit Cloud) via the shared schema.

---

## Storage model

Identical to spec 006 (Orbit Cloud). Refer to
`specs/006-orbit-cloud-storage/spec.md` → Storage Model section. The only
differences are operational:

- In BYOC, the `orbit_<user_id>` schema and role exist in the user's
  database and are created by Orbit's migration tool.
- Per-user DEK for ciphertext columns is still managed on-device; the
  user's KMS (if any) is not used because Orbit does not assume one
  exists on arbitrary providers.
- The user is the database admin and may `DROP`, `REVOKE`, or modify
  Orbit's schema at any time. Orbit treats such changes as
  "disconnection" and falls back to Tier 0.

---

## Dependencies

- 002 T025c–T025d (EnvelopeStorageBackend abstraction)
- 002 T025 (NetworkGatewayService in `:net`)
- 006 shipped with schema stable (BYOC schema mirrors 006)
- New: `com.capsule.app.net.cloud.ByocPostgresClient` with pgvector support
- New: `com.capsule.app.settings.ByocStorageScreen`
- New: Migration tool `com.capsule.app.net.cloud.SchemaMigrator` (shared
  with 006)

---

## Open Questions

- Do we require the user to create the `orbit_<user_id>` schema and role,
  or does the migration tool bootstrap them using superuser credentials?
  Leaning toward "require superuser at setup, drop to limited role at
  runtime."
- Do we allow BYOC users to disable per-user DEK ciphertext columns
  (trusting their own database encryption at rest)? Leaning no —
  consistency with spec 006 matters for the lossless migration promise.
- How do we handle device rotation (user replaces phone)? Re-seed local
  from BYOC on first launch. Same mechanism as spec 006.

---

*Targeted for v1.3 after spec 006 (Orbit Cloud) stabilizes.
Constitutionally bounded by Principle X Model B.*
