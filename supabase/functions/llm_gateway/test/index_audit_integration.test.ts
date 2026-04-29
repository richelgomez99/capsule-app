// T014-014 — index.ts audit + operator-log integration tests (Phase F).
// Verifies: 1 audit row per authenticated request, row shape matches the
// closed contract, operator log line shape matches §6, audit insert
// failure does not change user-facing response, and Bounded Observation:
// no request body content reaches operator log or audit details_json.

import { describe, it, expect, vi, beforeEach, beforeAll, afterAll } from "vitest";
import { SignJWT } from "jose";

// Mock all SDKs so handlers exercise their happy paths without network.
const mockAnthropicCreate = vi.fn();
vi.mock("@anthropic-ai/sdk", () => ({
  default: class {
    messages = { create: mockAnthropicCreate };
    constructor(_o: unknown) {}
  },
}));

const mockOpenAIEmbeddingsCreate = vi.fn();
vi.mock("openai", () => ({
  default: class {
    embeddings = { create: mockOpenAIEmbeddingsCreate };
    constructor(_o: unknown) {}
  },
}));

import { _resetClientForTest as _resetAnthropic } from "../lib/anthropic.js";
import { _resetClientForTest as _resetOpenAI } from "../lib/openai.js";
import { _setClientForTest as _setAuditClient } from "../lib/audit.js";

const TEST_SECRET = "test-jwt-secret-must-be-long-enough-32+";
const TEST_SUPABASE_URL = "https://test-project.supabase.co";
const VALID_SUB = "11111111-1111-4111-8111-111111111111";
const RID = "550e8400-e29b-41d4-a716-446655440099";
// Bounded-observation canary — must NEVER appear in operator log or audit row.
const PROMPT_CANARY = "CANARY-9b3c2-DO-NOT-LEAK";

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

interface CapturedAudit {
  table?: string;
  row?: Record<string, unknown>;
  rows: Array<Record<string, unknown>>;
}

function makeAuditClient(failInsert = false) {
  const captured: CapturedAudit = { rows: [] };
  const insert = vi.fn(async (row: Record<string, unknown>) => {
    captured.rows.push(row);
    captured.row = row;
    return failInsert ? { error: { message: "rls denied" } } : { error: null };
  });
  const fakeClient = {
    from(table: string) {
      captured.table = table;
      return { insert };
    },
  };
  return { fakeClient: fakeClient as unknown as Parameters<typeof _setAuditClient>[0], captured };
}

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

