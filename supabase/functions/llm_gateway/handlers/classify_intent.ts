// Spec 014 — classify_intent handler stub. Real impl lands in T014-010 (Phase D).

import type {
  ClassifyIntentRequest,
  HandlerContext,
  LlmGatewayResponse,
} from "../types.js";

export async function handle(
  req: ClassifyIntentRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
