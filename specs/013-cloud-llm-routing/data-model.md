# Phase 1 Data Model — Cloud LLM Routing + Supabase Backbone

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-04-28

This document defines every data structure introduced or modified by this spec: Kotlin sealed classes, AIDL parcels, runtime flags, and Supabase tables.

---

## 1. Kotlin sealed hierarchies

### 1.1 `LlmGatewayRequest` (sealed, `@Serializable`)

Package: `com.capsule.app.ai.gateway`
Wire format: kotlinx.serialization JSON (UTF-8). Discriminator: `"type"`.

| Subtype | `type` value | Fields | Notes |
|---------|--------------|--------|-------|
| `Embed` | `"embed"` | `requestId: String`, `text: String` | `requestId` is UUIDv4 string. Empty `text` MUST be filtered before crossing AIDL — caller responsibility (NanoLlmProvider already returns null on blank). |
| `Summarize` | `"summarize"` | `requestId: String`, `text: String`, `maxTokens: Int` | `maxTokens` mirrors existing `LlmProvider.summarize` parameter. |
| `ExtractActions` | `"extract_actions"` | `requestId: String`, `text: String`, `contentType: String`, `state: StateSnapshotJson`, `registeredFunctions: List<AppFunctionSummaryJson>`, `maxCandidates: Int = 3` | `StateSnapshotJson` and `AppFunctionSummaryJson` are `@Serializable` mirrors of the existing Kotlin types. Server side reconstructs them. |
| `ClassifyIntent` | `"classify_intent"` | `requestId: String`, `text: String`, `appCategory: String` | Routed to Haiku 4.5 with prompt-cached prefix. |
| `GenerateDayHeader` | `"generate_day_header"` | `requestId: String`, `dayIsoDate: String`, `envelopeSummaries: List<String>` | `dayIsoDate` is `YYYY-MM-DD`. |
| `ScanSensitivity` | `"scan_sensitivity"` | `requestId: String`, `text: String` | Routed to Haiku 4.5 with prompt-cached prefix. |

**Validation rules**:
- `requestId` MUST be a non-empty UUIDv4 string. Constructed at the `CloudLlmProvider` boundary, propagated through `LlmGatewayClient`, included in every outbound HTTP request, returned in the response. Enables SC-009 latency attribution.
- All `String` fields are unbounded at the data-model level; the gateway's 1 MB Binder limit is the only hard cap. Callers SHOULD pre-truncate large payloads.

### 1.2 `LlmGatewayResponse` (sealed, `@Serializable`)

Package: `com.capsule.app.ai.gateway`
Discriminator: `"type"`. Every response also carries the originating `requestId` for trace correlation.

| Subtype | `type` value | Fields | Notes |
|---------|--------------|--------|-------|
| `EmbedResponse` | `"embed_response"` | `requestId: String`, `vector: FloatArray`, `modelLabel: String` | **Overrides `equals`/`hashCode` using `vector.contentEquals` / `contentHashCode`**. `modelLabel` is opaque (e.g. `"openai/text-embedding-3-small@2026-04"`); used by the cluster engine for label-version drift detection (Spec 002 FR-038/039). |
| `SummarizeResponse` | `"summarize_response"` | `requestId: String`, `summary: String`, `modelLabel: String` | |
| `ExtractActionsResponse` | `"extract_actions_response"` | `requestId: String`, `proposals: List<ActionProposalJson>`, `modelLabel: String` | Mirrors existing `ActionExtractionResult`. |
| `ClassifyIntentResponse` | `"classify_intent_response"` | `requestId: String`, `intent: String`, `confidence: Float`, `modelLabel: String` | |
| `GenerateDayHeaderResponse` | `"generate_day_header_response"` | `requestId: String`, `header: String`, `modelLabel: String` | |
| `ScanSensitivityResponse` | `"scan_sensitivity_response"` | `requestId: String`, `tags: List<String>`, `modelLabel: String` | |
| `Error` | `"error"` | `requestId: String`, `code: String`, `message: String` | `code` values: `NETWORK_UNAVAILABLE`, `GATEWAY_5XX`, `PROVIDER_5XX`, `TIMEOUT`, `MALFORMED_RESPONSE`, `UNAUTHORIZED`, `INTERNAL`. |

