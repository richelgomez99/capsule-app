package com.capsule.app.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.model.AuditAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T093 — user-initiated, local-only export.
 *
 * Writes an unencrypted JSON bundle to `Downloads/Orbit-Export-<ts>/` per
 * audit-log-contract.md §7. Brackets the run with `EXPORT_STARTED` /
 * `EXPORT_COMPLETED` audit rows.
 *
 * Uses the [MediaStore.Downloads] content URI (API 29+ scoped storage;
 * minSdk 33 — always available). `RELATIVE_PATH` puts each file under
 * `Download/Orbit-Export-<ts>/` and the file appears in the user's
 * Downloads app automatically. No WRITE_EXTERNAL_STORAGE permission
 * required on API 29+.
 *
 * Not a bound Android `Service` — name is retained from tasks.md but
 * the surface is a plain suspend-fun invoked from [SettingsActivity].
 */
object ExportService {

    private const val TAG = "ExportService"

    /** Returns the relative folder path written to (e.g. "Orbit-Export-20260421-143055"). */
    suspend fun export(context: Context): ExportResult = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val db = OrbitDatabase.getInstance(appCtx)
        val auditDao = db.auditLogDao()
        val writer = AuditLogWriter()

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val folderName = "Orbit-Export-$ts"

        // Write EXPORT_STARTED up-front so a crash mid-export still leaves a
        // bracket-open audit row the user can see in "What Orbit did today".
        auditDao.insert(
            writer.build(
                action = AuditAction.EXPORT_STARTED,
                description = "Export started → Downloads/$folderName"
            )
        )

