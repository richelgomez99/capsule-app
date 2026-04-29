// Spec 014 — error codes + standard error envelope.
// ErrorCode enum mirrors data-model.md §2.1 (seven values) and matches
// the Day-1 Kotlin `LlmGatewayResponse.Error.code` strings.

export const ErrorCodes = {
  NETWORK_UNAVAILABLE: "NETWORK_UNAVAILABLE",
  GATEWAY_5XX: "GATEWAY_5XX",
  PROVIDER_5XX: "PROVIDER_5XX",
  TIMEOUT: "TIMEOUT",
  MALFORMED_RESPONSE: "MALFORMED_RESPONSE",
  UNAUTHORIZED: "UNAUTHORIZED",
  INTERNAL: "INTERNAL",
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];

/**
 * Closed-enum UNAUTHORIZED message strings per auth-jwt-contract.md §3.
 * The function MUST emit one of these and nothing else on the auth path.
 */
export const UNAUTHORIZED_MESSAGES = {
  MISSING_OR_INVALID_HEADER: "Missing or invalid Authorization header",
  TOKEN_VERIFICATION_FAILED: "Token verification failed",
  INVALID_SUBJECT: "Invalid subject claim",
  INVALID_ROLE: "Invalid role claim",
} as const;

export type UnauthorizedMessage =
  (typeof UNAUTHORIZED_MESSAGES)[keyof typeof UNAUTHORIZED_MESSAGES];

/**
 * Thrown by `verifyJwt` for any auth-gate failure. Carries one of the
 * closed-enum messages. The function MUST NOT propagate any other error
 * detail to the client (auth-jwt-contract.md §3).
 */
export class UnauthorizedError extends Error {
  constructor(public readonly publicMessage: UnauthorizedMessage) {
    super(publicMessage);
    this.name = "UnauthorizedError";
  }
}

/**
 * Build the standard error response envelope. Mirrors the Day-1 wire shape
 * `{type:"error", requestId, ok:false, error:{code, message}}` so the
 * Android `LlmGatewayClient.unwrapHttpEnvelope` parses it correctly.
 */
export function errorResponse(
  code: ErrorCode,
  message: string,
  requestId: string,
  status: 200 | 401 | 405 | 500 = 200,
): Response {
  const body = {
    type: "error" as const,
    requestId,
    ok: false as const,
    error: { code, message },
  };
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