**Validation rules**:
- Every response except `Error` MUST carry a non-empty `modelLabel`. The Edge Function is the source of truth for the label string.
- `Error.code` values are an open enum at the data-model level; the contract document enumerates the Day-1 set.

### 1.3 Error mapping at `CloudLlmProvider` boundary

`CloudLlmProvider` MUST translate `LlmGatewayResponse.Error` to the asymmetric `LlmProvider` error contract:

| Method | On `Error` (any code) | Rationale |
|--------|----------------------|-----------|
| `embed()` | return `null` | Preserves Spec 002's graceful-degrade contract; cluster worker must not throw mid-loop. |
| `summarize()` | throw `IOException(error.code + ": " + error.message)` | Existing contract. |
| `extractActions()` | throw `IOException(error.code + ": " + error.message)` | Existing contract. |
| `classifyIntent()` | throw `IOException(...)` | Existing contract. |
| `generateDayHeader()` | throw `IOException(...)` | Existing contract. |
| `scanSensitivity()` | throw `IOException(...)` | Existing contract. |

### 1.4 Supporting `@Serializable` types

| Type | Source | Notes |
|------|--------|-------|
| `StateSnapshotJson` | mirror of `com.capsule.app.data.entity.StateSnapshot` | New `@Serializable` data class in `com.capsule.app.ai.gateway`. Field-for-field copy. |
| `AppFunctionSummaryJson` | mirror of `com.capsule.app.ai.model.AppFunctionSummary` | Same approach. |
| `ActionProposalJson` | mirror of the existing `ActionProposal` shape | Same approach. |

The mirrors exist (rather than `@Serializable`-annotating the originals) so the AI Gateway wire format is structurally decoupled from internal model evolution. Conversion is mechanical and lives in `CloudLlmProvider` / `NetworkGatewayImpl`.

---

## 2. AIDL parcels

### 2.1 `LlmGatewayRequestParcel`

Package: `com.capsule.app.net.ipc`
Implements `android.os.Parcelable`.

```kotlin
data class LlmGatewayRequestParcel(
    val payloadJson: String,   // kotlinx.serialization Json.encodeToString(LlmGatewayRequest, value)
) : Parcelable
```

**`writeToParcel`**: writes `payloadJson` via `parcel.writeString`.
**`CREATOR`**: reads `payloadJson` via `parcel.readString()`. Constructs the parcel with non-null payload (throws on null — caller invariant).
**`describeContents`**: returns `0`.

### 2.2 `LlmGatewayResponseParcel`

Package: `com.capsule.app.net.ipc`
Implements `android.os.Parcelable`.

```kotlin
data class LlmGatewayResponseParcel(
    val payloadJson: String,   // kotlinx.serialization Json.encodeToString(LlmGatewayResponse, value)
) : Parcelable
```

Same wire shape as the request parcel: a single String field carrying the JSON-encoded sealed-class instance. Mirrors `FetchResultParcel`'s minimal Parcelable boilerplate (manual `writeToParcel` + `CREATOR`).

**Why a single String, not per-field columns**: the sealed class has six request shapes and seven response shapes. Per-field Parcelable would require either polymorphic Parcelable (not natively supported) or an enum tag + nullable-everywhere-else schema (brittle when fields evolve). JSON-in-String is the canonical Android workaround for this exact problem.

---

## 3. AIDL surface (modified)

File: `app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl`

```aidl
package com.capsule.app.net.ipc;

import com.capsule.app.net.ipc.FetchResultParcel;
import com.capsule.app.net.ipc.LlmGatewayRequestParcel;
import com.capsule.app.net.ipc.LlmGatewayResponseParcel;

interface INetworkGateway {
    FetchResultParcel fetchPublicUrl(String url, long timeoutMs);   // existing — UNCHANGED
    LlmGatewayResponseParcel callLlmGateway(in LlmGatewayRequestParcel request);   // NEW
}
```

