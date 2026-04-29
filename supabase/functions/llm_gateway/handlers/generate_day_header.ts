// Spec 014 — generate_day_header handler stub. Real impl lands in T014-009 (Phase D).

import type {
  GenerateDayHeaderRequest,
  HandlerContext,
  LlmGatewayResponse,
} from "../types.js";

export async function handle(
  req: GenerateDayHeaderRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
