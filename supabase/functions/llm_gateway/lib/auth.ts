// Spec 014 — JWT auth gate. Implements auth-jwt-contract.md §2 verbatim.
// Every code path that fails throws UnauthorizedError with one of the
// closed-enum public messages. NO console.log / console.error in this file
// (Constitution Principle XIV — Bounded Observation).

import { jwtVerify, errors as joseErrors } from "jose";
import { UnauthorizedError, UNAUTHORIZED_MESSAGES } from "./errors.js";

// Accepts UUIDv1–v5 (Supabase auth.users.id is technically v4 but we don't
// enforce the v4 marker here per auth-jwt-contract.md §2).
const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const BEARER_REGEX = /^Bearer\s+(.+)$/i;

export interface VerifiedJwt {
  sub: string;
}

/**
 * Verify a Supabase HS256-signed JWT from an `Authorization` header value.
 * Throws `UnauthorizedError` for any failure. Never logs the token, claims,
 * or stack traces.
 */
export async function verifyJwt(
  rawHeader: string | null,
): Promise<VerifiedJwt> {
  // Step 1: header parse
  if (!rawHeader || !BEARER_REGEX.test(rawHeader)) {
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.MISSING_OR_INVALID_HEADER);
  }
  const token = rawHeader.replace(/^Bearer\s+/i, "").trim();
  if (token.length === 0) {
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.MISSING_OR_INVALID_HEADER);
  }

  const secretEnv = process.env.SUPABASE_JWT_SECRET;
  const urlEnv = process.env.SUPABASE_URL;
  if (!secretEnv || !urlEnv) {
    // Misconfiguration. Defensive collapse to TOKEN_VERIFICATION_FAILED so
    // we never leak which env var is missing.
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED);
  }

  // Step 2: verify signature, exp, iss
  const secret = new TextEncoder().encode(secretEnv);
  let payload: Record<string, unknown>;
  try {
    const result = await jwtVerify(token, secret, {
      issuer: `${urlEnv}/auth/v1`,
    });
    payload = result.payload as Record<string, unknown>;
  } catch (e) {
    // jose throws JWTExpired / JWTInvalid / JWSSignatureVerificationFailed /
    // JWTClaimValidationFailed — all collapse to one public message.
    if (
      e instanceof joseErrors.JOSEError ||
      e instanceof Error
    ) {
      throw new UnauthorizedError(
        UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
      );
    }
    throw new UnauthorizedError(
      UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
    );
  }

  // Step 3: sub presence + UUID shape
  const sub = payload["sub"];
  if (typeof sub !== "string" || !UUID_REGEX.test(sub)) {
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.INVALID_SUBJECT);
  }

  // Step 4: defensive role check
  if (payload["role"] !== "authenticated") {
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.INVALID_ROLE);
  }

  return { sub };
}
