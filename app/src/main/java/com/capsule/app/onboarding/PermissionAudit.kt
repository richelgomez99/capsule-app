package com.capsule.app.onboarding

import android.content.Context
import android.util.Log
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.model.AuditAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * T102 — writes `PERMISSION_GRANTED` / `PERMISSION_REVOKED` audit rows
 * (contracts/audit-log-contract.md §3). Fire-and-forget on IO so
 * permission UI flows never block.
 */
object PermissionAudit {

    private const val TAG = "PermissionAudit"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer = AuditLogWriter()

    enum class Permission {
        POST_NOTIFICATIONS,
        SYSTEM_ALERT_WINDOW,
        PACKAGE_USAGE_STATS,
        ACTIVITY_RECOGNITION
    }

    fun record(context: Context, permission: Permission, granted: Boolean) {
        val appCtx = context.applicationContext
        val action = if (granted) AuditAction.PERMISSION_GRANTED else AuditAction.PERMISSION_REVOKED
        val desc = if (granted) "Granted ${permission.name}" else "Declined ${permission.name}"
        scope.launch {
            runCatching {
                val dao = OrbitDatabase.getInstance(appCtx).auditLogDao()
                dao.insert(
                    writer.build(
                        action = action,
                        description = desc,
                        extraJson = """{"permission":"${permission.name}"}"""
                    )
                )
            }.onFailure { Log.w(TAG, "record($permission,$granted) failed", it) }
        }
    }
}
