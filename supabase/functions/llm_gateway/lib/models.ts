// Spec 014 — model labels per gateway-request-response.md §3.
// These strings are wire-stable; updates require a spec amendment.

export const MODEL_SONNET = "claude-sonnet-4-6";
export const MODEL_SONNET_LABEL = "anthropic/claude-sonnet-4-6";

export const MODEL_HAIKU = "claude-haiku-4-5";
export const MODEL_HAIKU_LABEL = "anthropic/claude-haiku-4-5";

export const MODEL_EMBED = "text-embedding-3-small";
export const MODEL_EMBED_LABEL = "openai/text-embedding-3-small";

/** Per-type upstream timeouts (gateway-request-response.md §3). */
export const TIMEOUT_SUMMARIZE_MS = 60_000;
export const TIMEOUT_DEFAULT_MS = 30_000;

import type { AnthropicFailure } from "./anthropic.js";
import type { ErrorCode } from "./errors.js";

/** Map Anthropic upstream-failure to the seven-value ErrorCode enum. */
export function failureToErrorCode(f: AnthropicFailure): ErrorCode {
  switch (f.kind) {
    case "timeout":
      return "TIMEOUT";
    case "gateway_5xx":
      return "GATEWAY_5XX";
    case "malformed":
      return "MALFORMED_RESPONSE";
  }
}

/** Build a short, content-free human message for an upstream failure. */
export function failureToMessage(f: AnthropicFailure): string {
  switch (f.kind) {
    case "timeout":
      return "upstream request timed out";
    case "gateway_5xx":
      // Do not leak the upstream provider or status code to the client.
      // Logs (audit row + structured error log) carry the real details.
      return "upstream gateway error";
    case "malformed":
      return "upstream returned malformed response";
  }
}
