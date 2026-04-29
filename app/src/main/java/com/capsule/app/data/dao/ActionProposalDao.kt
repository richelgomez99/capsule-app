package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.ActionProposalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionProposalDao {

    /**
     * Inserts a freshly extracted proposal. The `(envelopeId, functionId)`
     * unique index dedupes concurrent extractor runs; on conflict we
     * silently no-op (re-extraction idempotency, T094 + quickstart §6 N6).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(proposal: ActionProposalEntity): Long

    @Query("SELECT * FROM action_proposal WHERE id = :id")
    suspend fun getById(id: String): ActionProposalEntity?

    @Query(
        """
        SELECT * FROM action_proposal
        WHERE envelopeId = :envelopeId AND functionId = :functionId
        LIMIT 1
        """
    )
    suspend fun findByEnvelopeAndFunction(envelopeId: String, functionId: String): ActionProposalEntity?

    /** Live feed of non-terminal proposals for an envelope, ordered by extraction time. */
    @Query(
        """
        SELECT * FROM action_proposal
        WHERE envelopeId = :envelopeId AND state = 'PROPOSED'
        ORDER BY createdAt ASC
        """
    )
    fun observeProposedForEnvelope(envelopeId: String): Flow<List<ActionProposalEntity>>

    /** All proposals, any state, for the envelope. Used by audit + diagnostics. */
    @Query("SELECT * FROM action_proposal WHERE envelopeId = :envelopeId ORDER BY createdAt ASC")
    suspend fun listAllForEnvelope(envelopeId: String): List<ActionProposalEntity>

    @Query("UPDATE action_proposal SET state = 'DISMISSED', stateChangedAt = :at WHERE id = :id AND state = 'PROPOSED'")
    suspend fun markDismissed(id: String, at: Long): Int

    @Query("UPDATE action_proposal SET state = 'CONFIRMED', stateChangedAt = :at WHERE id = :id AND state = 'PROPOSED'")
    suspend fun markConfirmed(id: String, at: Long): Int

    @Query("UPDATE action_proposal SET state = 'INVALIDATED', stateChangedAt = :at WHERE id = :id")
    suspend fun markInvalidated(id: String, at: Long): Int

    @Query("SELECT COUNT(*) FROM action_proposal WHERE envelopeId = :envelopeId AND state = 'PROPOSED'")
    suspend fun countProposedForEnvelope(envelopeId: String): Int
}
