// Spec 014 — classify_intent handler (Phase D, T014-010).
// Haiku, 30s timeout, prompt-cached system prefix per FR-014-010/011.
// `cacheHit` is derived from `usage.cache_read_input_tokens > 0`.

import type {
  ClassifyIntentRequest,
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
import { sanitizeIntent } from "../lib/allowlists.js";

// Stable system prefix — eligible for ephemeral cache. Variable per-call
// fields (text, appCategory) live in the user message so the prefix hashes
// identically across requests.
const SYSTEM_PREFIX =
  "You classify short user-app utterances into one of these intent labels: " +
  "WANT_IT, REFERENCE, READ_LATER, FOR_SOMEONE, INTERESTING, AMBIGUOUS. " +
  "WANT_IT means the user wants to act on or keep this; REFERENCE means source material; " +
  "READ_LATER means consume later; FOR_SOMEONE means save/share for another person; " +
  "INTERESTING means general curiosity or inspiration; AMBIGUOUS means unclear. " +
  'Respond with STRICT JSON only: {"intent":"<LABEL>","confidence":<0..1 number>}. ' +
  "Output JSON only — no markdown, no commentary, no preamble.";

export async function handle(
  req: ClassifyIntentRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  const userText = JSON.stringify({
    text: req.payload.text,
    appCategory: req.payload.appCategory,
  });

  try {
    const { text, cacheHit, tokensIn, tokensOut } = await callAnthropic({
      model: MODEL_HAIKU,
      system: cachedSystemPrompt(SYSTEM_PREFIX),
      userText,
      maxTokens: 64,
      timeoutMs: TIMEOUT_DEFAULT_MS,
      cacheBeta: true,
    });

    let raw: unknown;
    try {
      raw = JSON.parse(stripCodeFence(text));
    } catch {
      return malformed(req.requestId, cacheHit, tokensIn, tokensOut);
    }
    const obj = raw as { intent?: unknown; confidence?: unknown };
    if (
      typeof obj.intent !== "string" ||
      obj.intent.length === 0 ||
      typeof obj.confidence !== "number" ||
      !Number.isFinite(obj.confidence) ||
      obj.confidence < 0 ||
      obj.confidence > 1
    ) {
      return malformed(req.requestId, cacheHit, tokensIn, tokensOut);
    }

    // Spec 014 hotfix — collapse out-of-set intent labels to AMBIGUOUS. The
    // system prompt enumerates the Android intent labels but Haiku occasionally returns
    // synonyms or prompt-injected strings; closed-set enforcement happens
    // here, AFTER schema validation.
    const intent = sanitizeIntent(obj.intent);

    return {
      response: {
        type: "classify_intent_response",
        requestId: req.requestId,
        intent,
        confidence: obj.confidence,
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

function stripCodeFence(s: string): string {
  const trimmed = s.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(trimmed);
  return fenced && fenced[1] ? fenced[1].trim() : trimmed;
}
