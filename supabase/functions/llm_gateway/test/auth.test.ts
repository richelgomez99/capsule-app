// T014-006 — auth gate unit tests covering the six failure modes from
// auth-jwt-contract.md §1 plus the happy path.

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { SignJWT } from "jose";
import { verifyJwt } from "../lib/auth.js";
import { UnauthorizedError, UNAUTHORIZED_MESSAGES } from "../lib/errors.js";

const TEST_SECRET = "test-jwt-secret-must-be-long-enough-32+";
const TEST_SUPABASE_URL = "https://test-project.supabase.co";
const VALID_SUB = "11111111-1111-4111-8111-111111111111";

let savedSecret: string | undefined;
let savedUrl: string | undefined;

beforeAll(() => {
  savedSecret = process.env.SUPABASE_JWT_SECRET;
  savedUrl = process.env.SUPABASE_URL;
  process.env.SUPABASE_JWT_SECRET = TEST_SECRET;
  process.env.SUPABASE_URL = TEST_SUPABASE_URL;
});

afterAll(() => {
  process.env.SUPABASE_JWT_SECRET = savedSecret ?? "";
  process.env.SUPABASE_URL = savedUrl ?? "";
});

async function mintToken(opts: {
  sub?: string;
  role?: string;
  expiresIn?: string;
  issuer?: string;
  secret?: string;
}): Promise<string> {
  const builder = new SignJWT({
    role: opts.role ?? "authenticated",
    aud: "authenticated",
    ...(opts.sub !== undefined ? { sub: opts.sub } : {}),
  })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setIssuer(opts.issuer ?? `${TEST_SUPABASE_URL}/auth/v1`)
    .setExpirationTime(opts.expiresIn ?? "1h");
  if (opts.sub !== undefined) builder.setSubject(opts.sub);
  const key = new TextEncoder().encode(opts.secret ?? TEST_SECRET);
  return await builder.sign(key);
}

describe("verifyJwt — failure modes (auth-jwt-contract.md §1)", () => {
  it("rejects missing header (mode 1)", async () => {
    await expect(verifyJwt(null)).rejects.toThrow(UnauthorizedError);
    await expect(verifyJwt(null)).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.MISSING_OR_INVALID_HEADER,
    });
  });

  it("rejects scheme mismatch (mode 2)", async () => {
    await expect(verifyJwt("Basic abc123")).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.MISSING_OR_INVALID_HEADER,
    });
  });

  it("rejects empty token after Bearer scheme (mode 3)", async () => {
    await expect(verifyJwt("Bearer    ")).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.MISSING_OR_INVALID_HEADER,
    });
  });

  it("rejects forged signature (mode 4 — jose verification fails)", async () => {
    const tok = await mintToken({ sub: VALID_SUB, secret: "wrong-secret-padded-to-32-chars-min!" });
    await expect(verifyJwt(`Bearer ${tok}`)).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.TOKEN_VERIFICATION_FAILED,
    });
  });

  it("rejects token with missing sub (mode 5)", async () => {
    const tok = await mintToken({ sub: undefined });
    await expect(verifyJwt(`Bearer ${tok}`)).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.INVALID_SUBJECT,
    });
  });

  it("rejects token with non-authenticated role (mode 6 — service_role)", async () => {
    const tok = await mintToken({ sub: VALID_SUB, role: "service_role" });
    await expect(verifyJwt(`Bearer ${tok}`)).rejects.toMatchObject({
      publicMessage: UNAUTHORIZED_MESSAGES.INVALID_ROLE,
    });
  });
});

describe("verifyJwt — happy path", () => {
  it("returns sub for a valid Supabase-shaped JWT", async () => {
    const tok = await mintToken({ sub: VALID_SUB });
    const result = await verifyJwt(`Bearer ${tok}`);
    expect(result.sub).toBe(VALID_SUB);
  });

  it("accepts case-insensitive Bearer scheme", async () => {
    const tok = await mintToken({ sub: VALID_SUB });
    const result = await verifyJwt(`bearer ${tok}`);
    expect(result.sub).toBe(VALID_SUB);
  });
});
