package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.SkillUsageEntity

/**
 * Per-skill usage statistics for the Settings → Actions screen
 * (T079/T080) and forward-compat for the v1.2 agent's planner heuristics
 * (spec 008). Aggregations match `data-model.md` §5.
 */
@Dao
interface SkillUsageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SkillUsageEntity)

    @Query("SELECT COUNT(*) FROM skill_usage")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM skill_usage WHERE skillId = :skillId")
    suspend fun countForSkill(skillId: String): Int

    /**
     * Returns aggregated stats over the rolling window starting at
     * [sinceMillis]. Returns null when the skill has never been invoked
     * inside the window — callers render an "—" placeholder in that case.
     */
    @Query(
        """
        SELECT
            skillId AS skillId,
            CAST(SUM(CASE WHEN outcome IN ('SUCCESS','DISPATCHED') THEN 1 ELSE 0 END) AS REAL) / COUNT(*) AS successRate,
            CAST(SUM(CASE WHEN outcome = 'USER_CANCELLED' THEN 1 ELSE 0 END) AS REAL) / COUNT(*) AS cancelRate,
            AVG(latencyMs) AS avgLatencyMs,
            COUNT(*) AS invocationCount
        FROM skill_usage
        WHERE skillId = :skillId AND invokedAt >= :sinceMillis
        GROUP BY skillId
        """
    )
    suspend fun aggregate(skillId: String, sinceMillis: Long): SkillStats?
}

/**
 * Aggregated rollup of [SkillUsageEntity] rows, returned by [SkillUsageDao.aggregate].
 *
 * - [successRate] counts both `SUCCESS` (local-only handlers) and
 *   `DISPATCHED` (external-intent handlers, the v1.1 terminal state for
 *   calendar/share — see [com.capsule.app.data.model.ActionExecutionOutcome]).
 */
data class SkillStats(
    val skillId: String,
    val successRate: Double,
    val cancelRate: Double,
    val avgLatencyMs: Double,
    val invocationCount: Int
)
