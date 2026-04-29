// Spec 014 — embed handler (Phase E, T014-012).
// OpenAI text-embedding-3-small, 1536 dims, length + finite-number assertion.
// Constitution Principle XIV: vector content never logged.

import type { EmbedRequest, HandlerContext, HandlerResult } from "../types.js";
import { MODEL_EMBED, MODEL_EMBED_LABEL } from "../lib/models.js";
import {
  embedText,
  OpenAICallError,
  openaiFailureToErrorCode,
  openaiFailureToMessage,
} from "../lib/openai.js";

const EXPECTED_DIMS = 1536;

export async function handle(
  req: EmbedRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  try {
    const { vector, tokensIn, tokensOut } = await embedText(req.payload.text);

    if (vector.length !== EXPECTED_DIMS) {
      return malformed(req.requestId);
    }
    for (const v of vector) {
      if (typeof v !== "number" || !Number.isFinite(v)) {
        return malformed(req.requestId);
      }
    }

    return {
      response: {
        type: "embed_response",
        requestId: req.requestId,
        vector,
        modelLabel: MODEL_EMBED_LABEL,
      },
      model: MODEL_EMBED,
      modelLabel: MODEL_EMBED_LABEL,
      tokensIn,
      tokensOut,
      cacheHit: false,
    };
  } catch (e) {
    if (e instanceof OpenAICallError) {
      return {
        response: {
          type: "error",
          requestId: req.requestId,
          code: openaiFailureToErrorCode(e.failure),
          message: openaiFailureToMessage(e.failure),
        },
        model: MODEL_EMBED,
        modelLabel: MODEL_EMBED_LABEL,
        tokensIn: 0,
        tokensOut: 0,
        cacheHit: false,
      };
    }
    throw e;
  }
}

function malformed(requestId: string): HandlerResult {
  return {
    response: {
      type: "error",
      requestId,
      code: "MALFORMED_RESPONSE",
      message: "OpenAI returned malformed response",
    },
    model: MODEL_EMBED,
    modelLabel: MODEL_EMBED_LABEL,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
  };
}
