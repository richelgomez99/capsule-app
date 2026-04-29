// T014-008 — router dispatch tests. Each of the six valid bodies dispatches
// to the matching stub handler; malformed bodies return INTERNAL.

import { describe, it, expect, beforeAll, afterAll, vi } from "vitest";
import { SignJWT } from "jose";

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

async function validJwt(): Promise<string> {
  const key = new TextEncoder().encode(TEST_SECRET);
  return await new SignJWT({ role: "authenticated", aud: "authenticated" })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setSubject(VALID_SUB)
    .setIssuer(`${TEST_SUPABASE_URL}/auth/v1`)
    .setExpirationTime("1h")
    .sign(key);
}

async function postBody(body: string | object): Promise<Response> {
  // Re-import handler fresh per call so mocked handler modules apply.
  const mod = await import("../index.js");
  const tok = await validJwt();
  const init: RequestInit = {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${tok}`,
    },
    body: typeof body === "string" ? body : JSON.stringify(body),
  };
  return await mod.default(new Request("http://test.local/llm", init));
}

const RID = "550e8400-e29b-41d4-a716-446655440000";

describe("router — malformed body → INTERNAL", () => {
  it("non-JSON body returns INTERNAL with static message", async () => {
    const res = await postBody("{not json");
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toMatchObject({
      type: "error",
      ok: false,
      error: { code: "INTERNAL", message: "request body failed validation" },
    });
  });

  it("missing required field returns INTERNAL", async () => {
    const res = await postBody({ type: "embed", requestId: RID });
    const body = await res.json();
    expect(body.error.code).toBe("INTERNAL");
    expect(body.error.message).toBe("request body failed validation");
  });

  it("unknown discriminator returns INTERNAL", async () => {
    const res = await postBody({ type: "bogus", requestId: RID, payload: {} });
    const body = await res.json();
    expect(body.error.code).toBe("INTERNAL");
  });

  it("non-UUIDv4 requestId returns INTERNAL", async () => {
    const res = await postBody({
      type: "embed",
      requestId: "not-a-uuid",
      payload: { text: "hi" },
    });
    const body = await res.json();
    expect(body.error.code).toBe("INTERNAL");
  });
});

describe("router — happy paths dispatch by type", () => {
  // Each valid body should reach the corresponding stub handler, which today
  // returns INTERNAL "not yet implemented". Dispatch correctness is verified
  // by spying on each handler module's `handle` export.

  it("embed → embed handler", async () => {
    const handler = await import("../handlers/embed.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({ type: "embed", requestId: RID, payload: { text: "x" } });
    expect(spy).toHaveBeenCalledOnce();
    expect(spy.mock.calls[0]![0]!.type).toBe("embed");
    spy.mockRestore();
  });

  it("summarize → summarize handler", async () => {
    const handler = await import("../handlers/summarize.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({
      type: "summarize",
      requestId: RID,
      payload: { text: "x", maxTokens: 100 },
    });
    expect(spy).toHaveBeenCalledOnce();
    spy.mockRestore();
  });

  it("extract_actions → extract_actions handler", async () => {
    const handler = await import("../handlers/extract_actions.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({
      type: "extract_actions",
      requestId: RID,
      payload: {
        text: "x",
        contentType: "text/plain",
        state: {
          foregroundApp: null,
          appCategory: null,
          activityState: null,
          hourLocal: null,
          dayOfWeek: null,
        },
        registeredFunctions: [],
        maxCandidates: 3,
      },
    });
    expect(spy).toHaveBeenCalledOnce();
    spy.mockRestore();
  });

  it("classify_intent → classify_intent handler", async () => {
    const handler = await import("../handlers/classify_intent.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({
      type: "classify_intent",
      requestId: RID,
      payload: { text: "x", appCategory: "messaging" },
    });
    expect(spy).toHaveBeenCalledOnce();
    spy.mockRestore();
  });

  it("generate_day_header → generate_day_header handler", async () => {
    const handler = await import("../handlers/generate_day_header.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({
      type: "generate_day_header",
      requestId: RID,
      payload: { dayIsoDate: "2026-04-29", envelopeSummaries: ["a"] },
    });
    expect(spy).toHaveBeenCalledOnce();
    spy.mockRestore();
  });

  it("scan_sensitivity → scan_sensitivity handler", async () => {
    const handler = await import("../handlers/scan_sensitivity.js");
    const spy = vi.spyOn(handler, "handle");
    await postBody({
      type: "scan_sensitivity",
      requestId: RID,
      payload: { text: "x" },
    });
    expect(spy).toHaveBeenCalledOnce();
    spy.mockRestore();
  });
});