The two new parcel types each need a sibling AIDL file declaring them as parcelables: `app/src/main/aidl/com/capsule/app/net/ipc/LlmGatewayRequestParcel.aidl` and `LlmGatewayResponseParcel.aidl` containing `parcelable LlmGatewayRequestParcel;` and `parcelable LlmGatewayResponseParcel;` respectively (Android's standard pattern, mirrors how `FetchResultParcel` is declared).

---

## 4. `RuntimeFlags` (modified)

File: `app/src/main/java/com/capsule/app/RuntimeFlags.kt`
Package: `com.capsule.app` (existing — DO NOT introduce a new `runtime/` package).

Added fields (preserve existing `clusterModelLabelLock`):

```kotlin
@Volatile
@JvmStatic
var useLocalAi: Boolean = false
    // SharedPreferences key: "cloud.use_local_ai"
    // Default: false (cloud is the default per D-001 / FR-013-002)

@Volatile
@JvmStatic
var clusterEmitEnabled: Boolean = true
    // SharedPreferences key: "cluster.emit_enabled"
    // Default: true
    // Read by ClusterDetectionWorker as a runtime kill switch for cluster card surfacing
```

**Persistence**: same `SharedPreferences` mechanism the existing `clusterModelLabelLock` uses. Reads happen at boot and on flag change; the in-memory `@Volatile` field is the hot-path read.

---

## 5. Supabase tables (server-side, hybrid plaintext + reserved `*_ct bytea` per Round 2 D-005)

Migration files (server-side only — Day 1 has zero Android writes to these tables):
- `supabase/migrations/00000000_initial_schema.sql` — schema + pgvector
- `supabase/migrations/00000001_rls_policies.sql` — RLS per [supabase-rls-contract.md](contracts/supabase-rls-contract.md)
- `supabase/migrations/00000002_cluster_membership_check.sql` — FR-032 enforcement per [supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md)

Common columns on every table:
- `id uuid primary key default gen_random_uuid()`
- `user_id uuid not null references auth.users(id) on delete cascade`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

Common RLS policy on every table (one policy per CRUD verb, four policies total):
```sql
CREATE POLICY "<verb>_own_rows" ON <table> FOR <SELECT|INSERT|UPDATE|DELETE>
  USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
```

### 5.1 `envelopes`
| Column | Type | Class | Notes |
|--------|------|-------|-------|
| `id` | `uuid` | plaintext-indexable | PK |
| `user_id` | `uuid` | plaintext-indexable | RLS |
| `device_id` | `text` | plaintext-indexable | |
| `created_at` | `timestamptz` | plaintext-indexable | |
| `updated_at` | `timestamptz` | plaintext-indexable | |
| `day_local` | `date` | plaintext-indexable | |
| `content_type` | `text` | plaintext-indexable | |
| `intent` | `text` | plaintext-indexable | |
| `intent_source` | `text` | plaintext-indexable | |
| `state_snapshot` | `jsonb` | plaintext | |
| `intent_history` | `jsonb` | plaintext | |
| `body` | `text` | plaintext | Day-1 writes; spec 006 migrates to `body_ct`. |
| `ocr_text` | `text` | plaintext | Day-1 writes; spec 006 migrates to `ocr_ct`. |
| `transcript` | `text` | plaintext | Day-1 writes; spec 006 migrates to `transcript_ct`. |
| `media_ref` | `text` | plaintext | Day-1 writes; spec 006 migrates to `media_ref_ct`. |
| `body_ct` | `bytea` (NULL) | ciphertext (reserved) | Spec 006. |
| `ocr_ct` | `bytea` (NULL) | ciphertext (reserved) | Spec 006. |
| `transcript_ct` | `bytea` (NULL) | ciphertext (reserved) | Spec 006. |
| `media_ref_ct` | `bytea` (NULL) | ciphertext (reserved) | Spec 006. |

RLS: 4 policies on `auth.uid() = user_id`.

### 5.2 `continuations`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `envelope_id` | `uuid not null references envelopes(id) on delete cascade` | |
| `continuation_type` | `text not null` | e.g. `url_hydrate`, `summarise`, `extract_actions`. |
| `status` | `text not null` | `pending` / `running` / `done` / `error`. |
| `attempt_count` | `int not null default 0` | |

No ciphertext columns. RLS: 4 policies.

### 5.3 `continuation_results`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `continuation_id` | `uuid not null references continuations(id) on delete cascade` | |
| `envelope_id` | `uuid not null references envelopes(id) on delete cascade` | |
| `model_provenance` | `text` | model label used to produce result. |
| `result_json` | `jsonb` | plaintext, Day-1. |
| `result_ct` | `bytea` (NULL) | ciphertext (reserved, spec 006). |

RLS: 4 policies.

### 5.4 `clusters`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `summary` | `text` | plaintext, Day-1. |
| `summary_ct` | `bytea` (NULL) | ciphertext (reserved, spec 006). |
| `embedding` | `vector(1536)` | plaintext (one-way projection per encryption contract). |
| `model_label` | `text not null` | label that produced the cluster. |
| `member_count` | `int not null default 0` | |

RLS: 4 policies. `pgvector` extension required.

### 5.5 `cluster_members`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `cluster_id` | `uuid not null references clusters(id) on delete cascade` | |
| `envelope_id` | `uuid not null references envelopes(id) on delete cascade` | |
| `excerpt` | `text` | plaintext, Day-1. |
| `excerpt_ct` | `bytea` (NULL) | ciphertext (reserved, spec 006). |
| `embedding` | `vector(1536)` | plaintext. |

RLS: 4 policies. **Unique constraint** on `(cluster_id, envelope_id)` to support the cluster-membership CHECK in §5.10.

### 5.6 `action_proposals`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `envelope_id` | `uuid not null references envelopes(id) on delete cascade` | |
| `function_id` | `text not null` | |
| `args_json` | `jsonb not null` | plaintext, Day-1. |
| `confidence` | `real not null` | |
| `state` | `text not null` | `pending` / `confirmed` / `rejected` / `executed`. |
| `payload_ct` | `bytea` (NULL) | ciphertext (reserved, spec 006). |

RLS: 4 policies.

### 5.7 `action_executions`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at`, `updated_at` | common columns | |
| `proposal_id` | `uuid not null references action_proposals(id) on delete cascade` | |
| `outcome` | `text not null` | `success` / `failure` / `partial`. |
| `result_json` | `jsonb` | plaintext, Day-1. |
| `result_ct` | `bytea` (NULL) | ciphertext (reserved, spec 006). |
| `error_message` | `text` | plaintext for now; spec 006 will scrub. |

RLS: 4 policies.

### 5.8 `audit_log_entries`
| Column | Type | Notes |
|--------|------|-------|
| `id`, `user_id`, `created_at` | common columns (no `updated_at` — audit is append-only) | |
| `event_type` | `text not null` | |
| `actor` | `text not null` | |
| `subject_id` | `uuid` | nullable; row reference. |
| `details_json` | `jsonb` | plaintext metadata. |

RLS: SELECT and INSERT only — UPDATE and DELETE policies MUST evaluate to `false` (audit is structurally append-only per Constitution Principle XII). The audit log on the device itself is **never uploaded** per Constitution Principle X; this server-side `audit_log_entries` table is reserved for cloud-action mirrors that downstream specs will populate.

### 5.9 `user_profiles`
| Column | Type | Notes |
|--------|------|-------|
| `id` | `uuid primary key` (= `auth.users.id`) | |
| `user_id` | `uuid not null references auth.users(id) on delete cascade` | redundant with `id` for RLS uniformity. |
| `created_at`, `updated_at` | common columns | |
| `display_name` | `text` | plaintext. |
| `settings_json` | `jsonb` | plaintext. |

RLS: 4 policies.

### 5.10 Cluster-membership CHECK (cross-table invariant — FR-032 server-side)
Migration file: `supabase/migrations/00000002_cluster_membership_check.sql`.
Implementation: a Postgres trigger on `clusters` (UPDATE of `summary`) that walks any `summary` field carrying cited envelope IDs and verifies each cited ID has a matching row in `cluster_members` for the same `cluster_id`. (A pure CHECK constraint cannot reference another table; trigger is the structurally correct mechanism — see [supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md) for the full implementation.)

---

## 6. Cross-references

| Concept | File |
|---------|------|
| `LlmProvider` interface (modified doc-comment) | [LlmProvider.kt](../../app/src/main/java/com/capsule/app/ai/LlmProvider.kt) |
| Existing AIDL surface | [INetworkGateway.aidl](../../app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl) |
| Existing parcel pattern | [FetchResultParcel.kt](../../app/src/main/java/com/capsule/app/net/ipc/FetchResultParcel.kt) |
| Existing `:net` impl | [NetworkGatewayImpl.kt](../../app/src/main/java/com/capsule/app/net/NetworkGatewayImpl.kt) |
| Existing `RuntimeFlags` | [RuntimeFlags.kt](../../app/src/main/java/com/capsule/app/RuntimeFlags.kt) |
| Encryption contract (defines `*_ct` column names) | [envelope-content-encryption-contract.md](../contracts/envelope-content-encryption-contract.md) |
