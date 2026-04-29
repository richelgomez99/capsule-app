package com.capsule.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.data.dao.SkillStats
import com.capsule.app.data.entity.SkillUsageEntity
import com.capsule.app.data.model.ActionExecutionOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * T078 / 003 US4 — verifies [com.capsule.app.data.dao.SkillUsageDao.aggregate]
 * matches the per-skill rollup defined in data-model.md §5.
 *
 * Test name historically said "JVM" but Room's SQL aggregate cannot be
 * unit-tested without an Android runtime, so the test runs on the
 * androidTest tier in-memory. Hand-computed expectations are written
 * inline so a failing assertion points straight at the offending math.
 *
 * Outcome semantics (matters for `successRate`):
 *  - `SUCCESS` and `DISPATCHED` both count toward success — the v1.1
 *    external-intent handlers (calendar, share) terminate at
 *    `DISPATCHED` since the host app owns the actual write.
 *  - `USER_CANCELLED` is its own bucket reflected in `cancelRate`.
 *  - `FAILED` and `UNDONE` count as invocations only — pure denominator
 *    contribution.
 */
@RunWith(AndroidJUnit4::class)
class SkillUsageAggregationTest {

    private lateinit var db: OrbitDatabase
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now = 1_700_000_000_000L
    private val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aggregate_returnsNull_whenSkillNeverInvoked() = runBlocking {
        val stats = db.skillUsageDao().aggregate("calendar.createEvent", now - thirtyDaysMs)
        assertNull(stats)
    }

    @Test
    fun aggregate_calendar_handCheckedRollup() = runBlocking {
        // 6 invocations within window: 3 DISPATCHED, 1 USER_CANCELLED,
        // 1 FAILED, 1 SUCCESS.
        // Latencies: 100, 200, 300, 50, 800, 250 → avg = (100+200+300+50+800+250)/6 = 283.333…
        // successRate = (3 DISPATCHED + 1 SUCCESS) / 6 = 4/6 = 0.6667
        // cancelRate  = 1 / 6 = 0.1667
        seed("calendar.createEvent", ActionExecutionOutcome.DISPATCHED, 100, now - 1_000)
        seed("calendar.createEvent", ActionExecutionOutcome.DISPATCHED, 200, now - 2_000)
        seed("calendar.createEvent", ActionExecutionOutcome.DISPATCHED, 300, now - 3_000)
        seed("calendar.createEvent", ActionExecutionOutcome.USER_CANCELLED, 50, now - 4_000)
        seed("calendar.createEvent", ActionExecutionOutcome.FAILED, 800, now - 5_000)
        seed("calendar.createEvent", ActionExecutionOutcome.SUCCESS, 250, now - 6_000)
        // Plus one *outside* the rolling 30d window — must be excluded.
        seed("calendar.createEvent", ActionExecutionOutcome.SUCCESS, 9_999, now - thirtyDaysMs - 1)

        val stats = db.skillUsageDao().aggregate("calendar.createEvent", now - thirtyDaysMs)
        assertNotNull(stats)
        stats!!
        assertStats(
            stats,
            expectedSuccessRate = 4.0 / 6.0,
            expectedCancelRate = 1.0 / 6.0,
            expectedAvgLatencyMs = (100 + 200 + 300 + 50 + 800 + 250) / 6.0,
            expectedInvocationCount = 6
        )
    }

    @Test
    fun aggregate_isolatesPerSkill() = runBlocking {
        // 4 calendar rows, 2 todo rows, 1 share row — aggregate per
        // skill must not bleed outcomes across keys.
        seed("calendar.createEvent", ActionExecutionOutcome.SUCCESS, 100, now - 1_000)
        seed("calendar.createEvent", ActionExecutionOutcome.SUCCESS, 200, now - 2_000)
        seed("calendar.createEvent", ActionExecutionOutcome.FAILED, 300, now - 3_000)
        seed("calendar.createEvent", ActionExecutionOutcome.USER_CANCELLED, 400, now - 4_000)
        seed("tasks.createTodo", ActionExecutionOutcome.SUCCESS, 75, now - 1_500)
        seed("tasks.createTodo", ActionExecutionOutcome.SUCCESS, 125, now - 2_500)
        seed("share.send", ActionExecutionOutcome.DISPATCHED, 50, now - 1_000)

        val cal = db.skillUsageDao().aggregate("calendar.createEvent", now - thirtyDaysMs)!!
        val todo = db.skillUsageDao().aggregate("tasks.createTodo", now - thirtyDaysMs)!!
        val share = db.skillUsageDao().aggregate("share.send", now - thirtyDaysMs)!!

        assertEquals(4, cal.invocationCount)
        assertEquals(2.0 / 4.0, cal.successRate, 1e-9)
        assertEquals(1.0 / 4.0, cal.cancelRate, 1e-9)
        assertEquals((100 + 200 + 300 + 400) / 4.0, cal.avgLatencyMs, 1e-9)

        assertEquals(2, todo.invocationCount)
        assertEquals(1.0, todo.successRate, 1e-9)
        assertEquals(0.0, todo.cancelRate, 1e-9)

        assertEquals(1, share.invocationCount)
        assertEquals(1.0, share.successRate, 1e-9)
    }

    private fun assertStats(
        actual: SkillStats,
        expectedSuccessRate: Double,
        expectedCancelRate: Double,
        expectedAvgLatencyMs: Double,
        expectedInvocationCount: Int
    ) {
        val eps = 1e-9
        assertEquals("successRate", expectedSuccessRate, actual.successRate, eps)
        assertEquals("cancelRate", expectedCancelRate, actual.cancelRate, eps)
        assertEquals("avgLatencyMs", expectedAvgLatencyMs, actual.avgLatencyMs, eps)
        assertEquals("invocationCount", expectedInvocationCount, actual.invocationCount)
    }

    private suspend fun seed(
        skillId: String,
        outcome: ActionExecutionOutcome,
        latencyMs: Long,
        invokedAt: Long
    ) {
        db.skillUsageDao().insert(
            SkillUsageEntity(
                id = UUID.randomUUID().toString(),
                skillId = skillId,
                executionId = UUID.randomUUID().toString(),
                proposalId = UUID.randomUUID().toString(),
                episodeId = null,
                outcome = outcome,
                latencyMs = latencyMs,
                invokedAt = invokedAt
            )
        )
    }
}
