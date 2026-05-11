package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.capsule.app.data.entity.EnvelopeNoteEntity

@Dao
interface EnvelopeNoteDao {
    @Insert
    suspend fun insert(note: EnvelopeNoteEntity)

    @Query(
        """
        SELECT * FROM envelope_note
        WHERE envelopeId = :envelopeId
        ORDER BY COALESCE(updatedAt, createdAt) DESC
        LIMIT 1
        """
    )
    suspend fun latestForEnvelope(envelopeId: String): EnvelopeNoteEntity?

    @Query("SELECT COUNT(*) FROM envelope_note WHERE envelopeId = :envelopeId")
    suspend fun countForEnvelope(envelopeId: String): Int

    @Query(
        """
        UPDATE envelope_note
        SET text = :text, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateText(id: String, text: String, updatedAt: Long)
}
