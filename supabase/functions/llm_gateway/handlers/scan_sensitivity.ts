// Spec 014 — scan_sensitivity handler stub. Real impl lands in T014-010 (Phase D).

import type {
  ScanSensitivityRequest,
  HandlerContext,
  LlmGatewayResponse,
} from "../types.js";

export async function handle(
  req: ScanSensitivityRequest,
  _ctx: HandlerContext,
): Promise<LlmGatewayResponse> {
  return {
    type: "error",
    requestId: req.requestId,
    code: "INTERNAL",
    message: "not yet implemented",
  };
}
