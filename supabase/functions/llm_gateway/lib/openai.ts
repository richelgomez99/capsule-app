// Spec 014 — OpenAI SDK wrapper for embeddings (Phase E, T014-012).
//
// OpenAI is called DIRECTLY (no Vercel AI Gateway) per research.md §1: the
// AI Gateway is Anthropic-only for Day 2. `text-embedding-3-small` with
// `dimensions: 1536` (FR-014-005, gateway-request-response.md §4.1).
//
// Constitution Principle XIV — Bounded Observation: this module MUST NOT
// log embed input text or vector contents. Errors are thrown as classified
// categories; the caller maps to the seven-value ErrorCode enum.

import OpenAI from "openai";

import { MODEL_EMBED } from "./models.js";

/** Categorised upstream-failure modes for OpenAI; handlers map to ErrorCode. */
export type OpenAIFailure =
  | { kind: "timeout" }
  | { kind: "provider_5xx"; status: number }
  | { kind: "malformed"; reason: string };

export class OpenAICallError extends Error {
  constructor(public readonly failure: OpenAIFailure) {
    super(`openai call failed: ${failure.kind}`);
    this.name = "OpenAICallError";
  }
}

let cachedClient: OpenAI | null = null;

function client(): OpenAI {
  if (cachedClient) return cachedClient;
  const apiKey = process.env.OPENAI_API_KEY ?? "";
  cachedClient = new OpenAI({ apiKey });
  return cachedClient;
}

/** Reset the memoized client (test-only). */
export function _resetClientForTest(): void {
  cachedClient = null;
}

export interface EmbedTextResult {
  vector: number[];
  tokensIn: number;
  tokensOut: number;
}

const EMBED_TIMEOUT_MS = 30_000;
const EXPECTED_DIMS = 1536;

/**
 * Single-text embedding call. Returns the vector + token usage. On any
 * upstream failure throws OpenAICallError with a classified failure.
 *
 * The caller (handlers/embed.ts) is responsible for the dimension/finite
 * post-validation and the MALFORMED_RESPONSE mapping; this wrapper only
 * surfaces the raw vector and SDK-level failures.
 */
export async function embedText(text: string): Promise<EmbedTextResult> {
  const c = client();
  try {
    const response = await c.embeddings.create(
      {
        model: MODEL_EMBED,
        input: text,
        dimensions: EXPECTED_DIMS,
      },
      { timeout: EMBED_TIMEOUT_MS },
    );

    const data = (response as { data?: Array<{ embedding?: unknown }> }).data;
    if (!Array.isArray(data) || data.length === 0) {
      throw new OpenAICallError({ kind: "malformed", reason: "no embedding in response" });
    }
    const vector = data[0]?.embedding;
    if (!Array.isArray(vector)) {
      throw new OpenAICallError({ kind: "malformed", reason: "embedding not an array" });
    }
    const usage = (response as { usage?: { prompt_tokens?: number } }).usage ?? {};
    const tokensIn = usage.prompt_tokens ?? 0;
    return {
      vector: vector as number[],
      tokensIn,
      // Embeddings have no output tokens.
      tokensOut: 0,
    };
  } catch (e) {
    if (e instanceof OpenAICallError) throw e;
    const err = e as { status?: number; name?: string; message?: string };
    if (err && typeof err.status === "number" && err.status >= 500) {
      throw new OpenAICallError({ kind: "provider_5xx", status: err.status });
    }
    if (
      err?.name === "APIConnectionTimeoutError" ||
      /timeout/i.test(err?.message ?? "")
    ) {
      throw new OpenAICallError({ kind: "timeout" });
    }
    if (err && typeof err.status === "number") {
      // 4xx collapses to PROVIDER_5XX for Day 2 (uniform retry-once on client).
      throw new OpenAICallError({ kind: "provider_5xx", status: err.status });
    }
    throw new OpenAICallError({ kind: "malformed", reason: err?.message ?? "unknown" });
  }
}

/** Map an OpenAI failure to the seven-value ErrorCode enum. */
export function openaiFailureToErrorCode(
  f: OpenAIFailure,
): "TIMEOUT" | "PROVIDER_5XX" | "MALFORMED_RESPONSE" {
  switch (f.kind) {
    case "timeout":
      return "TIMEOUT";
    case "provider_5xx":
      return "PROVIDER_5XX";
    case "malformed":
      return "MALFORMED_RESPONSE";
  }
}

export function openaiFailureToMessage(f: OpenAIFailure): string {
  switch (f.kind) {
    case "timeout":
      return "upstream request timed out";
    case "provider_5xx":
      // Do not leak provider name or status code to the client.
      return "upstream gateway error";
    case "malformed":
      return "upstream returned malformed response";
  }
}
