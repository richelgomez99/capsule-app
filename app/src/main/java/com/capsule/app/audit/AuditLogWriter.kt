package com.capsule.app.audit

import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.model.AuditAction
import java.util.UUID

/**
 * Constructs [AuditLogEntryEntity] rows. The actual write is performed by
 * [com.capsule.app.data.EnvelopeStorageBackend] so the audit row lands inside
 * the same transaction as the mutation it is describing (audit-log-contract.md §6).
 *
 * v1 always populates LLM provenance columns as null (LocalNano-only). v1.1
 * will start populating them per constitution Principle IX condition 3.
 */
class AuditLogWriter(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> String = { UUID.randomUUID().toString() }
) {

    fun build(
        action: AuditAction,
        description: String,
        envelopeId: String? = null,
        extraJson: String? = null
    ): AuditLogEntryEntity = AuditLogEntryEntity(
        id = idGen(),
        at = clock(),
        action = action,
        description = description,
        envelopeId = envelopeId,
        extraJson = extraJson
    )
}
