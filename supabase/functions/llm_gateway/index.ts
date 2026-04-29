// Spec 014 — Edge Function entry point.
// Phase B: JWT auth gate live.
// Phase C: Zod-validated discriminated-union router dispatching to per-type
// handler stubs (handlers themselves still INTERNAL — real upstream calls
// land in Phases D + E).

import { verifyJwt } from "./lib/auth.js";
import {
  UnauthorizedError,
  errorResponse,
  UNAUTHORIZED_MESSAGES,
} from "./lib/errors.js";
import { LlmGatewayRequestSchema } from "./lib/schemas.js";
import { wireResponse } from "./lib/response.js";
import type { HandlerContext, LlmGatewayRequest, LlmGatewayResponse } from "./types.js";

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
): Promise<LlmGatewayResponse> {
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

  try {
    const response = await dispatch(requestEnvelope, ctx);
    return wireResponse(response);
  } catch {
    // Uncaught handler exception → INTERNAL per data-model §2.3 mapping.
    return errorResponse("INTERNAL", "handler exception", requestEnvelope.requestId, 200);
  }
}
