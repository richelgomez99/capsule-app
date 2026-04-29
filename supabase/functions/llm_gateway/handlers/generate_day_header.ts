// Spec 014 — generate_day_header handler (Phase D, T014-009).
// Sonnet, 30s timeout, no prompt cache.

import type {
  GenerateDayHeaderRequest,
  HandlerContext,
  HandlerResult,
} from "../types.js";
import { callAnthropic, AnthropicCallError } from "../lib/anthropic.js";
import {
  MODEL_SONNET,
  MODEL_SONNET_LABEL,
  TIMEOUT_DEFAULT_MS,
  failureToErrorCode,
  failureToMessage,
} from "../lib/models.js";

const SYSTEM_PROMPT =
  "You write short, evocative one-line headers for a day's diary. Given a list of envelope summaries, return one line of plain text — no quotes, no preamble.";

export async function handle(
  req: GenerateDayHeaderRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  const userText = JSON.stringify({
    dayIsoDate: req.payload.dayIsoDate,
    envelopeSummaries: req.payload.envelopeSummaries,
  });

  try {
    const { text, tokensIn, tokensOut } = await callAnthropic({
      model: MODEL_SONNET,
      system: SYSTEM_PROMPT,
      userText,
      maxTokens: 128,
      timeoutMs: TIMEOUT_DEFAULT_MS,
    });
    return {
      response: {
        type: "generate_day_header_response",
        requestId: req.requestId,
        header: text.trim(),
        modelLabel: MODEL_SONNET_LABEL,
      },
      model: MODEL_SONNET,
      modelLabel: MODEL_SONNET_LABEL,
      tokensIn,
      tokensOut,
      cacheHit: false,
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
        model: MODEL_SONNET,
        modelLabel: MODEL_SONNET_LABEL,
        tokensIn: 0,
        tokensOut: 0,
        cacheHit: false,
      };
    }
    throw e;
  }
}
