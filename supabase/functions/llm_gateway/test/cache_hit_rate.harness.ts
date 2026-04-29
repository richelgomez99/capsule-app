// Spec 014 — T014-011 — Prompt-cache hit-rate verification harness.
//
// NOT a unit test. Skipped under default `vitest run` because it has no
// `.test.ts` suffix and lives outside any test glob. Run manually after a
// deployment with:
//
//   RUN_LIVE=1 \
//   GATEWAY_URL=https://<your-vercel-deploy>/llm_gateway \
//   USER_JWT=<authenticated supabase session JWT> \
//   SUPABASE_URL=https://<project>.supabase.co \
//   SUPABASE_SERVICE_ROLE_KEY=<service role> \
//   npx tsx supabase/functions/llm_gateway/test/cache_hit_rate.harness.ts
//
// The harness issues 100 sequential `classify_intent` requests with the
// SAME system prefix (caching is keyed on the cached system block, not on
// the user message), waits 1s after the run for audit rows to land, then
// queries `audit_log_entries` for those request IDs and computes the
// cacheHit ratio. Prints PASS if ratio ≥ 0.80 (SC-014-002), FAIL otherwise.
//
// See `test/README.md` for the full operational contract.

import { createClient } from "@supabase/supabase-js";
import { randomUUID } from "node:crypto";

const RATIO_THRESHOLD = 0.8;
const REQUEST_COUNT = 100;
const POST_RUN_WAIT_MS = 1_000;

interface AuditRow {
  details_json: { requestId: string; cacheHit: boolean; success: boolean };
}

async function main(): Promise<number> {
  if (process.env.RUN_LIVE !== "1") {
    console.log(
      "[cache_hit_rate.harness] skipped (RUN_LIVE not set). Set RUN_LIVE=1 to execute.",
    );
    return 0;
  }
  const gatewayUrl = mustEnv("GATEWAY_URL");
  const userJwt = mustEnv("USER_JWT");
  const supabaseUrl = mustEnv("SUPABASE_URL");
  const serviceKey = mustEnv("SUPABASE_SERVICE_ROLE_KEY");

  const requestIds: string[] = [];
  console.log(`[cache_hit_rate.harness] sending ${REQUEST_COUNT} classify_intent requests...`);
  for (let i = 0; i < REQUEST_COUNT; i++) {
    const requestId = randomUUID();
    requestIds.push(requestId);
    const res = await fetch(gatewayUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${userJwt}`,
      },
      body: JSON.stringify({
        type: "classify_intent",
        requestId,
        payload: {
          // Identical text/appCategory across all 100 calls so the cached
          // system prefix dominates input tokens. We rely on Anthropic
          // ephemeral cache being warm after the first call.
          text: "remind me to call mom this evening",
          appCategory: "messaging",
        },
      }),
    });
    if (res.status !== 200) {
      console.error(
        `[cache_hit_rate.harness] request ${i} returned ${res.status} (continuing)`,
      );
    }
  }

  await new Promise((r) => setTimeout(r, POST_RUN_WAIT_MS));

  const supabase = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false },
  });
  const { data, error } = await supabase
    .from("audit_log_entries")
    .select("details_json")
    .eq("event_type", "cloud_llm_call")
    .in(
      "details_json->>requestId",
      requestIds,
    );
  if (error) {
    console.error("[cache_hit_rate.harness] audit query failed:", error.message);
    return 2;
  }

  const rows = (data ?? []) as AuditRow[];
  const successful = rows.filter((r) => r.details_json.success === true);
  if (successful.length === 0) {
    console.error("[cache_hit_rate.harness] no successful audit rows found");
    return 2;
  }
  const hits = successful.filter((r) => r.details_json.cacheHit === true).length;
  const ratio = hits / successful.length;
  const verdict = ratio >= RATIO_THRESHOLD ? "PASS" : "FAIL";
  console.log(
    `[cache_hit_rate.harness] ${verdict}: cacheHit ratio = ${ratio.toFixed(3)} ` +
      `(${hits}/${successful.length}, threshold ${RATIO_THRESHOLD})`,
  );
  return verdict === "PASS" ? 0 : 1;
}

function mustEnv(name: string): string {
  const v = process.env[name];
  if (!v || v.length === 0) {
    throw new Error(`[cache_hit_rate.harness] missing env: ${name}`);
  }
  return v;
}

main()
  .then((code) => process.exit(code))
  .catch((e) => {
    console.error("[cache_hit_rate.harness] fatal:", e);
    process.exit(2);
  });
