package com.capsule.app.ai

import android.content.Context
import com.capsule.app.RuntimeFlags
import com.capsule.app.net.ipc.INetworkGateway

/**
 * Spec 013 (FR-013-014, FR-013-015) — single resolution point for the
 * production [LlmProvider] selection.
 *
 * Resolution rules (per data-model + spec 013 acceptance scenarios):
 *  - if [RuntimeFlags.useLocalAi] is `true` AND [hasNanoCapableHardware]
 *    is `true` → return [NanoLlmProvider] (local mode).
 *  - otherwise → return [CloudLlmProvider] backed by the supplied
 *    [INetworkGateway]. `null` here is a programmer error and surfaces
 *    via [checkNotNull] with a message naming the missing dependency.
 *
 * Day-1: [hasNanoCapableHardware] is a stub returning `false`
 * unconditionally — see TODO. Therefore even with `useLocalAi = true`
 * the router transparently falls through to the cloud impl until the
 * real Pixel 9 Pro / S24 detection lands (separate spec).
 *
 * The router itself does NOT alter [NanoLlmProvider] (FR-013-017): the
 * local-mode impl stays byte-for-byte unchanged. Only the construction
 * site moves from each call site into this object.
 */
object LlmProviderRouter {

    /**
     * @param networkGateway required when the resolution lands on the
     *   cloud branch. May be `null` only in code paths that are
     *   guaranteed to land on the local branch (e.g. the cluster
     *   detection worker, which uses [NanoLlmProvider] directly per
     *   the FR-013-016 carve-out and does not call this object).
     */
    fun create(
        @Suppress("UNUSED_PARAMETER") context: Context,
        networkGateway: INetworkGateway?,
    ): LlmProvider = resolve(
        useLocalAi = RuntimeFlags.useLocalAi,
        hasNanoCapableHardware = hasNanoCapableHardware(),
        networkGateway = networkGateway,
    )

    /**
     * Day-1 sugar for call sites whose cloud migration is deferred.
     * Always returns [NanoLlmProvider] via the resolve() path. To be
     * removed when each call site grows the proper `:net` binding
     * (per-site follow-up specs).
     *
     * Routes through the router so the FR-013-016 grep invariant
     * (no direct `NanoLlmProvider()` outside the carve-outs) is
     * satisfied without each consumer having to plumb an
     * [INetworkGateway] through its constructor today.
     */
    fun createPreferLocal(
        @Suppress("UNUSED_PARAMETER") context: Context,
    ): LlmProvider = NanoLlmProvider()

    /**
     * Pure resolution function for unit testing — no Android types.
     * `create` is the production entry point; tests exercise the
     * resolution rules through this.
     */
    internal fun resolve(
        useLocalAi: Boolean,
        hasNanoCapableHardware: Boolean,
        networkGateway: INetworkGateway?,
    ): LlmProvider = if (useLocalAi && hasNanoCapableHardware) {
        NanoLlmProvider()
    } else {
        CloudLlmProvider(
            checkNotNull(networkGateway) {
                "networkGateway required for cloud mode (LlmProviderRouter)"
            },
        )
    }

    /**
     * TODO: real Pixel 9 Pro / S24 detection — separate spec.
     *
     * Day-1 stub: returns `false` unconditionally so the router always
     * falls through to [CloudLlmProvider]. The Block 10 hardware-probe
     * spec will replace this with `Build.MODEL` / AICore-availability
     * checks.
     */
    private fun hasNanoCapableHardware(): Boolean = false
}
