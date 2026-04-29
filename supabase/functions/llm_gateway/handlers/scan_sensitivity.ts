// Spec 014 — scan_sensitivity handler (Phase D, T014-010).
// Haiku, 30s timeout, prompt-cached system prefix.

import type {
  ScanSensitivityRequest,
  HandlerContext,
  HandlerResult,
} from "../types.js";
import {
  callAnthropic,
  cachedSystemPrompt,
  AnthropicCallError,
} from "../lib/anthropic.js";
import {
  MODEL_HAIKU,
  MODEL_HAIKU_LABEL,
  TIMEOUT_DEFAULT_MS,
  failureToErrorCode,
  failureToMessage,
} from "../lib/models.js";

const SYSTEM_PREFIX =
  "You scan short text for sensitivity tags. " +
  "Possible tags: PII, FINANCIAL, MEDICAL, LEGAL, CREDENTIAL, CHILD_SAFETY, NONE. " +
  'Respond with STRICT JSON only: {"tags":["TAG1","TAG2",...]} (use ["NONE"] when nothing applies). ' +
  "Output JSON only — no markdown, no commentary.";

export async function handle(
  req: ScanSensitivityRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  try {
    const { text, cacheHit, tokensIn, tokensOut } = await callAnthropic({
      model: MODEL_HAIKU,
      system: cachedSystemPrompt(SYSTEM_PREFIX),
      userText: req.payload.text,
      maxTokens: 64,
      timeoutMs: TIMEOUT_DEFAULT_MS,
      cacheBeta: true,
    });

    let raw: unknown;
    try {
      raw = JSON.parse(text);
    } catch {
      return malformed(req.requestId, cacheHit, tokensIn, tokensOut);
    }
    const obj = raw as { tags?: unknown };
    if (
      !Array.isArray(obj.tags) ||
      !obj.tags.every((t) => typeof t === "string" && t.length > 0)
    ) {
      return malformed(req.requestId, cacheHit, tokensIn, tokensOut);
    }

    return {
      response: {
        type: "scan_sensitivity_response",
        requestId: req.requestId,
        tags: obj.tags as string[],
        modelLabel: MODEL_HAIKU_LABEL,
      },
      model: MODEL_HAIKU,
      modelLabel: MODEL_HAIKU_LABEL,
      tokensIn,
      tokensOut,
      cacheHit,
    };
  } catch (e) {
    if (e instanceof AnthropicCallError) {
      return {
        response: {
          type: "error",
          requestId: req.requestId,
          code: failureToErrorCode(e.failure),
          message: failureToMessage(e.failure),
        },
        model: MODEL_HAIKU,
        modelLabel: MODEL_HAIKU_LABEL,
        tokensIn: 0,
        tokensOut: 0,
        cacheHit: false,
      };
    }
    throw e;
  }
}

function malformed(
  requestId: string,
  cacheHit: boolean,
  tokensIn: number,
  tokensOut: number,
): HandlerResult {
  return {
    response: {
      type: "error",
      requestId,
      code: "MALFORMED_RESPONSE",
      message: "Upstream returned malformed response",
    },
    model: MODEL_HAIKU,
    modelLabel: MODEL_HAIKU_LABEL,
    tokensIn,
    tokensOut,
    cacheHit,
  };
}
