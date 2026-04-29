# Gateway Request/Response Contract (Day 2)

**Boundary**: Android `:net` (`LlmGatewayClient`) ↔ Vercel Edge Function at
`https://<project>.vercel.app/llm` (production) or `http://localhost:3000/llm` (local dev).
**Status**: STABLE for Day 2.
**Wire format**: HTTPS POST, JSON body, UTF-8.

This contract is the **server-side counterpart** of
[specs/013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md](../../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md).
The wire shape is unchanged. This document specifies the **server's obligations**: which model
each `type` routes to, how upstream errors map to our seven `ErrorCode` values, and HTTP
status code semantics.

---

## 1. Endpoint

`POST /llm` — single endpoint, no path parameters, no query string.

### Required request headers

| Header | Value | Notes |
|--------|-------|-------|
| `Content-Type` | `application/json; charset=utf-8` | Function rejects other content types as `INTERNAL`. |
| `Authorization` | `Bearer <supabase-jwt>` | Required (Day 2 closes Day-1 grace). See [auth-jwt-contract.md](auth-jwt-contract.md). |
| `X-Orbit-Request-Id` | UUIDv4, identical to body's `requestId` | Optional but recommended for trace correlation in Vercel logs. |

---

## 2. Request body

Same shape as [Day-1 envelope contract §1–2](../../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md):
six discriminator values, per-type payload schemas, all carrying `requestId` (UUIDv4).

