// Spec 014 — extract_actions handler stub. Real impl lands in T014-009 (Phase D).

import type {
  ExtractActionsRequest,
  HandlerContext,
  LlmGatewayResponse,
} from "../types.js";

export async function handle(
  req: ExtractActionsRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
