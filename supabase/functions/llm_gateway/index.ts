// Spec 014 — Edge Function entry point.
// Phase B: JWT auth gate live.
// Phase C: Zod-validated discriminated-union router dispatching to per-type
// handlers.
// Phase F (T014-014): every authenticated request emits one audit row
// (`audit_log_entries.cloud_llm_call`) and one bounded operator log line.

import { verifyJwt } from "./lib/auth.js";
import {
  UnauthorizedError,
  errorResponse,
  UNAUTHORIZED_MESSAGES,
} from "./lib/errors.js";
import { LlmGatewayRequestSchema } from "./lib/schemas.js";
import { wireResponse } from "./lib/response.js";
import { recordAuditRow, type AuditInput } from "./lib/audit.js";
import type {
  HandlerContext,
  HandlerResult,
  LlmGatewayRequest,
} from "./types.js";
import type { ErrorCode } from "./lib/errors.js";

import * as embedHandler from "./handlers/embed.js";
import * as summarizeHandler from "./handlers/summarize.js";
import * as extractActionsHandler from "./handlers/extract_actions.js";
import * as classifyIntentHandler from "./handlers/classify_intent.js";
import * as generateDayHeaderHandler from "./handlers/generate_day_header.js";
import * as scanSensitivityHandler from "./handlers/scan_sensitivity.js";

export const config = { runtime: "edge" };

async function dispatch(
  req: LlmGatewayRequest,
  ctx: HandlerContext,
): Promise<HandlerResult> {
  switch (req.type) {
    case "embed":
      return embedHandler.handle(req, ctx);
    case "summarize":
      return summarizeHandler.handle(req, ctx);
    case "extract_actions":
      return extractActionsHandler.handle(req, ctx);
    case "classify_intent":
      return classifyIntentHandler.handle(req, ctx);
    case "generate_day_header":
      return generateDayHeaderHandler.handle(req, ctx);
    case "scan_sensitivity":
      return scanSensitivityHandler.handle(req, ctx);
  }
}

/**
 * Build the AuditInput from a HandlerResult. Closed shape — same fields
 * land in `audit_log_entries.details_json` and the operator log line.
 */
function buildAuditInput(
  userId: string,
  req: LlmGatewayRequest,
  result: HandlerResult,
  latencyMs: number,
): AuditInput {
  const success = result.response.type !== "error";
  if (success) {
    return {
      userId,
      requestId: req.requestId,
      requestType: req.type,
      model: result.model,
      modelLabel: result.modelLabel,
      latencyMs,
      tokensIn: result.tokensIn,
      tokensOut: result.tokensOut,
      cacheHit: result.cacheHit,
      success: true,
    };
  }
  return {
    userId,
    requestId: req.requestId,
    requestType: req.type,
    model: result.model,
    modelLabel: result.modelLabel,
    latencyMs,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
    success: false,
    errorCode: (result.response as { code: ErrorCode }).code,
  };
}

/**
 * Emit the per-request operator log line. Closed shape per
 * audit-row-contract.md §6 (audit fields + `userId`; `errorCode` only on
 * failure). Constitution Principle XIV — never includes prompt/response.
 */
function emitOperatorLog(audit: AuditInput): void {
  const line: Record<string, unknown> = {
    requestId: audit.requestId,
    userId: audit.userId,
    requestType: audit.requestType,
    model: audit.model,
    modelLabel: audit.modelLabel,
    latencyMs: audit.latencyMs,
    tokensIn: audit.tokensIn,
    tokensOut: audit.tokensOut,
    cacheHit: audit.cacheHit,
    success: audit.success,
  };
  if (!audit.success && audit.errorCode !== undefined) {
    line.errorCode = audit.errorCode;
  }
  console.log(JSON.stringify(line));
}

export default async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse("INTERNAL", "method not allowed", "", 405);
  }

  // Phase B — auth gate. requestId is "" because we have not parsed the
  // body yet (auth runs first per FR-014-007).
  let userId: string;
  try {
    const verified = await verifyJwt(req.headers.get("Authorization"));
    userId = verified.sub;
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      return errorResponse("UNAUTHORIZED", e.publicMessage, "", 401);
    }
    return errorResponse(
      "UNAUTHORIZED",
      UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
      "",
      401,
    );
  }

  // Phase C — body parse + router dispatch. Any parse / validation failure
  // collapses to INTERNAL with the static "request body failed validation"
  // message; the function MUST NOT echo Zod issues to the client
  // (gateway-request-response.md §2).
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    return errorResponse("INTERNAL", "request body failed validation", "", 200);
  }

  const parsed = LlmGatewayRequestSchema.safeParse(raw);
  if (!parsed.success) {
    return errorResponse("INTERNAL", "request body failed validation", "", 200);
  }

  const requestEnvelope = parsed.data;
  const ctx: HandlerContext = { userId, requestId: requestEnvelope.requestId };

  // Phase F — measure latency, dispatch, emit audit + operator log.
  const start = performance.now();
  let result: HandlerResult;
  try {
    result = await dispatch(requestEnvelope, ctx);
  } catch {
    // Uncaught handler exception → INTERNAL per data-model §2.3 mapping.
    // We still emit an audit row so cost-observability stays 1:1 with
    // requests past the auth gate (FR-014-013).
    const latencyMs = Math.round(performance.now() - start);
    const audit: AuditInput = {
      userId,
      requestId: requestEnvelope.requestId,
      requestType: requestEnvelope.type,
      model: "",
      modelLabel: "",
      latencyMs,
      tokensIn: 0,
      tokensOut: 0,
      cacheHit: false,
      success: false,
      errorCode: "INTERNAL",
    };
    emitOperatorLog(audit);
    // Fire-and-forget; audit failure does NOT degrade the response.
    void recordAuditRow(audit);
    return errorResponse(
      "INTERNAL",
      "handler exception",
      requestEnvelope.requestId,
      200,
    );
  }

  const latencyMs = Math.round(performance.now() - start);
  const audit = buildAuditInput(userId, requestEnvelope, result, latencyMs);
  emitOperatorLog(audit);
  // Fire-and-forget; audit insert failure must NOT change the user-facing
  // response per FR-014-014.
  void recordAuditRow(audit);
  return wireResponse(result.response);
}
