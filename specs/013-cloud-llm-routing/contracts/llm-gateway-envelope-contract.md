# LLM Gateway Envelope Contract

**Boundary**: Android `:net` process (`LlmGatewayClient`) → Edge Function (Vercel AI Gateway proxy)
**Status**: DRAFT — Day 1 of `013-cloud-llm-routing`
**Wire format**: HTTPS POST, JSON body, UTF-8.
**Endpoint (placeholder for Day 1)**: `https://gateway.example.invalid/llm` (RFC-2606 reserved). Real URL lands when the Edge Function deploys (separate spec).

This contract describes the **provider-agnostic envelope** that the Android client sends. It is the only shape the mobile binary knows. Provider-specific JSON shaping (Anthropic Messages, OpenAI Chat Completions, OpenAI Embeddings, AI SDK v5/v6 envelopes) happens **server-side in the Edge Function**, NOT on Android. This is the keystone decision that allows model swaps without re-shipping the mobile binary.

---

## 1. Request envelope

```json
{
  "type": "<one of: embed | summarize | extract_actions | classify_intent | generate_day_header | scan_sensitivity>",
  "requestId": "<UUIDv4 string>",
  "payload": { /* type-specific, see §2 */ }
}
```

### Required headers
- `Content-Type: application/json; charset=utf-8`
- `Authorization: Bearer <supabase user JWT>` — present when `AuthSessionStore.getCurrentToken()` returns non-null. **MAY BE OMITTED on Day 1** because `AuthSessionStore` does not yet exist; the Edge Function MUST handle unauthenticated requests gracefully (Edge Function spec defines that policy). Per FR-013-009.
- `X-Orbit-Request-Id: <same value as the body's requestId>` — duplicated header for trace correlation in Vercel logs.

### Required body fields
- `type`: lowercase snake_case discriminator. Closed enum.
- `requestId`: UUIDv4 string. Generated client-side at `CloudLlmProvider` request construction; propagated end-to-end and echoed in the response.
- `payload`: object whose schema is determined by `type`. See §2.

---

## 2. Per-type request payload schemas

### 2.1 `type = "embed"`
```json
{
  "type": "embed",
  "requestId": "...",
  "payload": {
    "text": "<the input string>"
  }
}
```
Edge Function routes to `openai/text-embedding-3-small` (1536d).

### 2.2 `type = "summarize"`
```json
{
  "type": "summarize",
  "requestId": "...",
  "payload": {
    "text": "<the input string>",
    "maxTokens": 256
  }
}
```
Edge Function routes to `anthropic/claude-sonnet-4-6`.

### 2.3 `type = "extract_actions"`
```json
{
  "type": "extract_actions",
  "requestId": "...",
  "payload": {
    "text": "<envelope text>",
    "contentType": "<e.g. url | screenshot | clipboard | manual>",
    "state": { /* mirror of StateSnapshot */ },
    "registeredFunctions": [
      { "id": "...", "name": "...", "schema": { ... } }
    ],
    "maxCandidates": 3
  }
}
```
Edge Function routes to `anthropic/claude-sonnet-4-6`.

### 2.4 `type = "classify_intent"`
```json
{
  "type": "classify_intent",
  "requestId": "...",
  "payload": {
    "text": "<the input string>",
    "appCategory": "<foreground app category, e.g. browser | messaging | productivity>"
  }
}
```
Edge Function routes to `anthropic/claude-haiku-4-5` with prompt-cached prefix (the cache prefix is the system + few-shot prompt; the user payload is the cache miss boundary).

### 2.5 `type = "generate_day_header"`
```json
{
  "type": "generate_day_header",
  "requestId": "...",
  "payload": {
    "dayIsoDate": "2026-04-28",
    "envelopeSummaries": ["...", "..."]
  }
}
```
Edge Function routes to `anthropic/claude-sonnet-4-6`.

### 2.6 `type = "scan_sensitivity"`
```json
{
  "type": "scan_sensitivity",
  "requestId": "...",
  "payload": {
    "text": "<the input string>"
  }
}
```
Edge Function routes to `anthropic/claude-haiku-4-5` with prompt-cached prefix.

---

## 3. Response envelope

The response uses the same envelope shape — `type` discriminator, `requestId` echo, type-specific data — with one extra top-level field `ok: bool` that simplifies error handling on the client.

### 3.1 Success
```json
{
  "type": "embed_response",
  "requestId": "<echo of request>",
  "ok": true,
  "data": {
    "vector": [0.123, -0.456, ...],
    "modelLabel": "openai/text-embedding-3-small@2026-04"
  }
}
```

### 3.2 Per-type response data schemas

| `type` value | `data` shape |
|--------------|--------------|
| `embed_response` | `{ vector: number[1536], modelLabel: string }` |
| `summarize_response` | `{ summary: string, modelLabel: string }` |
| `extract_actions_response` | `{ proposals: [{functionId, argsJson, confidence}], modelLabel: string }` |
| `classify_intent_response` | `{ intent: string, confidence: number, modelLabel: string }` |
| `generate_day_header_response` | `{ header: string, modelLabel: string }` |
| `scan_sensitivity_response` | `{ tags: string[], modelLabel: string }` |

