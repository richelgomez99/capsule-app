package com.capsule.app.audit

import com.capsule.app.data.model.AuditAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [AuditCopyTemplates] (T095). Verifies the
 * action→user-facing-string contract documented in data-model.md §6.
 */
class AuditCopyTemplatesTest {

    @Test
    fun proposed_uses_previewTitle_when_present() {
        val out = AuditCopyTemplates.format(
            AuditAction.ACTION_PROPOSED,
            """{"previewTitle":"Add to calendar","functionId":"calendar.create"}""",
            "fallback"
        )
        assertEquals("Proposed: Add to calendar", out)
    }

    @Test
    fun proposed_falls_back_to_functionId_when_no_title() {
        val out = AuditCopyTemplates.format(
            AuditAction.ACTION_PROPOSED,
            """{"functionId":"todo.create"}""",
            "fallback"
        )
        assertEquals("Proposed: todo.create", out)
    }

    @Test
    fun failed_no_handler_renders_user_facing_copy() {
        val out = AuditCopyTemplates.format(
            AuditAction.ACTION_FAILED,
            """{"reason":"no_handler","functionId":"calendar.create"}""",
            "raw"
        )
        assertEquals("No app handles this", out)
    }

    @Test
    fun failed_schema_mismatch_renders_user_facing_copy() {
        val out = AuditCopyTemplates.format(
            AuditAction.ACTION_FAILED,
            """{"reason":"schema_mismatch"}""",
            "raw"
        )
        assertEquals("Action rejected — schema mismatch", out)
    }

    @Test
    fun digest_generated_includes_envelope_count() {
        val out = AuditCopyTemplates.format(
            AuditAction.DIGEST_GENERATED,
            """{"weekId":"2025-W44","envelopeCount":17}""",
            "fallback"
        )
        assertEquals("Generated this week's digest (17 captures)", out)
    }

    @Test
    fun digest_skipped_too_sparse_distinct_from_already_exists() {
        val sparse = AuditCopyTemplates.format(
            AuditAction.DIGEST_SKIPPED,
            """{"reason":"too_sparse"}""",
            "raw"
        )
        val dup = AuditCopyTemplates.format(
            AuditAction.DIGEST_SKIPPED,
            """{"reason":"already_exists"}""",
            "raw"
        )
        assertEquals("Skipped digest — not enough captures this week", sparse)
        assertEquals("Skipped digest — already generated", dup)
    }

    @Test
    fun envelope_invalidated_lost_provenance_renders_specific_copy() {
        val out = AuditCopyTemplates.format(
            AuditAction.ENVELOPE_INVALIDATED,
            """{"reason":"lost_provenance"}""",
            "raw"
        )
        assertEquals("Removed digest — all sources deleted", out)
    }

    @Test
    fun unmapped_action_returns_fallback() {
        val out = AuditCopyTemplates.format(
            AuditAction.ENVELOPE_CREATED,
            null,
            "Captured a note"
        )
        assertEquals("Captured a note", out)
    }

    @Test
    fun corrupt_extras_returns_fallback_without_throwing() {
        val out = AuditCopyTemplates.format(
            AuditAction.ACTION_PROPOSED,
            "{not valid json",
            "Proposed: something"
        )
        assertEquals("Proposed: something", out)
    }
}
