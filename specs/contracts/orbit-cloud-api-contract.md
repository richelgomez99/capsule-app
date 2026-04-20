# Orbit Cloud API Contract

**Status**: DRAFT — targets spec 006 v1.1
**Governing document**: `.specify/memory/constitution.md` — Principles X, XI, XII
**Created**: 2026-04-20

---

## Scope

Defines the HTTPS binder between the Orbit Android client (`:net`
process) and the Orbit Cloud managed Postgres backend. Every endpoint
operates strictly on the caller's `orbit_<user_id>` schema; there is
no cross-tenant surface.

All requests MUST carry an Orbit identity token (short-lived JWT)
minted by the Orbit auth service. All payloads crossing the wire MUST
have passed the Principle XI consent filter inside the client's
`:agent` process; `:net` is a dumb transport.

All ciphertext columns are encrypted client-side with the user's DEK
(see `envelope-content-encryption-contract.md`); the server stores
opaque bytes.

## Conventions

- Base URL: `https://cloud.orbit.app/api/v1`
- Auth: `Authorization: Bearer <orbit-identity-jwt>`
- Tenant: derived from the JWT's `sub` claim; never accepted as a
  path or body parameter.
- Content-Type: `application/json`; binary ciphertext blobs
  base64-encoded.
- Idempotency: every mutation endpoint accepts an
  `Idempotency-Key` header (client-generated UUID).
- Errors: RFC 7807 problem+json. Quota exceeded returns HTTP 429
  with `Retry-After`.

## Endpoints

### `POST /envelopes`

Upload a new envelope (encrypted body and media refs, plaintext
metadata).

Request:
```json
{
  "id": "uuid",
  "device_id": "uuid",
  "created_at": "iso8601",
  "day_local": "yyyy-mm-dd",
  "content_type": "text|image|url|mixed",
  "intent": "string",
  "intent_source": "user|nano|managed|byok",
  "body_ct": "base64",
  "media_ref_ct": "base64|null",
  "ocr_ct": "base64|null",
  "transcript_ct": "base64|null",
  "state_snapshot": { },
  "intent_history": [ ]
}
```

Response: `201 Created`, body echoes server-assigned fields
(`updated_at`).

### `POST /episodes`

Record a raw-source episode for Principle XII provenance.

Request:
```json
{
  "id": "uuid",
  "source_envelope_id": "uuid|null",
  "source_kind": "capture|continuation|agent_action|user_edit|import",
  "raw_payload_ct": "base64",
  "occurred_at": "iso8601",
  "recorded_at": "iso8601"
}
```

Response: `201 Created`.

### `POST /profile-facts`

Upsert profile subgraph edges. Rejected if `episode_id` is missing
or if any fact is tagged `local_only`.

Request:
```json
{
  "facts": [
    {
      "id": "uuid",
      "predicate": "string",
      "object_ct": "base64",
      "weight": 1.0,
      "sensitivity": "public_to_orbit",
      "valid_from": "iso8601",
      "valid_to": "iso8601|null",
      "episode_id": "uuid"
    }
  ]
}
```

Response: `200 OK` with `{ "accepted": N, "rejected": [ { "id":..., "reason":... } ] }`.

### `POST /agent-state`

Write or update the current agent session state (JSONB ciphertext).

Request:
```json
{
  "session_id": "uuid",
  "opened_at": "iso8601",
  "closed_at": "iso8601|null",
  "context_jsonb_ct": "base64"
}
```

Response: `200 OK`.

### `GET /continuations?state=<value>`

Fetch continuation results scoped to the tenant filtered by a state
snapshot key (intent, day, or explicit state id). Returns ciphertext
results; client decrypts with the DEK.

### `GET /kg/neighbors?node_id=<uuid>&hops=<n>&as_of=<iso8601>`

Recursive-CTE neighbor fetch honoring bi-temporal bounds
(`valid_from <= as_of` AND (`valid_to IS NULL OR valid_to > as_of`)
AND `invalidated_at IS NULL OR invalidated_at > as_of`).
`hops` capped at 3 in v1.1.

Response:
```json
{
  "nodes": [ { "id":..., "type":..., "canonical_name_ct":..., "sensitivity":... } ],
  "edges": [ { "id":..., "from_node":..., "to_node":..., "relation_type":..., "weight":..., "valid_from":..., "valid_to":..., "episode_id":... } ]
}
```

### `POST /export-request`

Kick off a GDPR-grade export job. Returns a job id; completion
delivers a signed download URL via the client's registered device
push channel within 72 hours.

Request: `{ }` (tenant derived from JWT).

Response: `202 Accepted`, `{ "job_id": "uuid" }`.

### `DELETE /tenant`

Full tenant deletion: drop `orbit_<user_id>` schema, revoke the
role, destroy the wrapped DEK in KMS. Completes within 72 hours.

Request: `{ "confirmation_phrase": "delete everything" }`

Response: `202 Accepted`, `{ "receipt_id": "uuid" }` — client records
`receipt_id` in the local audit log; no further confirmation can
reverse the action.

## Non-endpoints (forbidden by Principle X Model A)

- No cross-tenant search.
- No "list all users" surface.
- No analytics over aggregated tenant content.
- No training-data export that includes any tenant's content.

## Transparency

Aggregate non-identifying counts of exports, deletions, and consent
revocations (per spec 006 FR-006-017) are published quarterly at
`https://orbit.app/transparency`.
