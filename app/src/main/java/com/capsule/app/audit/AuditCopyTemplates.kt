package com.capsule.app.audit

import com.capsule.app.data.model.AuditAction
import org.json.JSONObject

/**
 * T095 — user-facing copy templates for the v1.1 audit actions.
 *
 * The audit log persists a free-form `description` written at insertion
 * time (via [AuditLogWriter.build]) plus a structured `extraJson` blob.
 * Several surfaces (audit log viewer, "What Orbit did today", debug
 * tools) want a stable user-facing rendering keyed off
 * `(action, extraJson)` so we don't depend on the writer's diagnostic
 * description. This object is that mapping — pure, side-effect free,
 * deterministic across locales (English-only in v1.1; spec 010
 * finalises localisation).
 *
 * Returns `null` when no template is registered for an action — callers
 * should fall back to the raw `description` field. Returns the raw
 * fallback when `extraJson` is missing required fields rather than
 * throwing, so a corrupted row never crashes the audit screen.
 *
 * See data-model.md §6 for the canonical extraJson contract.
 */
object AuditCopyTemplates {

    /**
     * Render a user-facing one-line summary for the given audit row.
     *
     * @param action     the [AuditAction] enum from `audit_log.action`
     * @param extraJson  the raw `audit_log.extraJson` string (may be null)
     * @param fallback   the raw `audit_log.description`, used when no
     *                   template applies or required keys are missing
     */
    fun format(action: AuditAction, extraJson: String?, fallback: String): String {
        val extras: JSONObject? = parseOrNull(extraJson)
        return when (action) {
            AuditAction.ACTION_PROPOSED   -> formatProposed(extras, fallback)
            AuditAction.ACTION_DISMISSED  -> formatDismissed(extras, fallback)
            AuditAction.ACTION_CONFIRMED  -> formatConfirmed(extras, fallback)
            AuditAction.ACTION_EXECUTED   -> formatExecuted(extras, fallback)
            AuditAction.ACTION_FAILED     -> formatFailed(extras, fallback)
            AuditAction.APPFUNCTION_REGISTERED -> formatAppFunctionRegistered(extras, fallback)
            AuditAction.DIGEST_GENERATED  -> formatDigestGenerated(extras, fallback)
            AuditAction.DIGEST_SKIPPED    -> formatDigestSkipped(extras, fallback)
            AuditAction.ENVELOPE_INVALIDATED -> formatEnvelopeInvalidated(extras, fallback)
            AuditAction.CLUSTER_FORMED    -> formatClusterFormed(extras, fallback)
            AuditAction.CLUSTER_SURFACED  -> "Surfaced a cluster"
            AuditAction.CLUSTER_TAPPED    -> "You tapped a cluster card"
            AuditAction.CLUSTER_ACTING    -> "Summarising your cluster…"
            AuditAction.CLUSTER_ACTED     -> formatClusterActed(extras, fallback)
            AuditAction.CLUSTER_FAILED    -> formatClusterFailed(extras, fallback)
            AuditAction.CLUSTER_DISMISSED -> "You dismissed a cluster card"
            AuditAction.CLUSTER_AGED_OUT  -> "Cluster aged out"
            AuditAction.CLUSTER_ORPHANED  -> "Cluster auto-dismissed — too many sources removed"
            else -> fallback
        }
    }

    private fun formatProposed(extras: JSONObject?, fallback: String): String {
        val title = extras?.optString("previewTitle")?.takeIf { it.isNotBlank() }
        val functionId = extras?.optString("functionId")?.takeIf { it.isNotBlank() }
        return when {
            title != null      -> "Proposed: $title"
            functionId != null -> "Proposed: $functionId"
            else               -> fallback
        }
    }

    private fun formatDismissed(extras: JSONObject?, fallback: String): String {
        val reason = extras?.optString("reason")?.takeIf { it.isNotBlank() } ?: return "Dismissed proposal"
        return when (reason) {
            "sensitivity_scope_mismatch" -> "Hidden — sensitive content"
            "user_swipe"                 -> "Dismissed by you"
            else                         -> "Dismissed ($reason)"
        }
    }

