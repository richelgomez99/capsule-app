package com.capsule.app

import com.capsule.app.ai.NanoLlmProvider

/**
 * Phase 11 Block 4 stub for the runtime-flag surface that Block 10
 * (T156) will flesh out. The cluster engine reads
 * [clusterModelLabelLock] inside [ClusterDetector.detect] (FR-030) to
 * decide whether the embeddings-on-disk are still consistent with the
 * Nano build that wrote them; if they aren't, the worker silently
 * skips the run rather than mixing label-versions inside one cluster.
 *
 * Block 10 will replace this object with a `SharedPreferences`-backed
 * source of truth + a `:debug`-only mutator surface so the gate can
 * be exercised on physical devices. Until then the lock pins to the
 * current Nano build constant — i.e. the gate is a no-op on day one
 * but the call sites are wired and tested so flipping the lock in
 * Block 10 doesn't require touching the worker.
 *
 * The object is mutable for tests only — production code MUST treat
 * [clusterModelLabelLock] as immutable. The `@Volatile` annotation
 * mirrors the [com.capsule.app.ai.LlmProviderDiagnostics] convention
 * so behaviour under multi-process readers is well-defined.
 */
object RuntimeFlags {

    /**
     * The Nano build id the cluster engine is locked to. Embeddings
     * written under any other build are treated as drift and the
     * detection worker silently no-ops the run. Today this is the
     * current Nano build label so production code never trips the
     * gate; on Pixel 9 Pro firmware updates Block 10's debug surface
     * will let the lock outlive the build.
     */
    @Volatile
    @JvmStatic
    var clusterModelLabelLock: String = NanoLlmProvider.MODEL_LABEL
        internal set

    /**
     * Spec 013 (FR-013-002) — selects the on-device LLM provider.
     *  - `false` (default) → [com.capsule.app.ai.LlmProviderRouter] returns
     *    `CloudLlmProvider` (cloud is the Day-1 default per D-001).
     *  - `true` → router returns `NanoLlmProvider` *if and only if*
     *    `hasNanoCapableHardware()` also returns true; otherwise it
     *    transparently falls through to `CloudLlmProvider`.
     *
     * SharedPreferences key (Block 10 surface): `"cloud.use_local_ai"`.
     * The hot path is the in-memory `@Volatile` field; `SharedPreferences`
     * is the boot-time / settings-write path the same way
     * [clusterModelLabelLock] is intended to be wired.
     */
    @Volatile
    @JvmStatic
    var useLocalAi: Boolean = false
        internal set

    /**
     * Spec 013 (FR-013-002) — runtime kill switch for cluster card
     * surfacing read by `ClusterDetectionWorker` + `ClusterRepository`.
     *
     * **Default `false`** (Block 10 review FU#2). Until Block 6 lands
     * the `ClusterSummariser` and the cluster-card interactivity
     * handlers are wired beyond no-ops, surfacing a SURFACED cluster
     * to a real user produces a dead-end (tap Summarize → nothing
     * happens). The safe production default is therefore *off* — the
     * detection worker still computes locally for telemetry, but no
     * UI emission reaches the Diary slot until either:
     *  1. the user explicitly opts in (Settings — future surface), or
     *  2. a debug build flips [devClusterForceEmit] for stage demos.
     *
     * SharedPreferences key (Block 10 surface): `"cluster.emit_enabled"`.
     */
    @Volatile
    @JvmStatic
    var clusterEmitEnabled: Boolean = false
        internal set

    /**
     * Phase 11 Block 10 (T156/T157) — debug-only stage-demo override.
     * When `true` AND the build is `:debug`, the diagnostics surface
     * has written a hand-curated synthetic cluster row that should be
     * surfaced even if [clusterEmitEnabled] would otherwise suppress
     * it. Production builds never see this flag flipped.
     *
     * SharedPreferences key (Block 10 surface): `"cluster.dev_force_emit"`.
     * Mutator surface lives in `:debug` source set per the same model
     * as 003 T097: flag *checks* live in main code (single volatile
     * read); *writes* are debug-only.
     */
    @Volatile
    @JvmStatic
    var devClusterForceEmit: Boolean = false
        internal set
}
