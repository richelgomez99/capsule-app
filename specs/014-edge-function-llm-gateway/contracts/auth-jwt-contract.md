# Auth JWT Contract (Day 2)

**Boundary**: Edge Function auth gate (`supabase/functions/llm_gateway/index.ts`).
**Status**: STABLE for Day 2.

This contract specifies how the Edge Function authenticates incoming requests. Day-1
deliberately allowed null/missing auth (graceful degradation while `AuthSessionStore` was a
stub). Day 2 closes that hole: every request MUST carry a valid Supabase JWT.

---

## 1. Header shape

Required header on every request:

```
Authorization: Bearer <supabase-jwt>
```

- Scheme is exactly `Bearer ` (case-sensitive per RFC 6750, but the function MUST accept
  case-insensitive scheme matching for client compatibility).
- Token follows the literal space.
- The function MUST NOT accept the JWT in any other location: not in the body, not in a
  `X-Auth-Token` header, not in a query string, not in a cookie. FR-014-009 forbids trusting
  any other source for `user_id`.

Failure modes that all collapse to the **same** `UNAUTHORIZED` response (per FR-014-008,
"the function MUST NOT leak which check failed"):

1. Header missing entirely.
2. Header present but does not start with `Bearer `.
3. Header present but token is empty after the scheme.
4. Token fails `jose.jwtVerify` (signature, `exp`, `iss`).
5. Token verifies but `sub` claim is missing.
6. Token verifies but `sub` claim is non-UUID-shaped.
7. (Defensive) Token verifies but `role !== "authenticated"`.

---

## 2. JWT verification — algorithm and steps

**Library**: `jose` (TypeScript, MIT, edge-runtime compatible).
**Algorithm**: HS256 (Supabase project default).
**Key**: `process.env.SUPABASE_JWT_SECRET` — the project's JWT secret from the Supabase
dashboard, configured as a Vercel env var.

### Verification steps (in order)

```typescript
import { jwtVerify } from "jose";

async function verifyJwt(rawHeader: string | null): Promise<{ sub: string }> {
  // Step 1: header parse
  if (!rawHeader || !/^Bearer\s+(.+)$/i.test(rawHeader)) {
    throw new UnauthorizedError("Missing or invalid Authorization header");
  }
  const token = rawHeader.replace(/^Bearer\s+/i, "");

  // Step 2: verify signature, exp, iss
  const secret = new TextEncoder().encode(process.env.SUPABASE_JWT_SECRET!);
  const { payload } = await jwtVerify(token, secret, {
    issuer: `${process.env.SUPABASE_URL}/auth/v1`,
    // exp is checked automatically; no explicit option needed.
    // aud is intentionally NOT checked — Supabase emits aud="authenticated" but
    // we already check role below; aud check would be redundant.
  });

  // Step 3: sub presence + UUID shape
  const sub = payload.sub;
  if (typeof sub !== "string" || !UUID_REGEX.test(sub)) {
    throw new UnauthorizedError("Invalid subject claim");
  }

  // Step 4: defensive role check
  if (payload.role !== "authenticated") {
    throw new UnauthorizedError("Invalid role claim");
  }

  return { sub };
}
```

`UUID_REGEX`: `/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i`
(accepts UUIDv1–v5 since Supabase `auth.users.id` is technically UUIDv4 but we don't enforce
the v4 marker).

### `UnauthorizedError` handling

A single `catch` at the top of the request handler maps any `UnauthorizedError` (and any
`jose.errors.JWTExpired`, `JWTInvalid`, `JWSSignatureVerificationFailed`, etc.) to:

```json
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "type": "error",
  "requestId": "<from body if parseable, else empty string>",
  "code": "UNAUTHORIZED",
  "message": "<one of the static UNAUTHORIZED messages from §3>"
}
```

---

## 3. UNAUTHORIZED `message` strings (closed enum)

The function emits one of exactly these strings to the client. No other strings are allowed
on the auth path (avoid leaking verification internals).

| Trigger | `message` value |
|---------|-----------------|
| Header missing or scheme mismatch | `"Missing or invalid Authorization header"` |
| Token empty after `Bearer ` | `"Missing or invalid Authorization header"` |
| `jose.jwtVerify` throws (signature, exp, iss) | `"Token verification failed"` |
| `sub` missing or non-UUID | `"Invalid subject claim"` |
| `role !== "authenticated"` | `"Invalid role claim"` |
| Any uncaught exception in the auth path | `"Token verification failed"` (defensive collapse) |

