// Spec 014 — TypeScript types for the gateway request/response envelope.
// These mirror the Kotlin sealed classes from spec 013 data-model §1.1/§1.2.
// Wire serialization wraps these flat shapes into the HTTP envelope
// (`{type, requestId, ok, data | error}`) per envelope-contract §3.

import type { ErrorCode } from "./lib/errors.js";

// --- Supporting JSON-mirror types (data-model.md §1.2) ---

/** Mirrors com.capsule.app.data.entity.StateSnapshot. */
export interface StateSnapshotJson {
  foregroundApp: string | null;
  appCategory: string | null;
  activityState: string | null;
  hourLocal: number | null;
  dayOfWeek: string | null;
}

/** Mirrors com.capsule.app.ai.model.AppFunctionSummary. */
export interface AppFunctionSummaryJson {
  id: string;
  name: string;
  schema: Record<string, unknown>;
}

/** Mirrors com.capsule.app.actions.ActionProposal. */
export interface ActionProposalJson {
  functionId: string;
  args: Record<string, unknown>;
  confidence: number;
  rationale: string | null;
}

// --- Request discriminated union (data-model.md §1.1) ---

export interface EmbedRequest {
  type: "embed";
  requestId: string;
  payload: { text: string };
}

export interface SummarizeRequest {
  type: "summarize";
  requestId: string;
  payload: { text: string; maxTokens: number };
}

export interface ExtractActionsRequest {
  type: "extract_actions";
  requestId: string;
  payload: {
    text: string;
    contentType: string;
    state: StateSnapshotJson;
    registeredFunctions: AppFunctionSummaryJson[];
    maxCandidates: number;
  };
}

export interface ClassifyIntentRequest {
  type: "classify_intent";
  requestId: string;
  payload: { text: string; appCategory: string };
}

export interface GenerateDayHeaderRequest {
  type: "generate_day_header";
  requestId: string;
  payload: { dayIsoDate: string; envelopeSummaries: string[] };
}

export interface ScanSensitivityRequest {
  type: "scan_sensitivity";
  requestId: string;
  payload: { text: string };
}

export type LlmGatewayRequest =
  | EmbedRequest
  | SummarizeRequest
  | ExtractActionsRequest
  | ClassifyIntentRequest
  | GenerateDayHeaderRequest
  | ScanSensitivityRequest;

export type LlmGatewayRequestType = LlmGatewayRequest["type"];

// --- Response discriminated union (data-model.md §2.1) ---

export interface EmbedResponse {
  type: "embed_response";
  requestId: string;
  vector: number[];
  modelLabel: string;
}

export interface SummarizeResponse {
  type: "summarize_response";
  requestId: string;
  summary: string;
  modelLabel: string;
}

export interface ExtractActionsResponse {
  type: "extract_actions_response";
  requestId: string;
  proposals: ActionProposalJson[];
  modelLabel: string;
}

export interface ClassifyIntentResponse {
  type: "classify_intent_response";
  requestId: string;
  intent: string;
  confidence: number;
  modelLabel: string;
}

export interface GenerateDayHeaderResponse {
  type: "generate_day_header_response";
  requestId: string;
  header: string;
  modelLabel: string;
}

export interface ScanSensitivityResponse {
  type: "scan_sensitivity_response";
  requestId: string;
  tags: string[];
  modelLabel: string;
}

export interface ErrorResponse {
  type: "error";
  requestId: string;
  code: ErrorCode;
  message: string;
}

export type LlmGatewaySuccessResponse =
  | EmbedResponse
  | SummarizeResponse
  | ExtractActionsResponse
  | ClassifyIntentResponse
  | GenerateDayHeaderResponse
  | ScanSensitivityResponse;

export type LlmGatewayResponse = LlmGatewaySuccessResponse | ErrorResponse;

/**
 * Per-handler context shared between handlers and audit wiring.
 * Populated incrementally as the request flows through the function.
 */
export interface HandlerContext {
  userId: string;
  requestId: string;
}

/**
 * Handler return shape. Carries the wire response plus audit-only fields
 * (`model`, `tokensIn`, `tokensOut`, `cacheHit`) that index.ts consumes
 * to build `audit_log_entries.details_json`. The audit-only fields are
 * NEVER serialized to the wire.
 */
export interface HandlerResult {
  response: LlmGatewayResponse;
  model: string;
  modelLabel: string;
  tokensIn: number;
  tokensOut: number;
  cacheHit: boolean;
}
