// Spec 014 — Zod schemas mirroring TS request types in ../types.ts.
// Used by index.ts to validate inbound JSON bodies. A failed parse must NOT
// echo the issue array to the client (gateway-request-response.md §2);
// index.ts collapses any failure to a generic INTERNAL error response.

import { z } from "zod";

// UUIDv4 per data-model.md §1.1 ("requestId").
const UUID_V4_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const RequestId = z.string().regex(UUID_V4_REGEX, "requestId must be UUIDv4");

// --- Supporting JSON-mirror schemas (data-model.md §1.2) ---

export const StateSnapshotSchema = z.object({
  foregroundApp: z.string().nullable(),
  appCategory: z.string().nullable(),
  activityState: z.string().nullable(),
  hourLocal: z.number().nullable(),
  dayOfWeek: z.string().nullable(),
});

export const AppFunctionSummarySchema = z.object({
  id: z.string().max(256),
  name: z.string().max(256),
  schema: z.record(z.unknown()),
});

export const ActionProposalSchema = z.object({
  functionId: z.string(),
  args: z.record(z.unknown()),
  confidence: z.number().min(0).max(1),
  rationale: z.string().nullable(),
});

// --- Per-payload schemas ---

// Text size caps (bytes ~ chars for JSON). Defense-in-depth: prevent a
// compromised or buggy caller from forcing huge upstream LLM bills, and
// make the request shape explicit. Embed/summarize allow longer inputs;
// classify/scan/extract are short-text paths.
const TEXT_MAX_LONG = 32_000;
const TEXT_MAX_SHORT = 8_000;
const TEXT_MAX_EXTRACT = 64_000;
const SUMMARIZE_MAX_TOKENS = 512;
const MAX_REGISTERED_FUNCTIONS = 64;
const MAX_ENVELOPE_SUMMARIES = 200;
const ENVELOPE_SUMMARY_MAX = 4_000;

export const EmbedPayloadSchema = z.object({
  text: z.string().max(TEXT_MAX_LONG),
});

export const SummarizePayloadSchema = z.object({
  text: z.string().max(TEXT_MAX_LONG),
  maxTokens: z.number().int().positive().max(SUMMARIZE_MAX_TOKENS),
});

export const ExtractActionsPayloadSchema = z.object({
  text: z.string().max(TEXT_MAX_EXTRACT),
  contentType: z.string().max(256),
  state: StateSnapshotSchema,
  registeredFunctions: z.array(AppFunctionSummarySchema).max(MAX_REGISTERED_FUNCTIONS),
  maxCandidates: z.number().int().min(1),
});

export const ClassifyIntentPayloadSchema = z.object({
  text: z.string().max(TEXT_MAX_SHORT),
  appCategory: z.string().max(256),
});

export const GenerateDayHeaderPayloadSchema = z.object({
  dayIsoDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "dayIsoDate must be YYYY-MM-DD"),
  envelopeSummaries: z.array(z.string().max(ENVELOPE_SUMMARY_MAX)).max(MAX_ENVELOPE_SUMMARIES),
});

export const ScanSensitivityPayloadSchema = z.object({
  text: z.string().max(TEXT_MAX_SHORT),
});

// --- Root request schema (discriminated union) ---

export const LlmGatewayRequestSchema = z.discriminatedUnion("type", [
  z.object({
    type: z.literal("embed"),
    requestId: RequestId,
    payload: EmbedPayloadSchema,
  }),
  z.object({
    type: z.literal("summarize"),
    requestId: RequestId,
    payload: SummarizePayloadSchema,
  }),
  z.object({
    type: z.literal("extract_actions"),
    requestId: RequestId,
    payload: ExtractActionsPayloadSchema,
  }),
  z.object({
    type: z.literal("classify_intent"),
    requestId: RequestId,
    payload: ClassifyIntentPayloadSchema,
  }),
  z.object({
    type: z.literal("generate_day_header"),
    requestId: RequestId,
    payload: GenerateDayHeaderPayloadSchema,
  }),
  z.object({
    type: z.literal("scan_sensitivity"),
    requestId: RequestId,
    payload: ScanSensitivityPayloadSchema,
  }),
]);

// --- Upstream response validation schemas (used by handlers) ---

/** ActionProposal[] returned by the extract_actions Anthropic handler. */
export const ActionProposalArraySchema = z.array(ActionProposalSchema);