async function postBody(body: object): Promise<Response> {
  const mod = await import("../index.js");
  const tok = await validJwt();
  return await mod.default(
    new Request("http://test.local/llm", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tok}`,
      },
      body: JSON.stringify(body),
    }),
  );
}

beforeEach(() => {
  mockAnthropicCreate.mockReset();
  mockOpenAIEmbeddingsCreate.mockReset();
  _resetAnthropic();
  _resetOpenAI();
  _setAuditClient(null);
});

describe("index — audit row + operator log integration", () => {
  it("success: emits exactly one audit row with closed details_json keys", async () => {
    mockAnthropicCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: "a one-line summary" }],
      usage: { input_tokens: 10, output_tokens: 5 },
    });
    const { fakeClient, captured } = makeAuditClient();
    _setAuditClient(fakeClient);

    const logSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    const res = await postBody({
      type: "summarize",
      requestId: RID,
      payload: { text: PROMPT_CANARY, maxTokens: 80 },
    });

    expect(res.status).toBe(200);
    // Allow one tick for fire-and-forget audit insert.
    await new Promise((r) => setImmediate(r));
    expect(captured.rows.length).toBe(1);
    expect(captured.table).toBe("audit_log_entries");
    const row = captured.rows[0]!;
    expect(row.user_id).toBe(VALID_SUB);
    expect(row.event_type).toBe("cloud_llm_call");
    expect(row.actor).toBe("edge_function");
    const details = row.details_json as Record<string, unknown>;
    expect(Object.keys(details).sort()).toEqual(
      [
        "cacheHit",
        "latencyMs",
        "model",
        "modelLabel",
        "requestId",
        "requestType",
        "success",
        "tokensIn",
        "tokensOut",
      ].sort(),
    );
    expect(details.success).toBe(true);
    expect(details.requestType).toBe("summarize");
    expect(details.requestId).toBe(RID);

    // Bounded Observation: prompt canary nowhere in audit row or any logs.
    expect(JSON.stringify(row)).not.toContain(PROMPT_CANARY);
    for (const call of logSpy.mock.calls) {
      expect(JSON.stringify(call)).not.toContain(PROMPT_CANARY);
    }

    // Operator log: closed shape, includes userId, no errorCode on success.
    const logCalls = logSpy.mock.calls;
    expect(logCalls.length).toBeGreaterThanOrEqual(1);
    const logLine = JSON.parse(logCalls[0]![0] as string) as Record<string, unknown>;
    expect(Object.keys(logLine).sort()).toEqual(
      [
        "cacheHit",
        "latencyMs",
        "model",
        "modelLabel",
        "requestId",
        "requestType",
        "success",
        "tokensIn",
        "tokensOut",
        "userId",
      ].sort(),
    );
    expect(logLine.userId).toBe(VALID_SUB);
    expect(logLine.success).toBe(true);

    logSpy.mockRestore();
  });

  it("error: emits audit row with errorCode + tokens forced to 0", async () => {
    const e = new Error("HTTP 503") as Error & { status: number };
    e.status = 503;
    mockAnthropicCreate.mockRejectedValueOnce(e);
    const { fakeClient, captured } = makeAuditClient();
    _setAuditClient(fakeClient);

    const logSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    const res = await postBody({
      type: "summarize",
      requestId: RID,
      payload: { text: "ok", maxTokens: 50 },
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.error.code).toBe("GATEWAY_5XX");

    await new Promise((r) => setImmediate(r));
    expect(captured.rows.length).toBe(1);
    const details = captured.rows[0]!.details_json as Record<string, unknown>;
    expect(details.success).toBe(false);
    expect(details.errorCode).toBe("GATEWAY_5XX");
    expect(details.tokensIn).toBe(0);
    expect(details.tokensOut).toBe(0);
    expect(details.cacheHit).toBe(false);

    // Operator log includes errorCode on failure.
    const logLine = JSON.parse(logSpy.mock.calls[0]![0] as string) as Record<string, unknown>;
    expect(logLine.errorCode).toBe("GATEWAY_5XX");
    expect(logLine.success).toBe(false);
    logSpy.mockRestore();
  });

  it("audit insert failure does NOT degrade response", async () => {
    mockAnthropicCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: "summary" }],
      usage: { input_tokens: 8, output_tokens: 4 },
    });
    const { fakeClient } = makeAuditClient(/*failInsert*/ true);
    _setAuditClient(fakeClient);
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => {});
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const res = await postBody({
      type: "summarize",
      requestId: RID,
      payload: { text: "ok", maxTokens: 50 },
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.ok).toBe(true);
    expect(body.type).toBe("summarize_response");

    await new Promise((r) => setImmediate(r));
    // Bounded audit-failure log line: { level, requestId, audit_insert_failed }.
    expect(errSpy).toHaveBeenCalled();
    const failLine = JSON.parse(errSpy.mock.calls[0]![0] as string) as Record<string, unknown>;
    expect(Object.keys(failLine).sort()).toEqual(
      ["audit_insert_failed", "level", "requestId"].sort(),
    );
    logSpy.mockRestore();
    errSpy.mockRestore();
  });

  it("embed: vector content does not appear in operator log or audit row", async () => {
    const v = Array.from({ length: 1536 }, (_, i) => i * 1e-6);
    mockOpenAIEmbeddingsCreate.mockResolvedValueOnce({
      data: [{ embedding: v }],
      usage: { prompt_tokens: 4 },
    });
    const { fakeClient, captured } = makeAuditClient();
    _setAuditClient(fakeClient);
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => {});

    const res = await postBody({
      type: "embed",
      requestId: RID,
      payload: { text: PROMPT_CANARY },
    });

    expect(res.status).toBe(200);
    await new Promise((r) => setImmediate(r));
    expect(captured.rows.length).toBe(1);
    const row = captured.rows[0]!;
    expect(JSON.stringify(row)).not.toContain(PROMPT_CANARY);
    // No `vector` or `data` keys in details_json.
    const details = row.details_json as Record<string, unknown>;
    expect(details).not.toHaveProperty("vector");
    expect(details).not.toHaveProperty("data");
    // Operator log: same invariant.
    const logLine = JSON.parse(logSpy.mock.calls[0]![0] as string) as Record<string, unknown>;
    expect(logLine).not.toHaveProperty("vector");
    expect(JSON.stringify(logLine)).not.toContain(PROMPT_CANARY);
    logSpy.mockRestore();
  });
});
