package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.entity.StateSnapshot

/**
 * Principle IX — LLM Sovereignty.
 *
 * All AI inference MUST go through this interface. Two production impls:
 *  - [NanoLlmProvider] — local-mode (on-device Gemini Nano via AICore).
 *    Local-mode impls run on the `:ml` process and **MUST NOT touch the
 *    network** (Principle II — Local by Default in local mode).
 *  - `CloudLlmProvider` — cloud-mode. Cloud impls do NOT call HTTP
 *    directly; they route every request through the `:net`-process
 *    `INetworkGateway` AIDL surface (Principle VI — single network
 *    egress). The on-device process holding `LlmProvider` therefore
 *    still does not open sockets; the network egress lives in `:net`.
 *
 * Selection between local and cloud impls is owned by `LlmProviderRouter`
 * (see spec 013). Future providers (Cloud Boost, BYOK) will implement
 * this same interface and follow the same routing rule.
 */
interface LlmProvider {

    suspend fun classifyIntent(text: String, appCategory: String): IntentClassification

    suspend fun summarize(text: String, maxTokens: Int): SummaryResult

    suspend fun scanSensitivity(text: String): SensitivityResult

    suspend fun generateDayHeader(dayIsoDate: String, envelopeSummaries: List<String>): DayHeaderResult

    /**
     * Spec 003 v1.1 — Orbit Actions extraction.
     *
     * Given the raw envelope text, its content metadata, and the set of
     * registered functions, return up to [maxCandidates] proposals the user
     * is likely to want to confirm. Implementations MUST:
     *  - never invent a `functionId` outside [registeredFunctions]
     *  - emit `argsJson` that validates against the function's schema
     *  - clamp candidates to those with self-reported confidence ≥ 0.0
     *    (caller applies the 0.55 product floor)
     *  - sort the returned list by confidence descending
     *  - leave the returned list empty when the text is purely informational
     *
     * Local-mode implementations run on the `:ml` process and MUST NOT
     * touch the network. Cloud-mode implementations route the request
     * through the `:net` process via `INetworkGateway` (no direct HTTP
     * from the calling process).
     *
     * See `specs/003-orbit-actions/contracts/action-extraction-contract.md`.
     */
    suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int = 3
    ): ActionExtractionResult

    /**
     * Spec 002 Phase 11 / T123 — produce a sentence-embedding for the cluster
     * engine.
     *
     * Graceful-degrade contract — `embed` returns `null` and **never throws**:
     *  - blank/empty input → `null` (caller short-circuits),
     *  - AICore unavailable, v1 stub still `TODO`, call times out, or any
     *    [Throwable] escapes the impl → `null`,
     *  - **[com.capsule.app.ai.LlmProviderDiagnostics.forceNanoUnavailable]
     *    flag set → `null`** (intentional asymmetry vs [summarize] /
     *    [extractActions], which both throw `NanoUnavailableException` on
     *    that flag). Rationale: the cluster detection worker (Block 4)
     *    embeds N envelopes back-to-back per run; throwing per call would
     *    require try/catch on every iteration and risk fast-failing on the
     *    first envelope. The worker already pre-checks the flag as a
     *    top-level kill-switch, so honouring it here as a `null` return is
     *    safer and cheaper. **Do not "fix" this to throw** — the regression
     *    test in `NanoLlmProviderEmbedTest` will fail if you do.
     *
     * Local-mode implementations run on the `:ml` process and MUST NOT
     * touch the network (Principle II — Local by Default in local mode).
     * Cloud-mode implementations route the embedding call through the
     * `:net` process via `INetworkGateway` (Principle VI — single
     * network egress); the calling process still does not open sockets.
     * The returned [EmbeddingResult] stamps the producing model's
     * label + dimensionality so downstream cosine math can refuse
     * mismatched vectors (FR-038, FR-039).
     */
    suspend fun embed(text: String): EmbeddingResult?
}
