// Spec 014 — wire-envelope serializer.
// Wraps the FLAT internal LlmGatewayResponse types from ../types.ts into
// the HTTP envelope shape `{type, requestId, ok, data | error}` per
// envelope-contract §3 (so the Day-1 Android client `unwrapHttpEnvelope`
// parses correctly).

import type { LlmGatewayResponse } from "../types.js";

/**
 * Build the on-wire JSON for any LlmGatewayResponse variant. Errors wrap
 * into `{type:"error", requestId, ok:false, error:{code,message}}`. Successes
 * wrap into `{type, requestId, ok:true, data:{...other fields}}`.
 */
export function toWireBody(response: LlmGatewayResponse): Record<string, unknown> {
  if (response.type === "error") {
    return {
      type: "error",
      requestId: response.requestId,
      ok: false,
      error: { code: response.code, message: response.message },
    };
  }
  // Strip type + requestId from the payload; everything else becomes `data`.
  const { type, requestId, ...rest } = response as LlmGatewayResponse & Record<string, unknown>;
  return {
    type,
    requestId,
    ok: true,
    data: rest,
  };
}

/**
 * Build a `Response` object for a LlmGatewayResponse with HTTP 200 (the
 * function emits 200 for every body except auth-gate failures, which use
 * `errorResponse(...)` directly).
 */
export function wireResponse(response: LlmGatewayResponse): Response {
  return new Response(JSON.stringify(toWireBody(response)), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}
