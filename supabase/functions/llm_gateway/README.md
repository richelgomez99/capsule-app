# Orbit LLM Gateway — Vercel Edge Function (Spec 014)

This directory contains the **Vercel Edge Function** that fronts every cloud
LLM call from the Orbit Android app. Despite living under `supabase/`, this
function deploys to **Vercel**, not Supabase Edge Functions
(see [research.md §9](../../../specs/014-edge-function-llm-gateway/research.md)
for rationale).

This README is the **operator runbook of record** (spec 014 FR-014-020).

---

## 1. Required Vercel environment variables (FR-014-021)

All five MUST be set in the Vercel project's **Production** AND **Preview**
environments (and **Development** for `vercel dev`):

| Var | Source | Purpose |
|-----|--------|---------|
| `OPENAI_API_KEY` | OpenAI dashboard | `embed` handler — direct OpenAI Embeddings call. |
| `ANTHROPIC_API_KEY` | Vercel AI Gateway dashboard (gateway-routed key) | All five Anthropic-routed handlers via Vercel AI Gateway. |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase dashboard → Project Settings → API | Service-role insert into `audit_log_entries` (FR-014-013). |
| `SUPABASE_URL` | Supabase dashboard → Project Settings → API | Audit client + JWT issuer derivation. |
| `SUPABASE_JWT_SECRET` | Supabase dashboard → Project Settings → API → JWT Settings | HS256 verification key for inbound user JWTs. |

Secrets are **never** stored in `local.properties`, **never** committed.
Local development uses `.env.local` (gitignored); see §3.

The Android-side `cloud.gateway.url` (the deployed Vercel URL) is **not** a
secret — it lives in `local.properties` and is stamped into `BuildConfig`
at build time (FR-014-016).

---

## 2. First-time project setup

```bash
cd supabase/functions/llm_gateway
npm install
vercel link            # follow prompts to link/create the Vercel project
```

Then add each of the five env vars to Production, Preview, and Development:

```bash
vercel env add OPENAI_API_KEY production
vercel env add ANTHROPIC_API_KEY production
vercel env add SUPABASE_SERVICE_ROLE_KEY production
vercel env add SUPABASE_URL production
vercel env add SUPABASE_JWT_SECRET production
# repeat with `preview` and `development`
```

Verify:

```bash
vercel env ls production
```

---

## 3. Local development (`vercel dev`)

1. Copy `.env.local.example` → `.env.local`.
2. Fill in real values (`.env.local` is gitignored).
3. Run `vercel dev`. The function is served at `http://localhost:3000/llm`.

Smoke (no auth — should 401 once Phase B lands; currently 501 from Phase A):

```bash
curl -i -X POST http://localhost:3000/llm \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Smoke (with a real Supabase JWT):

```bash
JWT="<paste a fresh access_token from a signed-in supabase session>"
curl -i -X POST http://localhost:3000/llm \
  -H "Authorization: Bearer $JWT" \
  -H 'Content-Type: application/json' \
  -d '{"type":"embed","requestId":"550e8400-e29b-41d4-a716-446655440000","payload":{"text":"hello"}}'
```

---

## 4. Production deploy

Use the runbook script:

```bash
bash deploy.sh
```

This verifies all five env vars are present in `production` and then runs
`vercel deploy --prod`. Use `bash deploy.sh --dry-run` to verify env without
deploying.

After a successful deploy:

1. Capture the stable production URL (`https://<project>.vercel.app/llm`).
2. Update each developer's `local.properties`:
   ```
   cloud.gateway.url=https://<project>.vercel.app/llm
   ```
3. Smoke-test the deployed URL with the curl examples in §3 (replace
   `localhost:3000` with the production URL).

---

## 5. Audit telemetry

Every authenticated request — success or upstream failure — writes one row
into `audit_log_entries` with `event_type='cloud_llm_call'`. The
`details_json` shape is the **closed enum** specified in
[audit-row-contract.md](../../../specs/014-edge-function-llm-gateway/contracts/audit-row-contract.md):
`{requestId, requestType, model, modelLabel, latencyMs, tokensIn,
tokensOut, cacheHit, success}` plus `errorCode` on failures.

**Constitution Principle XIV (Bounded Observation) is load-bearing here.**
The function MUST NOT log or persist prompt content, response content,
embedding vectors, JWT contents, or the classified `intent` string. Adding
any field to `details_json` requires a spec amendment.

Cost observability surfaces via the read-only `cost_per_user_daily` view
(spec 014 FR-014-024, migration `00000003_cost_per_user_daily.sql`).

---

## 6. Deploy log

(Append a row per production deploy. T014-020 lands the first entry.)

| Date (UTC) | Git SHA | Vercel URL | Notes |
|-----------|---------|------------|-------|
| (pending) | (pending) | (pending) | First production deploy — T014-020. |