**Why these messages and not "Token expired"** — the function MUST NOT distinguish "expired"
from "forged" from "wrong secret" in its public response (FR-014-008). All collapse to the
same `Token verification failed` shape so attackers cannot probe which of their tokens are
"close" to working. Vercel-side logs MAY contain finer-grained reasons for operator
debugging, but those are bounded by the FR-014-012 logging shape — the function logs
`success: false, errorCode: "UNAUTHORIZED"` only, no token content.

---

## 4. Required Vercel environment variables

| Var | Purpose | Source |
|-----|---------|--------|
| `SUPABASE_JWT_SECRET` | HS256 key for JWT verification | Supabase dashboard → Project Settings → API → JWT Settings → JWT Secret |
| `SUPABASE_URL` | Used to construct expected `iss` claim (`${SUPABASE_URL}/auth/v1`) | Supabase dashboard → Project Settings → API → Project URL |

Both MUST be set in Vercel Production AND Preview environments. Missing → function fails on
first request with `INTERNAL` (uncaught reference error). The deploy runbook (FR-014-020)
verifies presence before deploy.

---

## 5. What the function does with `sub`

The verified `sub` claim becomes the authoritative `user_id` for:

1. **Audit row insert** (FR-014-009, FR-014-013): `audit_log_entries.user_id` is stamped
   from `sub`. This is the only path through which `audit_log_entries` learns the user
   identity — the function MUST NOT trust any body field, header, or query parameter.
2. **Operator log line**: `requestId` and `userId` (= `sub`) are the only identifying fields
   in the per-request log line. The user's email, display name, etc. are NOT logged.

The function MUST NOT:
- Echo `sub` back to the client in the response body (response carries `requestId`, not
  `userId`).
- Store `sub` outside the per-request context (no global state, no cache keyed on `sub`).
- Include `sub` in upstream LLM prompts (Anthropic/OpenAI must not learn the user identity).

---

## 6. Token lifecycle (Day 2 behavior)

- **Token expiry mid-flight**: If the token verifies at request entry but Anthropic takes 30s
  to respond, the function does NOT re-verify mid-flight. The audit row is written with the
  user_id from the original verification. Subsequent requests use a fresh token (Android
  `authStateBinder.currentJwt()` is responsible for refresh per FR-014-017).
- **Replay within validity window**: A captured JWT is replayable until `exp`. Day 2 accepts
  this risk; nonce/idempotency-key defense is deferred (per spec Edge Cases — replay).
  Audit telemetry surfaces unusual `requestId` duplicate patterns retrospectively.
- **Token rotation**: Supabase JWTs are issued by Auth and refreshed by the SDK. Rotation is
  invisible to the function — every request brings its own token, the function verifies and
  forgets.

---

## 7. Local development

`vercel dev` reads `.env.local` (gitignored) for `SUPABASE_JWT_SECRET` and `SUPABASE_URL`.
Local-dev tokens MUST be issued by the same Supabase project as production for JWT
verification to succeed. There is no "dev mode" bypass — the auth gate is uniform across
environments.

For unit tests, the function exposes (via `__test__` ESM export, not via HTTP) a way to mint
a test JWT signed with a test secret. The unit test asserts:

1. Missing header → 401 `UNAUTHORIZED` `Missing or invalid Authorization header`.
2. Forged signature → 401 `UNAUTHORIZED` `Token verification failed`.
3. Expired token → 401 `UNAUTHORIZED` `Token verification failed` (NOT `Token expired`).
4. Token without `sub` → 401 `UNAUTHORIZED` `Invalid subject claim`.
5. Token with `role: "service_role"` → 401 `UNAUTHORIZED` `Invalid role claim`.
6. Valid token → handler runs with `{ sub }` available.

---

## 8. Cross-references

- [gateway-request-response.md](gateway-request-response.md) — overall request/response shape
- [audit-row-contract.md](audit-row-contract.md) — how `sub` flows to `audit_log_entries.user_id`
- [data-model.md §3](../data-model.md#3-jwt-claim-shape-supabase-issued-hs256) — TypeScript
  claim shape
- Spec FR-014-007, FR-014-008, FR-014-009
