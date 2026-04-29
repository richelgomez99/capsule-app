// Spec 014 — classify_intent handler stub. Real impl lands in T014-010 (Phase D).

import type {
  ClassifyIntentRequest,
  HandlerContext,
  HandlerResult,
} from "../types.js";
import { MODEL_HAIKU, MODEL_HAIKU_LABEL } from "../lib/models.js";

export async function handle(
  req: ClassifyIntentRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  return {
    response: {
      type: "error",
      requestId: req.requestId,
      code: "INTERNAL",
      message: "not yet implemented",
    },
    model: MODEL_HAIKU,
    modelLabel: MODEL_HAIKU_LABEL,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
  };
}
