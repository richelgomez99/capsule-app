// T014-013 — audit insert unit tests (Phase F).
// Verifies the audit row shape (success + error), bounded-observation invariants,
// and the audit-insert-failure log line shape.

import { describe, it, expect, vi, beforeEach } from "vitest";

import { recordAuditRow, _setClientForTest, type AuditInput } from "../lib/audit.js";

// Minimal fake Supabase client surface that captures inserts.
function makeFakeClient(opts?: { failInsert?: boolean; throwInsert?: boolean }) {
  const captured: { table?: string; row?: Record<string, unknown> } = {};
  const insert = vi.fn(async (row: Record<string, unknown>) => {
    if (opts?.throwInsert) throw new Error("connection reset");
    captured.row = row;
    return opts?.failInsert
      ? { error: { message: "rls denied" }, data: null }
      : { error: null, data: row };
  });
  const fakeClient = {
    from(table: string) {
      captured.table = table;
      return { insert };
    },
  };
  return { fakeClient: fakeClient as unknown as Parameters<typeof _setClientForTest>[0], captured, insert };
}

const SUCCESS_INPUT: AuditInput = {
  userId: "11111111-1111-4111-8111-111111111111",
  requestId: "550e8400-e29b-41d4-a716-446655440013",
  requestType: "classify_intent",
  model: "claude-haiku-4-5",
  modelLabel: "anthropic/claude-haiku-4-5",
  latencyMs: 312,
  tokensIn: 800,
  tokensOut: 24,
  cacheHit: true,
  success: true,
};

const ERROR_INPUT: AuditInput = {
  userId: "11111111-1111-4111-8111-111111111111",
  requestId: "550e8400-e29b-41d4-a716-446655440014",
  requestType: "summarize",
  model: "claude-sonnet-4-6",
  modelLabel: "anthropic/claude-sonnet-4-6",
  latencyMs: 1500,
  // Even if a handler accidentally surfaces non-zero counts on an error,
  // recordAuditRow must force them to 0 per contract §2.2.
  tokensIn: 999,
  tokensOut: 999,
  cacheHit: true,
  success: false,
  errorCode: "GATEWAY_5XX",
};

const SUCCESS_KEYS = [
  "requestId",
  "requestType",
  "model",
  "modelLabel",
  "latencyMs",
  "tokensIn",
  "tokensOut",
  "cacheHit",
  "success",
] as const;

const ERROR_KEYS = [...SUCCESS_KEYS, "errorCode"] as const;

beforeEach(() => {
  _setClientForTest(null);
});

describe("recordAuditRow — success row shape", () => {
  it("inserts into audit_log_entries with closed details_json keys", async () => {
    const { fakeClient, captured, insert } = makeFakeClient();
    _setClientForTest(fakeClient);

    await recordAuditRow(SUCCESS_INPUT);

    expect(captured.table).toBe("audit_log_entries");
    expect(insert).toHaveBeenCalledOnce();
    const row = captured.row as Record<string, unknown>;
    expect(row.user_id).toBe(SUCCESS_INPUT.userId);
    expect(row.event_type).toBe("cloud_llm_call");
    expect(row.actor).toBe("edge_function");
    expect(row.subject_id).toBeNull();
    const details = row.details_json as Record<string, unknown>;
    // Exact key set — no extras, no missing.
    expect(Object.keys(details).sort()).toEqual([...SUCCESS_KEYS].sort());
    expect(details.success).toBe(true);
    expect(details.cacheHit).toBe(true);
    expect(details.tokensIn).toBe(800);
    expect(details.tokensOut).toBe(24);
    // Bounded Observation: no forbidden keys present in details_json. The
    // permitted key set is exactly SUCCESS_KEYS (asserted above); the
    // assertions below act as belt-and-braces guards against future
    // accidental additions.
    expect(details).not.toHaveProperty("payload");
    expect(details).not.toHaveProperty("vector");
    expect(details).not.toHaveProperty("text");
    expect(details).not.toHaveProperty("summary");
  });
});

describe("recordAuditRow — error row shape", () => {
  it("includes errorCode and forces token counts to 0", async () => {
    const { fakeClient, captured, insert } = makeFakeClient();
    _setClientForTest(fakeClient);

    await recordAuditRow(ERROR_INPUT);

    expect(insert).toHaveBeenCalledOnce();
    const details = (captured.row as Record<string, unknown>).details_json as Record<
      string,
      unknown
    >;
    expect(Object.keys(details).sort()).toEqual([...ERROR_KEYS].sort());
    expect(details.success).toBe(false);
    expect(details.errorCode).toBe("GATEWAY_5XX");
    expect(details.tokensIn).toBe(0);
    expect(details.tokensOut).toBe(0);
    expect(details.cacheHit).toBe(false);
  });
});

describe("recordAuditRow — audit failure handling (FR-014-014)", () => {
  it("supabase returns error: does not throw, logs bounded shape", async () => {
    const { fakeClient } = makeFakeClient({ failInsert: true });
    _setClientForTest(fakeClient);
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    await expect(recordAuditRow(SUCCESS_INPUT)).resolves.toBeUndefined();

    expect(errSpy).toHaveBeenCalledOnce();
    const arg = errSpy.mock.calls[0]![0] as string;
    const parsed = JSON.parse(arg) as Record<string, unknown>;
    expect(Object.keys(parsed).sort()).toEqual(
      ["audit_insert_failed", "level", "requestId"].sort(),
    );
    expect(parsed.level).toBe("error");
    expect(parsed.audit_insert_failed).toBe(true);
    expect(parsed.requestId).toBe(SUCCESS_INPUT.requestId);
    errSpy.mockRestore();
  });

  it("insert throws synchronously: still resolves and logs bounded shape", async () => {
    const { fakeClient } = makeFakeClient({ throwInsert: true });
    _setClientForTest(fakeClient);
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    await expect(recordAuditRow(SUCCESS_INPUT)).resolves.toBeUndefined();

    expect(errSpy).toHaveBeenCalledOnce();
    const parsed = JSON.parse(errSpy.mock.calls[0]![0] as string);
    expect(parsed.audit_insert_failed).toBe(true);
    expect(parsed.requestId).toBe(SUCCESS_INPUT.requestId);
    errSpy.mockRestore();
  });
});
