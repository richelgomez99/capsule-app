// Spec 014 — Anthropic SDK wrapper.
// Uses the official @anthropic-ai/sdk with `baseURL` swapped to the Vercel
// AI Gateway endpoint (per research.md §1, §8). The same `ANTHROPIC_API_KEY`
// env var doubles as the Vercel AI Gateway key per plan.md Open Clarification #2.
//
// Constitution Principle XIV — Bounded Observation: this module MUST NOT log
// prompt text, response text, or any Anthropic API content. Errors are
// thrown as classified categories; the caller maps to the seven-value
// ErrorCode enum.

import Anthropic from "@anthropic-ai/sdk";

const DEFAULT_GATEWAY_BASE_URL = "https://gateway.ai.vercel.app/v1/anthropic";

/** Categorised upstream-failure modes; handlers map to ErrorCode. */
export type AnthropicFailure =
  | { kind: "timeout" }
  | { kind: "gateway_5xx"; status: number }
  | { kind: "malformed"; reason: string };

export class AnthropicCallError extends Error {
  constructor(public readonly failure: AnthropicFailure) {
    super(`anthropic call failed: ${failure.kind}`);
    this.name = "AnthropicCallError";
  }
}

let cachedClient: Anthropic | null = null;

/** Lazy SDK client (env vars are read once per function cold start). */
function client(): Anthropic {
  if (cachedClient) return cachedClient;
  const apiKey = process.env.ANTHROPIC_API_KEY ?? "";
  const baseURL = process.env.VERCEL_AI_GATEWAY_URL ?? DEFAULT_GATEWAY_BASE_URL;
  cachedClient = new Anthropic({ apiKey, baseURL });
  return cachedClient;
}

/** Reset the memoized client (test-only). */
export function _resetClientForTest(): void {
  cachedClient = null;
}

export interface CachedSystemBlock {
  type: "text";
  text: string;
  cache_control: { type: "ephemeral" };
}

/**
 * Build the Anthropic system-prompt content-block shape for prompt caching.
 * Caller passes the resulting array as the `system` field on `messages.create`.
 */
export function cachedSystemPrompt(prefix: string): [CachedSystemBlock] {
  return [{ type: "text", text: prefix, cache_control: { type: "ephemeral" } }];
}

export interface CallAnthropicResult {
  text: string;
  /** True iff `usage.cache_read_input_tokens > 0`. */
  cacheHit: boolean;
  tokensIn: number;
  tokensOut: number;
}

export interface CallAnthropicOptions {
  model: string;
  /** Optional system prompt — string OR cached content-block array. */
  system?: string | CachedSystemBlock[];
  userText: string;
  maxTokens: number;
  /** Per-call timeout in ms. */
  timeoutMs: number;
  /** Set true on Haiku cached path; sends prompt-caching beta header. */
  cacheBeta?: boolean;
}

/**
 * Single-turn user message call. Returns extracted text + usage. On any
 * upstream failure throws AnthropicCallError with a classified failure.
 */
export async function callAnthropic(
  opts: CallAnthropicOptions,
): Promise<CallAnthropicResult> {
  const c = client();
  try {
    const response = await c.messages.create(
      {
        model: opts.model,
        max_tokens: opts.maxTokens,
        ...(opts.system !== undefined ? { system: opts.system as never } : {}),
        messages: [{ role: "user", content: opts.userText }],
      },
      {
        timeout: opts.timeoutMs,
        ...(opts.cacheBeta
          ? { headers: { "anthropic-beta": "prompt-caching-2024-07-31" } }
          : {}),
      },
    );

    // Extract text from content blocks.
    const blocks = (response as { content?: Array<{ type: string; text?: string }> }).content;
    if (!Array.isArray(blocks) || blocks.length === 0) {
      throw new AnthropicCallError({ kind: "malformed", reason: "no content blocks" });
    }
    const text = blocks
      .filter((b) => b.type === "text" && typeof b.text === "string")
      .map((b) => b.text!)
      .join("");
    if (text.length === 0) {
      throw new AnthropicCallError({ kind: "malformed", reason: "empty text" });
    }

    const usage = (response as {
      usage?: {
        input_tokens?: number;
        output_tokens?: number;
        cache_read_input_tokens?: number;
        cache_creation_input_tokens?: number;
      };
    }).usage ?? {};
    const tokensIn =
      (usage.input_tokens ?? 0) +
      (usage.cache_read_input_tokens ?? 0) +
      (usage.cache_creation_input_tokens ?? 0);
    const tokensOut = usage.output_tokens ?? 0;
    const cacheHit = (usage.cache_read_input_tokens ?? 0) > 0;

    return { text, cacheHit, tokensIn, tokensOut };
  } catch (e) {
    if (e instanceof AnthropicCallError) throw e;
    // Anthropic SDK throws APIError subclasses with `status`; SDK timeout
    // surfaces as APIConnectionTimeoutError with no `status`.
    const err = e as { status?: number; name?: string; message?: string };
    if (err && typeof err.status === "number" && err.status >= 500) {
      throw new AnthropicCallError({ kind: "gateway_5xx", status: err.status });
    }
    if (
      err?.name === "APIConnectionTimeoutError" ||
      /timeout/i.test(err?.message ?? "")
    ) {
      throw new AnthropicCallError({ kind: "timeout" });
    }
    // Per gateway-request-response.md §6, Anthropic 4xx collapses to GATEWAY_5XX
    // for Day 2 (uniform retry-once on the client side).
    if (err && typeof err.status === "number") {
      throw new AnthropicCallError({ kind: "gateway_5xx", status: err.status });
    }
    throw new AnthropicCallError({ kind: "malformed", reason: err?.message ?? "unknown" });
  }
}