### Server-side validation
- Body MUST parse as the Zod root schema (see [data-model.md §6](../data-model.md#6-zod-schemas-validation-surface)).
- `type` MUST be one of: `embed | summarize | extract_actions | classify_intent |
  generate_day_header | scan_sensitivity`.
- `requestId` MUST be UUIDv4.
- Per-type `payload` MUST match the per-type schema; missing required fields → validation
  failure.

Validation failure → HTTP 200 + `ErrorResponse(code: "INTERNAL", message: "request body
failed validation")`. The function MUST NOT echo the specific Zod issue (avoids leaking
schema internals to potentially-untrusted callers).

---

## 3. Model routing table (FR-014-002)

| Request `type` | Upstream provider | Model string | Caching | `modelLabel` in response |
|----------------|-------------------|--------------|---------|--------------------------|
| `embed` | OpenAI direct (no AI Gateway) | `text-embedding-3-small` (`dimensions: 1536`) | none | `"openai/text-embedding-3-small"` |
| `summarize` | Vercel AI Gateway → Anthropic | `anthropic/claude-sonnet-4-6` | none | `"anthropic/claude-sonnet-4-6"` |
| `extract_actions` | Vercel AI Gateway → Anthropic | `anthropic/claude-sonnet-4-6` | none | `"anthropic/claude-sonnet-4-6"` |
| `generate_day_header` | Vercel AI Gateway → Anthropic | `anthropic/claude-sonnet-4-6` | none | `"anthropic/claude-sonnet-4-6"` |
| `classify_intent` | Vercel AI Gateway → Anthropic | `anthropic/claude-haiku-4-5` | **prompt-cached prefix** | `"anthropic/claude-haiku-4-5"` |
| `scan_sensitivity` | Vercel AI Gateway → Anthropic | `anthropic/claude-haiku-4-5` | **prompt-cached prefix** | `"anthropic/claude-haiku-4-5"` |

`modelLabel` strings are stable across Day 2 and become part of the cluster engine's
label-version drift detection (spec 002 FR-038/039). Updating a `modelLabel` requires a spec
amendment.

### Upstream timeouts (FR-014-004 — TIMEOUT mapping)

| Request type | Upstream timeout |
|--------------|------------------|
| `summarize` | 60 s |
| All others | 30 s |

Mirrors `LlmGatewayClient`'s Day-1 client-side timeouts. Server timeout MUST be ≤ client
timeout to ensure the server returns a structured `TIMEOUT` error rather than the client
seeing an OkHttp socket timeout.

---

## 4. Response body

Same shape as [Day-1 envelope contract §3](../../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md).
Six success variants + `Error`. Discriminator: `type`. All variants carry the originating
`requestId`.

### 4.1 Success variants — schema details

#### `embed_response`

```json
{
  "type": "embed_response",
  "requestId": "...",
  "vector": [0.123, -0.456, ...],   // exactly 1536 finite numbers
  "modelLabel": "openai/text-embedding-3-small"
}
```

Server obligations:
- `vector.length === 1536`. If OpenAI ever returns a different dimension, return
  `MALFORMED_RESPONSE`.
- All elements are finite numbers (no `NaN`, `Infinity`, or null).

#### `summarize_response`, `extract_actions_response`, `classify_intent_response`, `generate_day_header_response`, `scan_sensitivity_response`

Field shapes per [data-model.md §2.1](../data-model.md#21-llmgatewayresponse-discriminated-union).
Server obligations:
- `modelLabel` is exactly the value from §3.
- All string fields are non-empty (empty body from upstream → `MALFORMED_RESPONSE`).
- `confidence` (classify_intent) is in `[0.0, 1.0]`.

### 4.2 Error variant

```json
{
  "type": "error",
  "requestId": "...",
  "code": "<one of the seven ErrorCode values>",
  "message": "<short human-readable description, never contains prompt/token/vector content>"
}
```

`message` content rules:
- Static or templated string only.
- MUST NOT include the original prompt, the upstream response body, or any user-derived
  content.
- MAY include the upstream provider name and a generic failure mode, e.g.
  `"Anthropic returned 503 Service Unavailable"`.

---

## 5. HTTP status codes

The function emits exactly two status codes:

| HTTP | When |
|------|------|
| `200 OK` | Every successful response AND every upstream LLM failure (encoded as `Error` body per FR-014-003). |
| `401 Unauthorized` | ONLY when the auth gate fails per [auth-jwt-contract.md](auth-jwt-contract.md). |

The function MUST NOT emit `4xx` for malformed bodies (`INTERNAL` body, HTTP 200), `5xx` for
upstream failures (`GATEWAY_5XX` / `PROVIDER_5XX` body, HTTP 200), or any other status code.
This is structurally important: the Android client treats any non-200 as a transport-layer
failure (its own retry path). The function returning HTTP 200 for upstream failures lets the
client distinguish "gateway is alive but upstream is sad" from "gateway itself is down".

---

## 6. Upstream error → `ErrorCode` mapping (FR-014-004)

| Upstream signal | Server response code |
|-----------------|----------------------|
| Vercel AI Gateway HTTP 5xx (any) | `GATEWAY_5XX` |
| Vercel AI Gateway times out before forwarding | `GATEWAY_5XX` |
| OpenAI HTTP 5xx (any) | `PROVIDER_5XX` |
| Anthropic / OpenAI request timeout (per §3 timeouts) | `TIMEOUT` |
| Upstream returns 200 but body is non-JSON or schema-invalid | `MALFORMED_RESPONSE` |
| Upstream `vector.length !== 1536` (embed only) | `MALFORMED_RESPONSE` |
| Inbound request fails Zod parse | `INTERNAL` |
| Any uncaught exception in handler | `INTERNAL` |
| Auth gate failure (any subcheck) | `UNAUTHORIZED` (HTTP 401, NOT HTTP 200) |
| `NETWORK_UNAVAILABLE` is reserved for the Android client (`:net` cannot reach the function); the function never emits it | n/a |

Anthropic 4xx (rate limit, invalid request, etc.) collapse to `GATEWAY_5XX` for Day 2 — the
Android client's retry-once direct-provider fallback (FR-013-008) handles all gateway-side
failures uniformly. Distinguishing 4xx from 5xx at the upstream gateway is deferred to a
future spec if telemetry shows it matters.

---

## 7. Streaming (out of scope)

Spec 014 explicitly does not stream (FR-014-006). The function MUST NOT emit
`Content-Type: text/event-stream` or chunked transfer encoding. All responses are single-shot
`Content-Type: application/json`. Streaming for `summarize` / `extract_actions` is deferred to
spec 004 (Ask Orbit).

---

## 8. Examples

### 8.1 Embed — happy path

Request:
```http
POST /llm HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "type": "embed",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": { "text": "the quick brown fox" }
}
```

Response:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "type": "embed_response",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "vector": [0.0123, -0.0456, ... /* 1536 floats */],
  "modelLabel": "openai/text-embedding-3-small"
}
```

### 8.2 Classify intent — cache hit

Request:
```http
POST /llm HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "type": "classify_intent",
  "requestId": "...",
  "payload": { "text": "Buy milk on the way home", "appCategory": "messaging" }
}
```

Response (cache hit on the system-prompt prefix):
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "type": "classify_intent_response",
  "requestId": "...",
  "intent": "REMINDER",
  "confidence": 0.92,
  "modelLabel": "anthropic/claude-haiku-4-5"
}
```

Audit row written: `details_json.cacheHit = true`.

### 8.3 Auth missing — 401

Request:
```http
POST /llm HTTP/1.1
Content-Type: application/json

{ "type": "embed", "requestId": "...", "payload": { "text": "..." } }
```

Response:
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "type": "error",
  "requestId": "",
  "code": "UNAUTHORIZED",
  "message": "Missing or invalid Authorization header"
}
```

Note: `requestId` may be empty when auth fails before body parse. The Android client tolerates
this.

### 8.4 Anthropic 5xx — gateway error

Request: any Sonnet- or Haiku-routed `type`.

Response:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "type": "error",
  "requestId": "...",
  "code": "GATEWAY_5XX",
  "message": "Vercel AI Gateway returned 503"
}
```

The Android client's `LlmGatewayClient` retry-once direct-provider fallback (FR-013-008)
fires on this `code`; the function does not retry itself.

---

## 9. Backward compatibility

Spec 014 does not modify the wire envelope shape from spec 013. Adding a new `requestId` /
new field to a payload requires:

1. Add it to the Kotlin sealed class in `app/src/main/java/com/capsule/app/ai/gateway/`.
2. Add it to the TypeScript mirror in this contract + Zod schema.
3. Add a new request-type variant handler in the function.
4. **Both** Android and Edge Function deploy together (Day-2 has no version negotiation).

Removing a field requires a spec amendment.

---

## 10. Cross-references

- [Day-1 envelope contract](../../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md)
- [auth-jwt-contract.md](auth-jwt-contract.md) — JWT verification details
- [audit-row-contract.md](audit-row-contract.md) — `details_json` shape per request
- [data-model.md](../data-model.md) — TypeScript types and Zod schemas
