# Envelope Content Encryption Contract

**Status**: DRAFT — targets spec 006 v1.1
**Governing document**: `.specify/memory/constitution.md` — Principle X Model A, Principle XI
**Created**: 2026-04-20

---

## Scope

Defines which envelope and knowledge-graph fields are stored
ciphertext vs. plaintext in Orbit Cloud (spec 006) and BYOC (spec
009), what key is used, where each key lives, and how `local_only`
facts are structurally prevented from ever crossing the device
boundary.

## Key hierarchy

```
Orbit KMS (HSM-backed)
   └── KEK_<user_id>   (per-user Key Encryption Key, lives in KMS)
          └── DEK_<user_id>   (per-user Data Encryption Key,
                              wrapped blob stored server-side,
                              plaintext DEK resident on-device only
                              after unwrap)
```

- **KEK**: generated server-side per user on Orbit Cloud enable.
  Never leaves KMS. Used only to wrap/unwrap the DEK.
- **DEK**: 256-bit AES key, generated server-side at enable, wrapped
  by KEK, stored as an opaque blob in the tenant's settings table.
  The client fetches the wrapped DEK on sign-in, asks the server to
  unwrap it (server calls KMS), then holds the plaintext DEK in
  Android Keystore-protected memory for the session.
- **Device root key**: Android Keystore-held key that protects the
  DEK when cached at rest on the device.
- **BYOC variant**: In spec 009 (Tier 2), the client manages DEK
  generation and storage on-device; no KMS is assumed on the user's
  Postgres provider. Schema-identical ciphertext columns remain.

## Encryption envelope

Ciphertext columns use AES-256-GCM with a per-row 96-bit nonce:

```
ct_column = nonce || ciphertext || tag
```

Associated data (AAD) MUST include the tenant id, table name, column
name, and row id. This binds ciphertext to its row and prevents
copy-paste attacks between tables.

## Field classification

Three classes of fields:

1. **Ciphertext-only (DEK-encrypted)** — content the server has no
   legitimate reason to read.
2. **Plaintext-indexable** — metadata required for routing, filtering,
   and constitutional enforcement (tenant isolation, bi-temporal
   bounds, audit).
3. **Local-only (never uploaded)** — structurally blocked at the
   `:agent` consent filter.

### `envelopes`

| Field | Class |
| --- | --- |
| `id`, `device_id`, `created_at`, `day_local`, `updated_at` | plaintext-indexable |
| `content_type`, `intent`, `intent_source` | plaintext-indexable |
| `state_snapshot`, `intent_history` (non-sensitive) | plaintext |
| `body_ct`, `media_ref_ct`, `ocr_ct`, `transcript_ct` | ciphertext |
| Raw screenshot bytes | NOT uploaded by default (user must opt-in per capture) |

### `embeddings`

| Field | Class |
| --- | --- |
| `envelope_id`, `chunk_index`, `created_at` | plaintext-indexable |
| `vector` | plaintext (vectors are one-way projections; uploadable) |

### `continuation_results`

| Field | Class |
| --- | --- |
| `envelope_id`, `continuation_type`, `model_provenance`, `created_at` | plaintext-indexable |
| `result_ct` | ciphertext |

### `kg_nodes`

| Field | Class |
| --- | --- |
| `id`, `type`, `sensitivity`, `created_at`, `updated_at` | plaintext-indexable |
| `embedding` | plaintext |
| `canonical_name_ct`, `aliases_ct` | ciphertext |
| Nodes tagged `sensitivity='local_only'` | NEVER uploaded |

### `kg_edges`

| Field | Class |
| --- | --- |
| All columns | plaintext-indexable (needed for traversal) |
| Edges tagged `sensitivity='local_only'` | NEVER uploaded |

### `profile_facts`

| Field | Class |
| --- | --- |
| `id`, `predicate`, `weight`, `sensitivity`, `valid_from`, `valid_to`, `invalidated_at`, `episode_id` | plaintext-indexable |
| `object_ct` | ciphertext |
| Facts tagged `sensitivity='local_only'` | NEVER uploaded |

### `agent_state`, `plans`, `skill_usage`

| Field | Class |
| --- | --- |
| `session_id`, `plan_id`, `status`, timestamps, `outcome_episode_id`, `latency_ms` | plaintext-indexable |
| `context_jsonb_ct`, `goal_ct`, `steps_jsonb_ct` | ciphertext |

### Never uploaded, ever (regardless of user preference)

- Local audit log (every row, every column).
- Consent ledger (every row, every column).
- OAuth tokens for external integrations (calendar, email, etc.).
- Any node, edge, or fact tagged `sensitivity='local_only'`.
- Original full-resolution screenshot or media bytes unless the user
  explicitly uploaded that specific capture.

## Principle XI consent filter (enforcement)

Before any payload crosses from `:agent` to `:net`:

1. Walk the payload; fail closed if any entry carries
   `sensitivity='local_only'`.
2. Verify the user's most recent consent for this sensitivity tier
   is still within the freshness window (default 90 days for
   `public_to_orbit`; configurable).
3. Check the sensitivity vs. the chosen provider's declared policy
   (Orbit-managed proxy has the strictest policy; BYOK vendors
   follow their own policies).
4. Verify necessity: the payload MUST be the minimum scoped to the
   capability's declared need; anything not required by the active
   capability's schema is redacted.

Every filter outcome (pass, redaction, block) is recorded in the
local audit log with a `consent_filter_hash` so the decision can be
reconstructed later.

## Rotation

- DEK rotation: user-initiated from Settings → Cloud Storage →
  Rotate key. The server generates a new DEK, wraps with KEK,
  lazy-rewrites ciphertext columns in the background, and deletes
  the old wrapped DEK after successful rewrite.
- KEK rotation: operationally scheduled (quarterly). Transparent to
  users; re-wraps existing DEK.
- BYOC variant: rotation is client-driven; client rewrites columns
  in place.

## Deletion

Full tenant deletion (spec 006 FR-006-015) MUST destroy the wrapped
DEK in KMS. Once the wrapped DEK is destroyed and the KEK is rotated
(quarterly boundary), historical ciphertext is cryptographically
unrecoverable even from backups, meeting GDPR erasure.

## Non-goals

- No homomorphic encryption for server-side queries in v1.1.
- No client-side-only encryption of indexing columns. Trade-off:
  indexable metadata is plaintext so the server can route and
  enforce tenant isolation.
- No user-supplied DEK (HYOK) in v1.1. Revisit if enterprise
  demand emerges.
