package com.capsule.app.audit

import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.model.AuditAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * JVM unit tests for [ActionsAuditAggregator] (T096).
 */
class ActionsAuditAggregatorTest {

    @Test
    fun groups_proposal_lifecycle_by_proposalId() {
        val pid = "p-1"
        val rows = listOf(
            row(at = 100, action = AuditAction.ACTION_PROPOSED,
                extras = """{"proposalId":"$pid","functionId":"calendar.create","previewTitle":"Lunch"}"""),
            row(at = 200, action = AuditAction.ACTION_CONFIRMED,
                extras = """{"proposalId":"$pid","functionId":"calendar.create"}"""),
            row(at = 300, action = AuditAction.ACTION_EXECUTED,
                extras = """{"proposalId":"$pid","functionId":"calendar.create","outcome":"DISPATCHED"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(1, s.proposalLifecycles.size)
        val lc = s.proposalLifecycles[0]
        assertEquals(pid, lc.proposalId)
        assertEquals("calendar.create", lc.functionId)
        assertEquals(100L, lc.firstSeenAt)
        assertEquals(AuditAction.ACTION_EXECUTED, lc.terminal)
    }

    @Test
    fun open_lifecycle_has_null_terminal() {
        val pid = "p-open"
        val rows = listOf(
            row(at = 50, action = AuditAction.ACTION_PROPOSED,
                extras = """{"proposalId":"$pid","functionId":"todo.create"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(1, s.proposalLifecycles.size)
        assertNull(s.proposalLifecycles[0].terminal)
    }

    @Test
    fun aggregates_dismissal_and_failure_reasons() {
        val rows = listOf(
            row(action = AuditAction.ACTION_DISMISSED,
                extras = """{"proposalId":"a","reason":"user_swipe"}"""),
            row(action = AuditAction.ACTION_DISMISSED,
                extras = """{"proposalId":"b","reason":"user_swipe"}"""),
            row(action = AuditAction.ACTION_DISMISSED,
                extras = """{"proposalId":"c","reason":"sensitivity_scope_mismatch"}"""),
            row(action = AuditAction.ACTION_FAILED,
                extras = """{"proposalId":"d","reason":"no_handler"}"""),
            row(action = AuditAction.ACTION_FAILED,
                extras = """{"proposalId":"e","reason":"schema_mismatch"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(2, s.dismissalReasons["user_swipe"])
        assertEquals(1, s.dismissalReasons["sensitivity_scope_mismatch"])
        assertEquals(1, s.failureReasons["no_handler"])
        assertEquals(1, s.failureReasons["schema_mismatch"])
    }

    @Test
    fun digest_runs_capture_envelope_count_and_reason() {
        val rows = listOf(
            row(at = 1_000, action = AuditAction.DIGEST_GENERATED,
                extras = """{"weekId":"2025-W44","envelopeCount":17}"""),
            row(at = 2_000, action = AuditAction.DIGEST_SKIPPED,
                extras = """{"reason":"too_sparse"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(2, s.digestRuns.size)
        val gen = s.digestRuns.first { it.action == AuditAction.DIGEST_GENERATED }
        assertEquals(17, gen.envelopeCount)
        val skip = s.digestRuns.first { it.action == AuditAction.DIGEST_SKIPPED }
        assertEquals("too_sparse", skip.reason)
    }

    @Test
    fun envelope_invalidated_counted() {
        val rows = listOf(
            row(action = AuditAction.ENVELOPE_INVALIDATED, extras = """{"reason":"lost_provenance"}"""),
            row(action = AuditAction.ENVELOPE_INVALIDATED, extras = """{"reason":"lost_provenance"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(2, s.invalidations)
    }

    @Test
    fun unrelated_actions_are_ignored() {
        val rows = listOf(
            row(action = AuditAction.ENVELOPE_CREATED, extras = null),
            row(action = AuditAction.NETWORK_FETCH, extras = null)
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertTrue(s.proposalLifecycles.isEmpty())
        assertTrue(s.dismissalReasons.isEmpty())
        assertTrue(s.failureReasons.isEmpty())
        assertTrue(s.digestRuns.isEmpty())
        assertEquals(0, s.invalidations)
    }

    @Test
    fun lifecycle_uses_earliest_at_as_firstSeenAt_even_if_unsorted_input() {
        val pid = "p-x"
        val rows = listOf(
            row(at = 300, action = AuditAction.ACTION_EXECUTED,
                extras = """{"proposalId":"$pid","functionId":"calendar.create"}"""),
            row(at = 100, action = AuditAction.ACTION_PROPOSED,
                extras = """{"proposalId":"$pid","functionId":"calendar.create"}""")
        )
        val s = ActionsAuditAggregator.aggregate(rows)
        assertEquals(1, s.proposalLifecycles.size)
        assertEquals(100L, s.proposalLifecycles[0].firstSeenAt)
        assertEquals(AuditAction.ACTION_EXECUTED, s.proposalLifecycles[0].terminal)
        assertNotNull(s.proposalLifecycles[0].functionId)
    }

    private fun row(
        at: Long = System.currentTimeMillis(),
        action: AuditAction,
        extras: String?
    ) = AuditLogEntryEntity(
        id = UUID.randomUUID().toString(),
        at = at,
        action = action,
        description = "test",
        envelopeId = null,
        extraJson = extras
    )
}
