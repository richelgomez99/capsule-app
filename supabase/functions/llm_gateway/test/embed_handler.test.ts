// T014-012 — embed handler unit tests (Phase E).
// Mocks the OpenAI SDK module so the handler exercises the dimension assertion
// and the OpenAICallError → ErrorCode mapping without network calls.

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockEmbeddingsCreate = vi.fn();

vi.mock("openai", () => {
  return {
    default: class MockOpenAI {
      embeddings = { create: mockEmbeddingsCreate };
      constructor(_opts: unknown) {}
    },
  };
});

import { _resetClientForTest } from "../lib/openai.js";

beforeEach(() => {
  mockEmbeddingsCreate.mockReset();
  _resetClientForTest();
});

const RID = "550e8400-e29b-41d4-a716-446655440010";
const CTX = { userId: "u", requestId: RID };

function makeReq(text = "hello world") {
  return {
    type: "embed" as const,
    requestId: RID,
    payload: { text },
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

function vec(n = 1536, fill = 0.01) {
  return Array.from({ length: n }, (_, i) => fill + i * 1e-6);
}

describe("embed handler — OpenAI text-embedding-3-small, 1536 dims", () => {
  it("happy path → embed_response with 1536-d vector and correct modelLabel", async () => {
    mockEmbeddingsCreate.mockResolvedValueOnce({
      data: [{ embedding: vec() }],
      usage: { prompt_tokens: 4 },
    });
    const { handle } = await import("../handlers/embed.js");
    const result = await handle(makeReq(), CTX);
    expect(result.response).toMatchObject({
      type: "embed_response",
      requestId: RID,
      modelLabel: "openai/text-embedding-3-small",
    });
    if (result.response.type !== "embed_response") throw new Error("unreachable");
    expect(result.response.vector.length).toBe(1536);
    expect(result.modelLabel).toBe("openai/text-embedding-3-small");
    expect(result.cacheHit).toBe(false);
    expect(result.tokensIn).toBe(4);
    expect(result.tokensOut).toBe(0);
  });

  it("wrong dimension → MALFORMED_RESPONSE", async () => {
    mockEmbeddingsCreate.mockResolvedValueOnce({
      data: [{ embedding: vec(768) }],
      usage: { prompt_tokens: 4 },
    });
    const { handle } = await import("../handlers/embed.js");
    const result = await handle(makeReq(), CTX);
    expect(result.response).toMatchObject({
      type: "error",
      code: "MALFORMED_RESPONSE",
    });
  });

  it("non-finite element → MALFORMED_RESPONSE", async () => {
    const v = vec();
    v[0] = Number.NaN;
    mockEmbeddingsCreate.mockResolvedValueOnce({
      data: [{ embedding: v }],
      usage: { prompt_tokens: 4 },
    });
    const { handle } = await import("../handlers/embed.js");
    const result = await handle(makeReq(), CTX);
    expect(result.response).toMatchObject({
      type: "error",
      code: "MALFORMED_RESPONSE",
    });
  });

  it("OpenAI 5xx → PROVIDER_5XX", async () => {
    mockEmbeddingsCreate.mockRejectedValueOnce(makeApiError(503));
    const { handle } = await import("../handlers/embed.js");
    const result = await handle(makeReq(), CTX);
    expect(result.response).toMatchObject({
      type: "error",
      code: "PROVIDER_5XX",
    });
  });

  it("timeout → TIMEOUT", async () => {
    mockEmbeddingsCreate.mockRejectedValueOnce(makeTimeoutError());
    const { handle } = await import("../handlers/embed.js");
    const result = await handle(makeReq(), CTX);
    expect(result.response).toMatchObject({
      type: "error",
      code: "TIMEOUT",
    });
  });
});
