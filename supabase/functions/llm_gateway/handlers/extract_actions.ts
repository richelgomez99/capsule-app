// Spec 014 — extract_actions handler (Phase D, T014-009).
// Sonnet, 30s timeout, no prompt cache. Validates upstream JSON output as
// ActionProposal[]; schema-invalid → MALFORMED_RESPONSE.

import type {
  ExtractActionsRequest,
  HandlerContext,
  HandlerResult,
  ActionProposalJson,
} from "../types.js";
import { callAnthropic, AnthropicCallError } from "../lib/anthropic.js";
import { ActionProposalArraySchema } from "../lib/schemas.js";
import {
  MODEL_SONNET,
  MODEL_SONNET_LABEL,
  TIMEOUT_DEFAULT_MS,
  failureToErrorCode,
  failureToMessage,
} from "../lib/models.js";

const SYSTEM_PROMPT =
  'You extract actionable proposals from text. Output STRICT JSON: an array of objects with keys functionId, args, confidence (0..1), rationale (string|null). No markdown, no commentary.';

export async function handle(
  req: ExtractActionsRequest,
  _ctx: HandlerContext,
): Promise<HandlerResult> {
  const userText = JSON.stringify({
    text: req.payload.text,
    contentType: req.payload.contentType,
    state: req.payload.state,
    registeredFunctions: req.payload.registeredFunctions,
    maxCandidates: req.payload.maxCandidates,
  });

  try {
    const { text, tokensIn, tokensOut } = await callAnthropic({
      model: MODEL_SONNET,
      system: SYSTEM_PROMPT,
      userText,
      maxTokens: 1024,
      timeoutMs: TIMEOUT_DEFAULT_MS,
    });

    let raw: unknown;
    try {
      raw = JSON.parse(stripCodeFence(text));
    } catch {
      return malformed(req.requestId);
    }
    const parsed = ActionProposalArraySchema.safeParse(raw);
    if (!parsed.success) {
      return malformed(req.requestId);
    }
    const proposals: ActionProposalJson[] = parsed.data;

    return {
      response: {
        type: "extract_actions_response",
        requestId: req.requestId,
        proposals,
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

function malformed(requestId: string): HandlerResult {
  return {
    response: {
      type: "error",
      requestId,
      code: "MALFORMED_RESPONSE",
      message: "Upstream returned malformed response",
    },
    model: MODEL_SONNET,
    modelLabel: MODEL_SONNET_LABEL,
    tokensIn: 0,
    tokensOut: 0,
    cacheHit: false,
  };
}

function stripCodeFence(s: string): string {
  const trimmed = s.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(trimmed);
  return fenced && fenced[1] ? fenced[1].trim() : trimmed;
}