    private fun formatConfirmed(extras: JSONObject?, fallback: String): String {
        val functionId = extras?.optString("functionId")?.takeIf { it.isNotBlank() }
        return if (functionId != null) "Confirmed: $functionId" else fallback
    }

    private fun formatExecuted(extras: JSONObject?, fallback: String): String {
        val outcome = extras?.optString("outcome")?.takeIf { it.isNotBlank() } ?: return fallback
        val functionId = extras.optString("functionId")?.takeIf { it.isNotBlank() } ?: return fallback
        return when (outcome) {
            "DISPATCHED" -> "Opened $functionId"
            "SUCCESS"    -> "Completed $functionId"
            else         -> "$functionId ($outcome)"
        }
    }

    private fun formatFailed(extras: JSONObject?, fallback: String): String {
        val reason = extras?.optString("reason")?.takeIf { it.isNotBlank() } ?: return "Action failed"
        return when (reason) {
            "no_handler"             -> "No app handles this"
            "schema_mismatch"        -> "Action rejected — schema mismatch"
            "schema_invalidated"    -> "Action rejected — registry updated"
            "user_cancelled"         -> "Undone within 5s"
            "ml_binder_unavailable"  -> "Action engine unavailable"
            "share_delegate_disabled_v1_1" -> "Sharing disabled in this build"
            "nano_unavailable"       -> "On-device AI unavailable"
            else                     -> "Action failed ($reason)"
        }
    }

    private fun formatAppFunctionRegistered(extras: JSONObject?, fallback: String): String {
        val fid = extras?.optString("functionId")?.takeIf { it.isNotBlank() } ?: return fallback
        return "Registered skill: $fid"
    }

    private fun formatDigestGenerated(extras: JSONObject?, fallback: String): String {
        val count = extras?.optInt("envelopeCount", -1) ?: -1
        return if (count >= 0) "Generated this week's digest ($count captures)"
               else "Generated this week's digest"
    }

    private fun formatDigestSkipped(extras: JSONObject?, fallback: String): String {
        val reason = extras?.optString("reason")?.takeIf { it.isNotBlank() } ?: return "Weekly digest skipped"
        return when (reason) {
            "too_sparse"     -> "Skipped digest — not enough captures this week"
            "already_exists" -> "Skipped digest — already generated"
            "nano_failed"    -> "Skipped digest — on-device AI failed"
            else             -> "Skipped digest ($reason)"
        }
    }

    private fun formatEnvelopeInvalidated(extras: JSONObject?, fallback: String): String {
        val reason = extras?.optString("reason")?.takeIf { it.isNotBlank() } ?: return fallback
        return when (reason) {
            "lost_provenance" -> "Removed digest — all sources deleted"
            else              -> "Invalidated ($reason)"
        }
    }

    private fun formatClusterFormed(extras: JSONObject?, fallback: String): String {
        val count = extras?.optInt("memberCount", -1) ?: -1
        val type = extras?.optString("clusterType")?.takeIf { it.isNotBlank() }
        return when {
            count > 0 && type == "RESEARCH_SESSION" -> "Noticed a research session ($count captures)"
            count > 0                               -> "Noticed a cluster ($count captures)"
            else                                    -> "Noticed a cluster"
        }
    }

    private fun formatClusterActed(extras: JSONObject?, fallback: String): String {
        val bullets = extras?.optInt("bulletCount", -1) ?: -1
        return if (bullets > 0) "Summarised your cluster ($bullets bullets)"
               else "Summarised your cluster"
    }

    private fun formatClusterFailed(extras: JSONObject?, fallback: String): String {
        val reason = extras?.optString("reason")?.takeIf { it.isNotBlank() } ?: return "Cluster summary failed"
        return when (reason) {
            "nano_unavailable"     -> "On-device AI unavailable"
            "nano_timeout"         -> "On-device AI timed out"
            "uncited_output"       -> "Rejected — agent didn't cite its sources"
            "prompt_injection"     -> "Rejected — suspicious source content"
            "model_label_mismatch" -> "Skipped — firmware drift detected"
            else                   -> "Cluster summary failed ($reason)"
        }
    }

    private fun parseOrNull(json: String?): JSONObject? {
        if (json.isNullOrBlank()) return null
        return runCatching { JSONObject(json) }.getOrNull()
    }
}
