// T014-009 — Anthropic Sonnet handler unit tests.
// Mocks the Anthropic SDK module so the handlers exercise the real
// `callAnthropic` mapping logic without making network calls.

import { describe, it, expect, vi, beforeEach } from "vitest";

// Hoisted stub: the mocked SDK class. Tests reassign `mockCreate` per case.
const mockCreate = vi.fn();

vi.mock("@anthropic-ai/sdk", () => {
  return {
    default: class MockAnthropic {
      messages = { create: mockCreate };
      constructor(_opts: unknown) {}
    },
  };
});

// Reset client memo between tests so env changes (none here) take effect.
import { _resetClientForTest } from "../lib/anthropic.js";

beforeEach(() => {
  mockCreate.mockReset();
  _resetClientForTest();
});

const RID = "550e8400-e29b-41d4-a716-446655440001";

function anthropicSuccess(text: string, usage?: Record<string, number>) {
  return {
    content: [{ type: "text", text }],
    usage: usage ?? { input_tokens: 12, output_tokens: 7 },
  };
}

function makeApiError(status: number) {
  const e = new Error(`HTTP ${status}`) as Error & { status: number };
  e.status = status;
  return e;
}

function makeTimeoutError() {
  const e = new Error("Connection timed out") as Error & { name: string };
  e.name = "APIConnectionTimeoutError";
  return e;
}

describe("summarize handler — Sonnet, 60s, no cache", () => {
  it("happy path → summarize_response with cacheHit=false", async () => {
    mockCreate.mockResolvedValueOnce(anthropicSuccess("a one line summary"));
    const { handle } = await import("../handlers/summarize.js");
    const result = await handle(
      {
        type: "summarize",
        requestId: RID,
        payload: { text: "long text", maxTokens: 80 },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "summarize_response",
      requestId: RID,
      summary: "a one line summary",
      modelLabel: "anthropic/claude-sonnet-4-6",
    });
    expect(result.cacheHit).toBe(false);
    expect(result.modelLabel).toBe("anthropic/claude-sonnet-4-6");
  });

  it("upstream 5xx → GATEWAY_5XX", async () => {
    mockCreate.mockRejectedValueOnce(makeApiError(503));
    const { handle } = await import("../handlers/summarize.js");
    const result = await handle(
      { type: "summarize", requestId: RID, payload: { text: "x", maxTokens: 10 } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ type: "error", code: "GATEWAY_5XX" });
    expect(result.cacheHit).toBe(false);
  });

  it("timeout → TIMEOUT", async () => {
    mockCreate.mockRejectedValueOnce(makeTimeoutError());
    const { handle } = await import("../handlers/summarize.js");
    const result = await handle(
      { type: "summarize", requestId: RID, payload: { text: "x", maxTokens: 10 } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ type: "error", code: "TIMEOUT" });
  });

  it("empty content blocks → MALFORMED_RESPONSE", async () => {
    mockCreate.mockResolvedValueOnce({ content: [], usage: {} });
    const { handle } = await import("../handlers/summarize.js");
    const result = await handle(
      { type: "summarize", requestId: RID, payload: { text: "x", maxTokens: 10 } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ type: "error", code: "MALFORMED_RESPONSE" });
  });
});

describe("extract_actions handler — Sonnet, 30s, JSON-validated", () => {
  const baseReq = {
    type: "extract_actions" as const,
    requestId: RID,
    payload: {
      text: "buy milk",
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
  };

  it("happy path → extract_actions_response", async () => {
    const proposals = [
      { functionId: "fn.reminder", args: { what: "milk" }, confidence: 0.9, rationale: null },
    ];
    mockCreate.mockResolvedValueOnce(anthropicSuccess(JSON.stringify(proposals)));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    expect(result.response).toMatchObject({
      type: "extract_actions_response",
      requestId: RID,
      modelLabel: "anthropic/claude-sonnet-4-6",
    });
    expect(result.cacheHit).toBe(false);
  });

  it("non-JSON upstream → MALFORMED_RESPONSE", async () => {
    mockCreate.mockResolvedValueOnce(anthropicSuccess("not json at all"));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    expect(result.response).toMatchObject({ type: "error", code: "MALFORMED_RESPONSE" });
  });

  it("schema-invalid (confidence > 1) → MALFORMED_RESPONSE", async () => {
    const bad = [
      { functionId: "fn.x", args: {}, confidence: 2.0, rationale: null },
    ];
    mockCreate.mockResolvedValueOnce(anthropicSuccess(JSON.stringify(bad)));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    expect(result.response).toMatchObject({ type: "error", code: "MALFORMED_RESPONSE" });
  });

  it("upstream 5xx → GATEWAY_5XX", async () => {
    mockCreate.mockRejectedValueOnce(makeApiError(502));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    expect(result.response).toMatchObject({ type: "error", code: "GATEWAY_5XX" });
  });
});

describe("generate_day_header handler — Sonnet, 30s", () => {
  it("happy path → generate_day_header_response", async () => {
    mockCreate.mockResolvedValueOnce(anthropicSuccess("A quiet Tuesday morning."));
    const { handle } = await import("../handlers/generate_day_header.js");
    const result = await handle(
      {
        type: "generate_day_header",
        requestId: RID,
        payload: { dayIsoDate: "2026-04-29", envelopeSummaries: ["a", "b"] },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "generate_day_header_response",
      requestId: RID,
      header: "A quiet Tuesday morning.",
      modelLabel: "anthropic/claude-sonnet-4-6",
    });
    expect(result.cacheHit).toBe(false);
  });

  it("timeout → TIMEOUT", async () => {
    mockCreate.mockRejectedValueOnce(makeTimeoutError());
    const { handle } = await import("../handlers/generate_day_header.js");
    const result = await handle(
      {
        type: "generate_day_header",
        requestId: RID,
        payload: { dayIsoDate: "2026-04-29", envelopeSummaries: [] },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ type: "error", code: "TIMEOUT" });
  });
});
