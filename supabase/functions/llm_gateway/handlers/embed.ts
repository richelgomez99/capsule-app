// Spec 014 — embed handler stub. Real implementation lands in T014-012 (Phase E).

import type { EmbedRequest, HandlerContext, LlmGatewayResponse } from "../types.js";

export async function handle(
  req: EmbedRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
