package com.capsule.app.data.ipc

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.capsule.app.audit.AuditLogImpl
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.ai.LlmProviderRouter
import com.capsule.app.ai.extract.ActionExtractor
import com.capsule.app.continuation.ContinuationEngine
import com.capsule.app.data.ActionsRepositoryDelegate
import com.capsule.app.data.AppFunctionRegistry
import com.capsule.app.data.ClusterRepository
import com.capsule.app.data.EnvelopeRepositoryImpl
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.WeeklyDigestDelegate
import com.capsule.app.ai.DigestComposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Bound service running in :ml process. Owns the encrypted Room database
 * and exposes the IEnvelopeRepository AIDL surface.
 *
 * Callers: :capture (seal path), :ui (read + mutate paths).
 *
 * The backend is selected via [com.capsule.app.data.EnvelopeStorageBackend]
 * so v1.1 cloud / v1.3 BYOC can be added without modifying this service.
 */
class EnvelopeRepositoryService : Service() {

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(serviceJob)

    private lateinit var repository: EnvelopeRepositoryImpl
    private lateinit var auditLog: AuditLogImpl

    override fun onCreate() {
        super.onCreate()
        val db = OrbitDatabase.getInstance(applicationContext)
        val backend = LocalRoomBackend(db)
        val auditWriter = AuditLogWriter()
        // T068 — engine wired here so every `seal()` with URLs fires a
        // WorkManager job once the Room transaction commits. Tests that
        // construct `EnvelopeRepositoryImpl` directly pass `null` for the
        // engine and stay oblivious to WorkManager.
        val engine = ContinuationEngine.create(applicationContext)
        // T025/T027 — actions registry + delegate live in :ml so binder
        // calls from :ui flow through here. Registry is constructed eagerly
        // but boot-time `registerAll(BUILT_IN)` is owned by Application.
        val registry = AppFunctionRegistry(
            database = db,
            skillDao = db.appFunctionSkillDao(),
            usageDao = db.skillUsageDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = auditWriter
        )
        val actionsDelegate = ActionsRepositoryDelegate(
            database = db,
            registry = registry,
            auditWriter = auditWriter,
            scope = serviceScope
        )
        // T043/T044 — extractor lives in :ml and is invoked via
        // [IEnvelopeRepository.extractActionsForEnvelope] from
        // [com.capsule.app.ai.extract.ActionExtractionWorker].
        val actionExtractor = ActionExtractor(
            database = db,
            envelopeDao = db.intentEnvelopeDao(),
            proposalDao = db.actionProposalDao(),
            auditLogDao = db.auditLogDao(),
            registry = registry,
            llmProvider = LlmProviderRouter.createPreferLocal(applicationContext),
            auditWriter = auditWriter
        )
        // T072/T074 — weekly digest delegate. Composer reuses the same
        // NanoLlmProvider instance, taking advantage of the locale-gated
        // structured fallback when AICore isn't available. Backend
        // pointer is shared so the partial unique index check happens
        // inside the same Room writer.
        val weeklyDigestDelegate = WeeklyDigestDelegate(
            database = db,
            backend = backend,
            composer = DigestComposer(LlmProviderRouter.createPreferLocal(applicationContext)),
            auditWriter = auditWriter
        )
        repository = EnvelopeRepositoryImpl(
            backend = backend,
            auditWriter = auditWriter,
            scope = serviceScope,
            continuationEngine = engine,
            actionsDelegate = actionsDelegate,
            actionExtractor = actionExtractor,
            weeklyDigestDelegate = weeklyDigestDelegate,
            // Spec 002 Phase 11 Block 5 / T135 — cluster read surface.
            clusterRepository = ClusterRepository(db.clusterDao())
        )
        // T088 — same service binder pool exposes the audit-log surface on a
        // distinct intent action so the Settings / audit viewer process can
        // read audit rows without a second service class.
        auditLog = AuditLogImpl(auditLogDao = db.auditLogDao())
    }

    override fun onBind(intent: Intent?): IBinder {
        // T088 — dispatch on intent action. Default (null action or the
        // envelope-repository action) returns the repository binder for
        // backwards compatibility with all existing callers.
        return when (intent?.action) {
            ACTION_BIND_AUDIT_LOG -> auditLog
            else -> repository
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        /** T088 — intent action for binding to the audit-log surface. */
        const val ACTION_BIND_AUDIT_LOG = "com.capsule.app.action.BIND_AUDIT_LOG"
    }
}