        runCatching {
            val envelopes = db.intentEnvelopeDao().listAll()
            val continuations = db.continuationDao().listAll()
            val results = db.continuationResultDao().listAll()
            val audits = auditDao.listAll()

            writeFile(appCtx, folderName, "envelopes.json", envelopesJson(envelopes))
            writeFile(appCtx, folderName, "continuations.json", continuationsJson(continuations))
            writeFile(appCtx, folderName, "results.json", resultsJson(results))
            writeFile(appCtx, folderName, "audit.json", auditsJson(audits))
            writeFile(appCtx, folderName, "README.md", readme(envelopes.size, continuations.size, results.size, audits.size))

            auditDao.insert(
                writer.build(
                    action = AuditAction.EXPORT_COMPLETED,
                    description = "Export completed (${envelopes.size} envelopes, ${audits.size} audit rows)",
                    extraJson = """{"folder":"$folderName","counts":{"envelopes":${envelopes.size},"continuations":${continuations.size},"results":${results.size},"audit":${audits.size}}}"""
                )
            )
            ExportResult.Success(folderName)
        }.getOrElse { t ->
            Log.w(TAG, "Export failed", t)
            // No EXPORT_FAILED action in the v1 closed set — surface via
            // the COMPLETED description so the failure is still user-visible.
            runCatching {
                auditDao.insert(
                    writer.build(
                        action = AuditAction.EXPORT_COMPLETED,
                        description = "Export failed: ${t.javaClass.simpleName}",
                        extraJson = """{"folder":"$folderName","error":"${t.message?.take(200)?.replace("\"", "\\\"")}"}"""
                    )
                )
            }
            ExportResult.Failure(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun writeFile(ctx: Context, folder: String, name: String, body: String): Uri {
        val resolver = ctx.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder"
        val mime = if (name.endsWith(".md")) "text/markdown" else "application/json"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore.insert returned null for $name")
        resolver.openOutputStream(uri).use { out ->
            out ?: error("openOutputStream returned null for $name")
            out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
        return uri
    }

    private fun envelopesJson(rows: List<IntentEnvelopeEntity>): String {
        val arr = JSONArray()
        rows.forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("contentType", e.contentType.name)
            o.put("textContent", e.textContent ?: JSONObject.NULL)
            o.put("imageUri", e.imageUri ?: JSONObject.NULL)
            o.put("textContentSha256", e.textContentSha256 ?: JSONObject.NULL)
            o.put("intent", e.intent.name)
            o.put("intentConfidence", e.intentConfidence?.toDouble() ?: JSONObject.NULL)
            o.put("intentSource", e.intentSource.name)
            o.put("intentHistoryJson", e.intentHistoryJson)
            o.put("appCategory", e.state.appCategory.name)
            o.put("activityState", e.state.activityState.name)
            o.put("tzId", e.state.tzId)
            o.put("hourLocal", e.state.hourLocal)
            o.put("dayOfWeekLocal", e.state.dayOfWeekLocal)
            o.put("createdAt", e.createdAt)
            o.put("dayLocal", e.dayLocal)
            o.put("isArchived", e.isArchived)
            o.put("isDeleted", e.isDeleted)
            o.put("deletedAt", e.deletedAt ?: JSONObject.NULL)
            o.put("sharedContinuationResultId", e.sharedContinuationResultId ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun continuationsJson(rows: List<ContinuationEntity>): String {
        val arr = JSONArray()
        rows.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("envelopeId", c.envelopeId)
            o.put("type", c.type.name)
            o.put("status", c.status.name)
            o.put("inputUrl", c.inputUrl ?: JSONObject.NULL)
            o.put("scheduledAt", c.scheduledAt)
            o.put("startedAt", c.startedAt ?: JSONObject.NULL)
            o.put("completedAt", c.completedAt ?: JSONObject.NULL)
            o.put("attemptCount", c.attemptCount)
            o.put("failureReason", c.failureReason ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun resultsJson(rows: List<ContinuationResultEntity>): String {
        val arr = JSONArray()
        rows.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("continuationId", r.continuationId)
            o.put("envelopeId", r.envelopeId)
            o.put("producedAt", r.producedAt)
            o.put("title", r.title ?: JSONObject.NULL)
            o.put("domain", r.domain ?: JSONObject.NULL)
            o.put("canonicalUrl", r.canonicalUrl ?: JSONObject.NULL)
            o.put("canonicalUrlHash", r.canonicalUrlHash ?: JSONObject.NULL)
            o.put("excerpt", r.excerpt ?: JSONObject.NULL)
            o.put("summary", r.summary ?: JSONObject.NULL)
            o.put("summaryModel", r.summaryModel ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun auditsJson(rows: List<AuditLogEntryEntity>): String {
        val arr = JSONArray()
        rows.forEach { a ->
            val o = JSONObject()
            o.put("id", a.id)
            o.put("at", a.at)
            o.put("action", a.action.name)
            o.put("description", a.description)
            o.put("envelopeId", a.envelopeId ?: JSONObject.NULL)
            o.put("extraJson", a.extraJson ?: JSONObject.NULL)
            o.put("llmProvider", a.llmProvider ?: JSONObject.NULL)
            o.put("llmModel", a.llmModel ?: JSONObject.NULL)
            o.put("promptDigestSha256", a.promptDigestSha256 ?: JSONObject.NULL)
            o.put("tokenCount", a.tokenCount ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString(2)
    }

    private fun readme(ne: Int, nc: Int, nr: Int, na: Int): String = """
        |# Orbit export
        |
        |Generated: ${Date()}
        |
        |## Contents
        |- `envelopes.json` — $ne intent-envelope rows (every capture on device, including archived + soft-deleted).
        |- `continuations.json` — $nc continuation (enrichment) attempts.
        |- `results.json` — $nr hydrated URL results (title, domain, summary).
        |- `audit.json` — $na audit-log rows (up to the last 90 days; older rows are purged daily per contract §2).
        |
        |## Notes
        |- **Not encrypted.** The on-device DB is encrypted with SQLCipher; this export bundle is plain JSON on purpose so you can inspect, grep, and re-import without Orbit. Treat it accordingly.
        |- **Not signed.** There is no e2e signature; anything with write access to Downloads could mutate these files. Trust nothing beyond your own device.
        |- **Soft-deleted envelopes are included.** Rows where `isDeleted=true` or `deletedAt` is non-null are still on device until the retention worker hard-purges them (30-day tombstone). Exporting restores full visibility in case you need to recover a deleted capture.
        |- **Audit log is retention-bounded.** Entries older than 90 days are already gone by design.
        |- **No cloud traffic.** This export did not leave the device.
        |
        |## Schema stability
        |Fields mirror the Room entities 1:1 (see `com.capsule.app.data.entity.*`). Column names may change in future versions; re-running the export is the source of truth.
    """.trimMargin()

    sealed class ExportResult {
        data class Success(val folderName: String) : ExportResult()
        data class Failure(val message: String) : ExportResult()
    }
}
