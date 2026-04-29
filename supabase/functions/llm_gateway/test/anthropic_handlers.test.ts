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

describe("classify_intent handler — Haiku, prompt-cached", () => {
  it("first call (cache miss) → cacheHit=false; sends beta header + cache_control", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"intent":"REMINDER","confidence":0.92}' }],
      usage: { input_tokens: 50, output_tokens: 10, cache_read_input_tokens: 0 },
    });
    const { handle } = await import("../handlers/classify_intent.js");
    const result = await handle(
      {
        type: "classify_intent",
        requestId: RID,
        payload: { text: "remind me to call mom", appCategory: "messaging" },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "classify_intent_response",
      intent: "REMINDER",
      confidence: 0.92,
      modelLabel: "anthropic/claude-haiku-4-5",
    });
    expect(result.cacheHit).toBe(false);
    // Verify beta header + cached system block were sent to the SDK.
    const call = mockCreate.mock.calls[0]!;
    const body = call[0] as { model: string; system: Array<{ cache_control: unknown }> };
    const opts = call[1] as { headers: Record<string, string> };
    expect(opts.headers["anthropic-beta"]).toBe("prompt-caching-2024-07-31");
    expect(body.system[0]!.cache_control).toEqual({ type: "ephemeral" });
    expect(body.model).toBe("claude-haiku-4-5");
  });

  it("second call (cache hit) → cacheHit=true", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"intent":"NOTE","confidence":0.7}' }],
      usage: { input_tokens: 5, output_tokens: 8, cache_read_input_tokens: 480 },
    });
    const { handle } = await import("../handlers/classify_intent.js");
    const result = await handle(
      {
        type: "classify_intent",
        requestId: RID,
        payload: { text: "buy milk", appCategory: "notes" },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.cacheHit).toBe(true);
    expect(result.tokensIn).toBe(5 + 480);
  });

  it("schema-invalid (confidence > 1) → MALFORMED_RESPONSE", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"intent":"X","confidence":2.0}' }],
      usage: { input_tokens: 5, output_tokens: 5 },
    });
    const { handle } = await import("../handlers/classify_intent.js");
    const result = await handle(
      {
        type: "classify_intent",
        requestId: RID,
        payload: { text: "x", appCategory: "y" },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ code: "MALFORMED_RESPONSE" });
  });
});

describe("scan_sensitivity handler — Haiku, prompt-cached", () => {
  it("happy path with cache hit → cacheHit=true, tags array", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"tags":["PII","FINANCIAL"]}' }],
      usage: { input_tokens: 6, output_tokens: 6, cache_read_input_tokens: 320 },
    });
    const { handle } = await import("../handlers/scan_sensitivity.js");
    const result = await handle(
      { type: "scan_sensitivity", requestId: RID, payload: { text: "ssn 123-45-6789" } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "scan_sensitivity_response",
      tags: ["PII", "FINANCIAL"],
      modelLabel: "anthropic/claude-haiku-4-5",
    });
    expect(result.cacheHit).toBe(true);
    const call = mockCreate.mock.calls[0]!;
    const body = call[0] as { system: Array<{ cache_control: unknown }> };
    const opts = call[1] as { headers: Record<string, string> };
    expect(opts.headers["anthropic-beta"]).toBe("prompt-caching-2024-07-31");
    expect(body.system[0]!.cache_control).toEqual({ type: "ephemeral" });
  });

  it("non-array tags → MALFORMED_RESPONSE", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"tags":"PII"}' }],
      usage: { input_tokens: 5, output_tokens: 5 },
    });
    const { handle } = await import("../handlers/scan_sensitivity.js");
    const result = await handle(
      { type: "scan_sensitivity", requestId: RID, payload: { text: "x" } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ code: "MALFORMED_RESPONSE" });
  });

  it("upstream timeout → TIMEOUT", async () => {
    mockCreate.mockRejectedValueOnce(makeTimeoutError());
    const { handle } = await import("../handlers/scan_sensitivity.js");
    const result = await handle(
      { type: "scan_sensitivity", requestId: RID, payload: { text: "x" } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({ code: "TIMEOUT" });
  });
});

// ─── Spec 014 hotfix — closed-set allowlist enforcement ────────────────

describe("classify_intent allowlist (hotfix)", () => {
  it("out-of-set intent collapses to OTHER", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"intent":"DELETE_ALL","confidence":0.99}' }],
      usage: { input_tokens: 5, output_tokens: 5 },
    });
    const { handle } = await import("../handlers/classify_intent.js");
    const result = await handle(
      {
        type: "classify_intent",
        requestId: RID,
        payload: { text: "x", appCategory: "y" },
      },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "classify_intent_response",
      intent: "OTHER",
      confidence: 0.99,
    });
  });
});

describe("scan_sensitivity allowlist (hotfix)", () => {
  it("drops unknown tags and dedupes; preserves valid ones", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [
        {
          type: "text",
          text: '{"tags":["PII","IGNORE_ALL","PII","FINANCIAL"]}',
        },
      ],
      usage: { input_tokens: 5, output_tokens: 5 },
    });
    const { handle } = await import("../handlers/scan_sensitivity.js");
    const result = await handle(
      { type: "scan_sensitivity", requestId: RID, payload: { text: "x" } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "scan_sensitivity_response",
      tags: ["PII", "FINANCIAL"],
    });
  });

  it("all-unknown tags default to [NONE]", async () => {
    mockCreate.mockResolvedValueOnce({
      content: [{ type: "text", text: '{"tags":["IGNORE_ALL","HOSTILE"]}' }],
      usage: { input_tokens: 5, output_tokens: 5 },
    });
    const { handle } = await import("../handlers/scan_sensitivity.js");
    const result = await handle(
      { type: "scan_sensitivity", requestId: RID, payload: { text: "x" } },
      { userId: "u", requestId: RID },
    );
    expect(result.response).toMatchObject({
      type: "scan_sensitivity_response",
      tags: ["NONE"],
    });
  });
});

describe("extract_actions allowlist + maxCandidates (hotfix)", () => {
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
      registeredFunctions: [
        { id: "fn.reminder", name: "Reminder", schema: {} },
        { id: "fn.note", name: "Note", schema: {} },
      ],
      maxCandidates: 2,
    },
  };

  it("filters out proposals with unregistered functionId", async () => {
    const proposals = [
      { functionId: "fn.delete_all", args: {}, confidence: 1.0, rationale: null },
      { functionId: "fn.reminder", args: { what: "milk" }, confidence: 0.9, rationale: null },
    ];
    mockCreate.mockResolvedValueOnce(anthropicSuccess(JSON.stringify(proposals)));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    expect(result.response).toMatchObject({ type: "extract_actions_response" });
    const r = result.response as { proposals: Array<{ functionId: string }> };
    expect(r.proposals).toHaveLength(1);
    expect(r.proposals[0]!.functionId).toBe("fn.reminder");
  });

  it("clamps proposal array to maxCandidates", async () => {
    const proposals = [
      { functionId: "fn.reminder", args: {}, confidence: 0.9, rationale: null },
      { functionId: "fn.note", args: {}, confidence: 0.8, rationale: null },
      { functionId: "fn.reminder", args: {}, confidence: 0.7, rationale: null },
    ];
    mockCreate.mockResolvedValueOnce(anthropicSuccess(JSON.stringify(proposals)));
    const { handle } = await import("../handlers/extract_actions.js");
    const result = await handle(baseReq, { userId: "u", requestId: RID });
    const r = result.response as { proposals: unknown[] };
    expect(r.proposals).toHaveLength(2); // maxCandidates=2
  });
});
