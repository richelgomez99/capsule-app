# Quickstart — 014-edge-function-llm-gateway (Day 2)

**Audience**: the next implementer picking this up to land the actual code.

This is a 5-minute walkthrough. Do steps in order.

---

## 0. Verify branch + Day-1 baseline

```sh
cd /Users/richelgomez/dev/capsule-app
git rev-parse --abbrev-ref HEAD     # MUST print: cloud-pivot
git --no-pager log --oneline -5     # SHOULD show the Day-1 alpha-gate commit + spec 013 work
```

If you're not on `cloud-pivot`, stop. `git checkout cloud-pivot`.

The Day-1 backbone is a hard prerequisite. Verify:

```sh
# Migrations applied to the production Supabase project.
psql "$SUPABASE_PROD_DB_URL" -c "\dt" | grep -E 'envelopes|audit_log_entries|clusters'

# Multi-user RLS smoke test passes.
psql "$SUPABASE_PROD_DB_URL" -f supabase/tests/multi_user_smoke.sql | tail -3
# Expected: a 'PASS' line.
```

If any of those fail, fix Day-1 before proceeding. **No Day-2 work ships until Day-1 is
green.**

## 1. Run the Android-side baseline (must be green BEFORE any Day-2 changes)

```sh
./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest \
  --tests '*Llm*' --tests '*Cluster*'
```

If red, fix that first.

## 2. Read in this order (do not skim — most-recent-modified wins on conflicts)

1. [spec.md](spec.md) — authoritative requirements.
2. [plan.md](plan.md) — phase structure, constitution check, complexity tracking.
3. [data-model.md](data-model.md) — every TypeScript type, JWT claims, `details_json` shape,
   view DDL.
4. [contracts/gateway-request-response.md](contracts/gateway-request-response.md) — server's
   wire-format obligations.
5. [contracts/auth-jwt-contract.md](contracts/auth-jwt-contract.md) — JWT verification.
6. [contracts/audit-row-contract.md](contracts/audit-row-contract.md) — `details_json`
   shape, fully specified.
7. [research.md](research.md) — skim for validated facts; full read only to challenge a
   decision.
8. **For Day-1 reference (do not modify any of these):**
   [specs/013-cloud-llm-routing/data-model.md](../013-cloud-llm-routing/data-model.md),
   [specs/013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md](../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md).

## 3. Vercel project setup (Phase A)

This step happens **once**. Output: a stable production URL and five env vars set in Vercel.

```sh
# Install Vercel CLI if not already.
npm i -g vercel

cd supabase/functions/llm_gateway
vercel link        # link this directory to a new Vercel project (or existing)
vercel env add OPENAI_API_KEY production
vercel env add ANTHROPIC_API_KEY production         # or VERCEL_AI_GATEWAY_KEY if routing via gateway
vercel env add SUPABASE_SERVICE_ROLE_KEY production
vercel env add SUPABASE_URL production
vercel env add SUPABASE_JWT_SECRET production

# Same five vars in 'preview' and 'development' environments.
vercel env ls      # confirm all five are set per environment.
```

**Secrets discipline (FR-014-021)**: the env vars are set ONLY via Vercel; never in
`local.properties`, never committed, never logged. If you ever see a key in stdout, rotate
it immediately.

## 4. Local dev loop

```sh
cd supabase/functions/llm_gateway
cp .env.local.example .env.local        # gitignored; fill with dev keys
npm install                              # one-time
vercel dev                               # serves at http://localhost:3000/llm
```

Smoke test from a terminal (replace `<JWT>` with a token from a real signed-in Supabase user):

```sh
curl -X POST http://localhost:3000/llm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "type": "embed",
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "payload": { "text": "the quick brown fox" }
  }' \
  | jq .
# Expected: { "type": "embed_response", "requestId": "...", "vector": [...1536 floats...], "modelLabel": "openai/text-embedding-3-small" }
```

Auth-failure smoke test:

```sh
curl -i -X POST http://localhost:3000/llm \
  -H "Content-Type: application/json" \
  -d '{ "type": "embed", "requestId": "...", "payload": { "text": "..." } }'
# Expected: HTTP 401, body { "type":"error", "code":"UNAUTHORIZED", ... }.
```

## 5. Implementation order

Run `/speckit.tasks` to generate `tasks.md`. Implement in the order it produces. Each task
lands as **its own atomic commit** on `cloud-pivot` (NFR-014-002). Do not squash before
review.

The phases are:
- **Phase A** — Vercel project bootstrap, env vars, `.env.local.example`, `package.json`,
  `tsconfig.json`, `deploy.sh`, README runbook.
