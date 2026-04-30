package com.capsule.app.action.handler

import android.content.Context
import com.capsule.app.action.ActionHandler
import com.capsule.app.action.HandlerResult
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import org.json.JSONException
import org.json.JSONObject

/**
 * Spec 002 Phase 11 Block 13 / T164 — `cluster.summarize` AppFunction
 * handler.
 *
 * Thin pass-through to the binder method
 * [IEnvelopeRepository.summarizeCluster]: this handler doesn't need any
 * `:capture`-side state (no Activity launch, no NotificationManager) so
 * it just parses `clusterId` from `argsJson`, calls the binder, and
 * maps the outcome string to [HandlerResult].
 *
 * Outcome-string contract (mirrors [com.capsule.app.data.WeeklyDigestDelegate]):
 *  - `"GENERATED:<envelopeId>"` → [HandlerResult.Success]
 *  - `"FAILED:<reason>"`        → [HandlerResult.Failed]
 *  - anything else              → [HandlerResult.Failed] (`reason="unknown_outcome"`)
 *
 * Latency is measured around the binder call so the executor service
 * sees the wall-clock cost of the cross-process trip + Nano inference.
 */
class ClusterSummarizeActionHandler : ActionHandler {
    override suspend fun handle(
        context: Context,
        skill: AppFunctionSummaryParcel,
        argsJson: String,
        repository: IEnvelopeRepository?
    ): HandlerResult {
        val started = System.currentTimeMillis()
        val clusterId = try {
            val args = JSONObject(argsJson)
            val raw = args.optString("clusterId", "")
            if (raw.isBlank()) {
                return HandlerResult.Failed(
                    latencyMs = System.currentTimeMillis() - started,
                    reason = "missing_cluster_id"
                )
            }
            raw
        } catch (e: JSONException) {
            return HandlerResult.Failed(
                latencyMs = System.currentTimeMillis() - started,
                reason = "invalid_args_json",
                cause = e
            )
        }

        val repo = repository ?: return HandlerResult.Failed(
            latencyMs = System.currentTimeMillis() - started,
            reason = "repository_unavailable"
        )

        val outcome = try {
            repo.summarizeCluster(clusterId)
        } catch (t: Throwable) {
            return HandlerResult.Failed(
                latencyMs = System.currentTimeMillis() - started,
                reason = "binder_threw",
                cause = t
            )
        }

        val latency = System.currentTimeMillis() - started
        return when {
            outcome.startsWith(GENERATED_PREFIX) -> HandlerResult.Success(
                latencyMs = latency,
                info = "local:${outcome.removePrefix(GENERATED_PREFIX)}"
            )
            outcome.startsWith(FAILED_PREFIX) -> HandlerResult.Failed(
                latencyMs = latency,
                reason = outcome.removePrefix(FAILED_PREFIX)
            )
            else -> HandlerResult.Failed(
                latencyMs = latency,
                reason = "unknown_outcome"
            )
        }
    }

    private companion object {
        const val GENERATED_PREFIX = "GENERATED:"
        const val FAILED_PREFIX = "FAILED:"
    }
}
