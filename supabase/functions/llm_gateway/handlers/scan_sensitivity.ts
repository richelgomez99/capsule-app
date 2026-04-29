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
import { sanitizeSensitivityTags } from "../lib/allowlists.js";

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
    const stripped = stripCodeFence(text);
    try {
      raw = JSON.parse(stripped);
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
    // Spec 014 hotfix — closed-set allowlist filter. Drops unknown tags,
    // dedupes, defaults to ["NONE"] when nothing valid remains. Prevents
    // hostile or hallucinated tags from crossing the handler boundary.
    const tags = sanitizeSensitivityTags(obj.tags as string[]);

    return {
      response: {
        type: "scan_sensitivity_response",
        requestId: req.requestId,
        tags,
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

/**
 * Strip ```json ... ``` or ``` ... ``` markdown fences that some models
 * include despite "JSON only" instructions. Returns the inner content
 * trimmed of whitespace.
 */
function stripCodeFence(s: string): string {
  const trimmed = s.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(trimmed);
  return fenced && fenced[1] ? fenced[1].trim() : trimmed;
}
