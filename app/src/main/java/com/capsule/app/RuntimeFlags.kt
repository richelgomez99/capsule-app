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
}