`modelLabel` is opaque to the client (used only by the cluster engine for FR-038/039 label-version drift detection). Format hint: `<provider>/<model>@<edge-deploy-tag>`.

### 3.3 Error
```json
{
  "type": "error",
  "requestId": "<echo of request>",
  "ok": false,
  "error": {
    "code": "GATEWAY_5XX",
    "message": "human-readable detail"
  }
}
```

Day-1 `error.code` enumeration:

| `code` | When |
|--------|------|
| `NETWORK_UNAVAILABLE` | Synthesised on the client when DNS / connect fails before bytes leave the device. |
| `TIMEOUT` | Request exceeded 30s default (or 60s for `summarize`). |
| `GATEWAY_5XX` | Vercel AI Gateway returned 5xx and the direct-provider retry also failed (or returned 5xx). |
| `PROVIDER_5XX` | Direct-provider fallback returned 5xx (one retry exhausted). |
| `UNAUTHORIZED` | 401/403 from gateway or provider. |
| `MALFORMED_RESPONSE` | Server returned non-JSON or schema-violating JSON. Client logs and surfaces. |
| `INTERNAL` | Edge Function unhandled exception. |

The client MUST NOT inspect the HTTP status code beyond the gateway-vs-direct retry decision. The `error.code` in the body is the source of truth for the type returned across AIDL.

### 3.4 Boundary translation (HTTP envelope vs. on-device sealed-class JSON)

The HTTP wire format defined in §1–§3 is the OUTBOUND shape from `LlmGatewayClient`. Inside the Android process boundary (parcel layer + sealed-class type system), the equivalent representation is FLAT (no `payload` / `data` wrapper) — i.e. the kotlinx.serialization JSON produced with `classDiscriminator = "type"` against `LlmGatewayRequest` / `LlmGatewayResponse` is `{type, requestId, …fields}`. `LlmGatewayClient` is the sole translation seam: it wraps the flat sealed-class JSON into `{type, requestId, payload: {…fields}}` before POST and unwraps the nested response envelope `{type, requestId, ok, data: {…fields} | error: {…}}` back into either the flat sealed-class JSON (success) or `LlmGatewayResponse.Error(code, message)` (failure). Do not leak the nested HTTP envelope into the parcel layer or the sealed-class types.

---

## 4. Retry-once direct-provider fallback (per ADR-003 / FR-013-008)

`LlmGatewayClient` flow per request:

1. POST to gateway URL.
2. If response is 2xx with valid JSON envelope → return.
3. If response is 5xx OR `error.code` is `INTERNAL` OR `GATEWAY_5XX` → retry **once** against the corresponding direct provider endpoint:
   - `embed` → `https://api.openai.com/v1/embeddings` (placeholder URL acceptable on Day 1; key MUST NOT ship in the binary, so the call is a smoke-only stub returning 5xx in practice on Day 1).
   - All other types → `https://api.anthropic.com/v1/messages` (same caveat).
4. If retry succeeds → return mapped to envelope schema.
5. If retry also fails → return `{type: "error", requestId, ok: false, error: {code: "PROVIDER_5XX", ...}}` to the AIDL caller.

**Important Day-1 reality**: Because no real provider keys ship in the binary, the direct-provider fallback will always fail on Day 1 with `UNAUTHORIZED`. This is expected and aligns with R-013-002 — Day 1 ships the routing skeleton; real provider integration is the Edge Function spec's responsibility. The fallback path is wired so the Phase 11 Block 5+ specs do not have to add it later.

---

## 5. Latency budget (per Constitution Principle XV / NFR-013-001)

Measured at the `CloudLlmProvider` boundary (round-trip from `:capture` invocation to result):

| Path | p95 budget |
|------|-----------|
| Capture-path: `classify_intent`, `scan_sensitivity` | ≤ 2s |
| Stage-path: `summarize`, `generate_day_header`, `extract_actions` | ≤ 3s |
| `embed` | not yet bounded; cluster worker is background-only on Day 1 |

`LlmGatewayClient` request timeouts: 30s default, 60s for `summarize`. These bound worst-case; typical p50 should hit budget.

---

## 6. What this contract does NOT cover

- The Edge Function's internal logic (provider selection, retry, prompt assembly, key management) — owned by the Edge Function spec.
- Streaming responses — none of the six methods stream; all responses are single-shot JSON.
- Rate limiting and quota — handled at the Edge Function and Supabase layers; client surfaces 429 as `INTERNAL` on Day 1 (refined in a later spec).
- Token accounting / cost telemetry — out of scope for Day 1; lands in a telemetry spec.
- Anthropic prompt caching specifics — server-side concern; the client does not see cache-vs-no-cache.
