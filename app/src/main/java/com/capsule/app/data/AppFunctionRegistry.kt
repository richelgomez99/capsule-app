package com.capsule.app.data

import androidx.room.withTransaction
import com.capsule.app.action.AppFunctionSchema
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.dao.AppFunctionSkillDao
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.SkillUsageDao
import com.capsule.app.data.entity.AppFunctionSkillEntity
import com.capsule.app.data.entity.SkillUsageEntity
import com.capsule.app.data.model.ActionExecutionOutcome
import com.capsule.app.data.model.AuditAction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Process-singleton registry of [AppFunctionSkillEntity] rows. Owned by the
 * `:ml` process — every other process reaches the registry through the
 * `IEnvelopeRepository` AIDL surface (T026/T027).
 *
 * Concurrency: a [Mutex] serialises all mutation paths so that two boot-time
 * `registerAll` calls (e.g., process restart races) cannot interleave their
 * audit-log writes. Read paths (`lookupLatest`, `listForApp`, `stats`) are
 * lock-free DAO queries.
 *
 * All mutation paths emit one [AuditAction.APPFUNCTION_REGISTERED] row per
 * affected skill so the audit log carries a forensics trail of which schema
 * version was active at any moment.
 */
class AppFunctionRegistry(
    private val database: OrbitDatabase,
    private val skillDao: AppFunctionSkillDao,
    private val usageDao: SkillUsageDao,
    private val auditLogDao: AuditLogDao,
    private val auditWriter: AuditLogWriter,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private val mutex = Mutex()

    /**
     * Seeds the registry with [schemas] at process start. Idempotent: a
     * schema that's already present at the same version is a no-op (no
     * audit row written); a bumped version triggers REPLACE + audit.
     */
    suspend fun registerAll(schemas: List<AppFunctionSchema>) {
        if (schemas.isEmpty()) return
        mutex.withLock {
            database.withTransaction {
                for (schema in schemas) {
                    val existing = skillDao.lookupLatest(schema.functionId)
                    if (existing != null && existing.schemaVersion == schema.schemaVersion) continue
                    val ts = now()
                    val entity = AppFunctionSkillEntity(
                        functionId = schema.functionId,
                        appPackage = schema.appPackage,
                        displayName = schema.displayName,
                        description = schema.description,
                        schemaVersion = schema.schemaVersion,
                        argsSchemaJson = schema.argsSchemaJson,
                        sideEffects = schema.sideEffects,
                        reversibility = schema.reversibility,
                        sensitivityScope = schema.sensitivityScope,
                        registeredAt = existing?.registeredAt ?: ts,
                        updatedAt = ts
                    )
                    skillDao.upsert(entity)
                    auditLogDao.insert(
                        auditWriter.build(
                            action = AuditAction.APPFUNCTION_REGISTERED,
                            description = "Registered ${schema.functionId} v${schema.schemaVersion}",
                            envelopeId = null,
                            extraJson = """{"functionId":"${schema.functionId}","schemaVersion":${schema.schemaVersion}}"""
                        )
                    )
                }
            }
        }
    }

    /** Strict lookup at the latest registered version. */
    suspend fun lookupLatest(functionId: String): AppFunctionSkillEntity? =
        skillDao.lookupLatest(functionId)

    /** Strict lookup at an exact `(functionId, schemaVersion)` pair — used at execute time. */
    suspend fun lookupExact(functionId: String, schemaVersion: Int): AppFunctionSkillEntity? =
        skillDao.lookupExact(functionId, schemaVersion)

    suspend fun listForApp(appPackage: String): List<AppFunctionSkillEntity> =
        skillDao.listForApp(appPackage)

    suspend fun listAll(): List<AppFunctionSkillEntity> = skillDao.listAll()

    /**
     * Records a per-execution usage row. The caller MUST already have written
     * the corresponding `action_execution` row; this helper is invoked from
     * the same transaction (`EnvelopeRepositoryImpl.recordActionInvocation`)
     * so a partial write cannot leak.
     */
    suspend fun recordInvocation(
        skillId: String,
        executionId: String,
        proposalId: String,
        episodeId: String?,
        outcome: ActionExecutionOutcome,
        latencyMs: Long,
        invokedAt: Long = now()
    ) {
        usageDao.insert(
            SkillUsageEntity(
                id = UUID.randomUUID().toString(),
                skillId = skillId,
                executionId = executionId,
                proposalId = proposalId,
                episodeId = episodeId,
                outcome = outcome,
                latencyMs = latencyMs,
                invokedAt = invokedAt
            )
        )
    }

    /** Settings-screen rollup for a single skill over the rolling window. */
    suspend fun stats(skillId: String, sinceMillis: Long) =
        usageDao.aggregate(skillId, sinceMillis)
}
