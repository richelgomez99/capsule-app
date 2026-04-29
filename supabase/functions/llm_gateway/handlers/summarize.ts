// Spec 014 — summarize handler (Phase D, T014-009).
// Sonnet, 60s timeout, no prompt cache. Maps upstream failures per
// gateway-request-response.md §6 to GATEWAY_5XX / TIMEOUT / MALFORMED_RESPONSE.

import type { SummarizeRequest, HandlerContext, HandlerResult } from "../types.js";
import { callAnthropic, AnthropicCallError } from "../lib/anthropic.js";
import {
  MODEL_SONNET,
  MODEL_SONNET_LABEL,
  TIMEOUT_SUMMARIZE_MS,
  failureToErrorCode,
  failureToMessage,
} from "../lib/models.js";

const SYSTEM_PROMPT =
  "You are a concise summarizer. Produce a single-sentence summary of the user's text. Output the summary only — no preamble, no quoting.";

export async function handle(
  req: SummarizeRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  try {
    const { text, tokensIn, tokensOut } = await callAnthropic({
      model: MODEL_SONNET,
      system: SYSTEM_PROMPT,
      userText: req.payload.text,
      maxTokens: req.payload.maxTokens,
      timeoutMs: TIMEOUT_SUMMARIZE_MS,
    });
    return {
      response: {
        type: "summarize_response",
        requestId: req.requestId,
        summary: text,
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