- **Phase B** — JWT verification middleware (`jose`-based, edge-runtime).
- **Phase C** — Request router (Zod-validated discriminated union → handler dispatch).
- **Phase D** — Anthropic handlers (Sonnet for 3 capabilities, Haiku for 2 with prompt
  caching).
- **Phase E** — OpenAI embeddings handler.
- **Phase F** — Audit insert via Supabase service-role client.
- **Phase G** — `cost_per_user_daily` SQL view migration (`supabase/migrations/00000003_cost_per_user_daily.sql`).
- **Phase H** — Android wiring: `BuildConfig.CLOUD_GATEWAY_URL` + `authStateBinder` interface
  + Supabase implementation + `LlmGatewayClient` integration.
- **Phase I** — End-to-end smoke test (deployed function + Android emulator).

## 6. Production deploy

```sh
cd supabase/functions/llm_gateway
./deploy.sh        # delegates to: vercel deploy --prod
# Output ends with: Production: https://<project>.vercel.app
```

Update `local.properties` (gitignored) on the dev machine:

```
cloud.gateway.url=https://<project>.vercel.app/llm
```

Rebuild Android: `./gradlew assembleDebug` — the new URL flows into `BuildConfig.CLOUD_GATEWAY_URL`.

## 7. End-to-end verification (SC-014-001 → SC-014-007)

From an instrumented Android test or the emulator with a signed-in user:

```sh
# Run the Day-2 instrumented smoke (added in Phase I).
./gradlew connectedDebugAndroidTest \
  --tests '*EdgeFunctionEndToEndTest*'
```

Expected: all six request types succeed; latency p95 ≤ 2500ms (SC-014-003); audit rows
visible in `audit_log_entries` (SC-014-005).

Verify SC-014-002 (cache hit rate ≥ 80%):

```sql
SELECT
  COUNT(*) AS total,
  COUNT(*) FILTER (WHERE (details_json->>'cacheHit')::boolean) AS hits,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE (details_json->>'cacheHit')::boolean) / COUNT(*),
    1
  ) AS hit_pct
FROM audit_log_entries
WHERE event_type = 'cloud_llm_call'
  AND details_json->>'requestType' = 'classify_intent'
  AND user_id = '<test user>'
  AND created_at > now() - interval '10 minutes'
ORDER BY created_at DESC
LIMIT 100;
-- Expected after running 100 sequential ClassifyIntent calls: hit_pct >= 80.0.
```

Verify SC-014-006 (no prompt content in logs):

```sh
vercel logs --since 1h | grep -E '<unique UUID embedded in test prompts>' \
  || echo "PASS: no prompt content in logs"
```

Verify SC-014-007 (RLS still holds):

```sh
psql "$SUPABASE_PROD_DB_URL" -f supabase/tests/multi_user_smoke.sql | tail -3
# Expected: 'PASS'.
```

## 8. Do NOT push, do NOT deploy without approval

```sh
# DO NOT RUN until user explicitly approves (NFR-014-001):
# git push -u origin cloud-pivot
# vercel deploy --prod          # held until user approves
```

Phase A's `vercel link` and Phase A's first `vercel dev` invocation are local-only and do
not require approval. The first `vercel deploy --prod` does.

## 9. Common pitfalls

- **`@supabase/supabase-js` for JWT verification.** Don't. It calls back to Auth REST. Use
  `jose` directly.
- **Forgetting `dimensions: 1536` on OpenAI embed calls.** Default is 1536 today, but pin it
  defensively.
- **Logging the JWT or any claim other than `sub`.** Forbidden by FR-014-012. The operator
  log line carries `userId` (= `sub`) and nothing else identifying.
- **Distinguishing JWT failure modes in the response body.** All collapse to one message
  per [auth-jwt-contract.md §3](contracts/auth-jwt-contract.md). Don't echo "Token expired".
- **Anthropic 4xx → `PROVIDER_5XX`.** Wrong. Anthropic-via-gateway is `GATEWAY_5XX` for any
  non-2xx. `PROVIDER_5XX` is reserved for direct OpenAI (the only direct-provider call).
- **Audit insert failure crashing the request.** Wrap in try/catch, log
  `audit_insert_failed=true`, return upstream response anyway (FR-014-014).
- **Streaming.** Don't. Spec 014 is non-streaming (FR-014-006).
- **Touching the Day-1 wire envelope shape.** Don't. Adding fields requires a spec
  amendment.
- **Hardcoding the gateway URL on Android.** Use `BuildConfig.CLOUD_GATEWAY_URL` from
  `local.properties` (FR-014-016).
- **Calling `LlmGatewayClient` without `authStateBinder`.** Day-2 closes the auth gate;
  null token → return `Error(UNAUTHORIZED)` immediately (FR-014-017).
