// Spec 014 — embed handler stub. Real implementation lands in T014-012 (Phase E).

import type { EmbedRequest, HandlerContext, HandlerResult } from "../types.js";
import { MODEL_EMBED, MODEL_EMBED_LABEL } from "../lib/models.js";

export async function handle(
  req: EmbedRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  return {
    response: {
      type: "error",
      requestId: req.requestId,
      code: "INTERNAL",
      message: "not yet implemented",
    },
    model: MODEL_EMBED,
    modelLabel: MODEL_EMBED_LABEL,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
  };
}
