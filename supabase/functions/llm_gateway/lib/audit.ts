// Spec 014 — Phase F (T014-013): service-role Supabase client + audit insert.
//
// Inserts exactly one row into `audit_log_entries` per authenticated request,
// per audit-row-contract.md §1. Service-role bypasses RLS, but `user_id` is
// stamped explicitly from the JWT `sub` claim so SELECT RLS continues to
// scope users to their own rows (SC-014-007).
//
// Constitution Principle XIV — Bounded Observation: `details_json` shape is
// CLOSED. Only the documented keys (§2.1 / §2.2) are emitted. Audit failure
// emits exactly one error log line and resolves; never throws to the caller.

import { createClient, type SupabaseClient } from "@supabase/supabase-js";

import type { ErrorCode } from "./errors.js";
import type { LlmGatewayRequestType } from "../types.js";

/** Closed shape — no fields beyond these are ever emitted. */
export interface AuditInput {
  userId: string;
  requestId: string;
  requestType: LlmGatewayRequestType;
  model: string;
  modelLabel: string;
  latencyMs: number;
  tokensIn: number;
  tokensOut: number;
  cacheHit: boolean;
  success: boolean;
  /** Present only when `success === false`. */
  errorCode?: ErrorCode;
}

/** `details_json` payload per audit-row-contract.md §2. Closed shape. */
type DetailsJsonSuccess = {
  requestId: string;
  requestType: LlmGatewayRequestType;
  model: string;
  modelLabel: string;
  latencyMs: number;
  tokensIn: number;
  tokensOut: number;
  cacheHit: boolean;
  success: true;
};

type DetailsJsonError = Omit<DetailsJsonSuccess, "success"> & {
  success: false;
  errorCode: ErrorCode;
};

type DetailsJson = DetailsJsonSuccess | DetailsJsonError;

let cachedClient: SupabaseClient | null = null;

/** Lazy service-role client (env vars read once per cold start). */
function client(): SupabaseClient {
  if (cachedClient) return cachedClient;
  const url = process.env.SUPABASE_URL;
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !serviceKey) {
    // Fail loud on first audit insert rather than silently writing into a
    // misconfigured client. Either the deploy is missing required env or
    // someone removed it; both are bugs we want to surface immediately.
    throw new Error("missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY");
  }
  cachedClient = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return cachedClient;
}

/** Test seam: swap the service-role client. */
export function _setClientForTest(c: SupabaseClient | null): void {
  cachedClient = c;
}

/**
 * Insert one `audit_log_entries` row. Never throws — on failure, emits the
 * single bounded error log line per audit-row-contract.md §4 and resolves.
 */
export async function recordAuditRow(input: AuditInput): Promise<void> {
  try {
    const details = buildDetailsJson(input);
    const { error } = await client()
      .from("audit_log_entries")
      .insert({
        user_id: input.userId,
        event_type: "cloud_llm_call",
        actor: "edge_function",
        subject_id: null,
        details_json: details,
      });
    if (error) {
      logAuditFailure(input.requestId);
    }
  } catch {
    // Per contract §4, log a single bounded line with EXACTLY two fields
    // (plus `level`). Never includes prompt, response, vector, claim data,
    // or the underlying error message.
    logAuditFailure(input.requestId);
  }
}

function buildDetailsJson(input: AuditInput): DetailsJson {
  if (input.success) {
    return {
      requestId: input.requestId,
      requestType: input.requestType,
      model: input.model,
      modelLabel: input.modelLabel,
      latencyMs: input.latencyMs,
      tokensIn: input.tokensIn,
      tokensOut: input.tokensOut,
      cacheHit: input.cacheHit,
      success: true,
    };
  }
  // Error rows: errorCode required; tokens forced to 0; cacheHit forced false
  // per audit-row-contract.md §2.2. Defensive — handlers already do this, but
  // we re-enforce here so a misbehaving handler cannot leak token counts on
  // an error path.
  return {
    requestId: input.requestId,
    requestType: input.requestType,
    model: input.model,
    modelLabel: input.modelLabel,
    latencyMs: input.latencyMs,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
    success: false,
    errorCode: input.errorCode ?? "INTERNAL",
  };
}

function logAuditFailure(requestId: string): void {
  // Closed shape: { level, requestId, audit_insert_failed }. Nothing else.
  console.error(
    JSON.stringify({
      level: "error",
      requestId,
      audit_insert_failed: true,
    }),
  );
}
