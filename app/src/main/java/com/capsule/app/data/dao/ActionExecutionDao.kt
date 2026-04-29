package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.ActionExecutionEntity
import com.capsule.app.data.model.ActionExecutionOutcome
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionExecutionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(execution: ActionExecutionEntity)

    @Query("SELECT * FROM action_execution WHERE id = :id")
    suspend fun getById(id: String): ActionExecutionEntity?

    @Query("SELECT * FROM action_execution WHERE proposalId = :proposalId ORDER BY dispatchedAt DESC")
    fun observeByProposal(proposalId: String): Flow<List<ActionExecutionEntity>>

    /**
     * Records the terminal outcome of a previously-DISPATCHED execution.
     * Returns the number of rows affected so callers can detect a no-op
     * when the row has already been resolved (e.g., undo-window cleanup
     * worker firing after an external resolution).
     */
    @Query(
        """
        UPDATE action_execution
        SET outcome = :outcome,
            outcomeReason = :reason,
            completedAt = :completedAt,
            latencyMs = :latencyMs
        WHERE id = :id AND outcome IN ('PENDING','DISPATCHED')
        """
    )
    suspend fun markOutcome(
        id: String,
        outcome: ActionExecutionOutcome,
        reason: String?,
        completedAt: Long,
        latencyMs: Long
    ): Int

    @Query("SELECT * FROM action_execution WHERE outcome = 'DISPATCHED' AND dispatchedAt > :sinceMillis")
    suspend fun listDispatchedSince(sinceMillis: Long): List<ActionExecutionEntity>

    @Query("SELECT COUNT(*) FROM action_execution WHERE proposalId = :proposalId")
    suspend fun countForProposal(proposalId: String): Int
}
