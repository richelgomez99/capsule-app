// Spec 014 — JWT auth gate. Implements auth-jwt-contract.md §2 verbatim.
// Every code path that fails throws UnauthorizedError with one of the
// closed-enum public messages. NO console.log / console.error in this file
// (Constitution Principle XIV — Bounded Observation).
//
// T10 update (2026-04-29): Supabase migrated all projects to asymmetric
// signing keys (ES256). New tokens are no longer HS256-signed. We now
// verify via the project's JWKS endpoint and accept ES256/RS256/HS256
// (HS256 retained only as a defensive fallback for any legacy token still
// in flight; no PII leaks from this file).

import { jwtVerify, createRemoteJWKSet, errors as joseErrors } from "jose";
import { UnauthorizedError, UNAUTHORIZED_MESSAGES } from "./errors.js";

// Accepts UUIDv1–v5 (Supabase auth.users.id is technically v4 but we don't
// enforce the v4 marker here per auth-jwt-contract.md §2).
const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const BEARER_REGEX = /^Bearer\s+(.+)$/i;

export interface VerifiedJwt {
  sub: string;
}

// Module-level JWKS cache. `createRemoteJWKSet` returns a function that
// transparently caches the JWKS response and refreshes on key rotation
// (jose default: 30s cooldown between fetches). Lazily initialised on the
// first call so unit tests that never hit verifyJwt do not perform a
// network fetch.
let jwksResolver: ReturnType<typeof createRemoteJWKSet> | null = null;
let jwksUrlForResolver: string | null = null;

function getJwksResolver(supabaseUrl: string): ReturnType<typeof createRemoteJWKSet> {
  const url = `${supabaseUrl}/auth/v1/.well-known/jwks.json`;
  if (jwksResolver === null || jwksUrlForResolver !== url) {
    jwksResolver = createRemoteJWKSet(new URL(url));
    jwksUrlForResolver = url;
  }
  return jwksResolver;
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
  if (!urlEnv) {
    // Misconfiguration. Defensive collapse to TOKEN_VERIFICATION_FAILED so
    // we never leak which env var is missing.
    throw new UnauthorizedError(UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED);
  }

  // Step 2: verify signature, exp, iss.
  // Primary path: JWKS (ES256/RS256) — current Supabase asymmetric keys.
  // Fallback path: HS256 with the legacy shared secret, only attempted if
  // JWKS verification fails AND a legacy secret is configured.
  const issuer = `${urlEnv}/auth/v1`;
  let payload: Record<string, unknown>;
  try {
    const jwks = getJwksResolver(urlEnv);
    const result = await jwtVerify(token, jwks, { issuer });
    payload = result.payload as Record<string, unknown>;
  } catch (jwksErr) {
    // Try HS256 fallback (legacy tokens issued before asymmetric keys).
    if (secretEnv) {
      try {
        const secret = new TextEncoder().encode(secretEnv);
        const result = await jwtVerify(token, secret, { issuer });
        payload = result.payload as Record<string, unknown>;
      } catch (hsErr) {
        if (
          hsErr instanceof joseErrors.JOSEError ||
          hsErr instanceof Error
        ) {
          throw new UnauthorizedError(
            UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
          );
        }
        throw new UnauthorizedError(
          UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
        );
      }
    } else if (
      jwksErr instanceof joseErrors.JOSEError ||
      jwksErr instanceof Error
    ) {
      throw new UnauthorizedError(
        UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
      );
    } else {
      throw new UnauthorizedError(
        UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
      );
    }
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
