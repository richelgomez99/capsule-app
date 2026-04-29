// Spec 014 — summarize handler stub. Real implementation lands in T014-009 (Phase D).

import type { SummarizeRequest, HandlerContext, LlmGatewayResponse } from "../types.js";

export async function handle(
  req: SummarizeRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
