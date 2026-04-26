package com.capsule.app.data

import com.capsule.app.ai.DigestComposer
import com.capsule.app.ai.DigestComposition
import com.capsule.app.ai.DigestInputEnvelope
import com.capsule.app.ai.DigestWindow
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * T074 / 003 US3 — `:ml`-side delegate for the weekly digest. Lives in
 * the data package so the [EnvelopeRepositoryImpl] (which holds the
 * encrypted-Room reference) can wire it without leaking AICore types
 * into the AIDL surface.
 *
 * Lifecycle:
 *  1. Caller (the [com.capsule.app.continuation.workers.WeeklyDigestWorker]
 *     process) invokes [runWeeklyDigest] over the binder with the target
 *     local Sunday (e.g. `"2026-04-26"`).
 *  2. We compute the `[Mon..Sat]` window, load up to 100 REGULAR
 *     envelopes (per weekly-digest-contract.md §4), project them to
 *     [DigestInputEnvelope]s, and hand them to the [DigestComposer].
 *  3. On `EmptyWindow` → write `DIGEST_SKIPPED reason=too_sparse` and
 *     return `"SKIPPED:too_sparse"`.
 *  4. On `Composed` → try to insert the new DIGEST envelope through
 *     [EnvelopeStorageBackend.insertDigestTransaction]. The partial
 *     unique index `index_digest_unique_per_day` enforces "exactly one
 *     DIGEST per day" — if it fires, we map to
 *     `"SKIPPED:already_exists"` and audit accordingly.
 *  5. On generic failure (LLM threw, Room threw something other than
 *     a UNIQUE conflict) → `"FAILED:<short-reason>"`.
 *
 * Outcome strings are the worker's contract — see
 * [com.capsule.app.continuation.workers.WeeklyDigestWorker.doWork] for
 * how they're mapped to `Result.success/retry/failure`.
 */
class WeeklyDigestDelegate(
    private val database: OrbitDatabase,
    private val backend: EnvelopeStorageBackend,
    private val composer: DigestComposer,
    private val auditWriter: AuditLogWriter,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {

    private val auditDao = database.auditLogDao()

    suspend fun runWeeklyDigest(targetDayLocal: String): String {
        val zone = zoneIdProvider()
        val targetSunday = runCatching { LocalDate.parse(targetDayLocal) }.getOrNull()
            ?: return "FAILED:bad_target_day"

        val window = runCatching { DigestWindow.forSunday(zone, targetSunday) }.getOrElse {
            return "FAILED:not_a_sunday"
        }

        val rows = try {
            backend.listRegularEnvelopesInWindow(
                windowStartDayLocal = window.windowStart.toString(),
                windowEndDayLocalInclusive = window.windowEndInclusive.toString(),
                limit = MAX_INPUT_ENVELOPES
            )
        } catch (t: Throwable) {
            return "FAILED:read_failed"
        }

        val inputs = rows.map { it.toDigestInput() }

        val composition = try {
            composer.compose(window, inputs)
        } catch (t: Throwable) {
            return "FAILED:compose_failed"
        }

        return when (composition) {
            DigestComposition.EmptyWindow -> {
                writeAudit(
                    action = AuditAction.DIGEST_SKIPPED,
                    description = "Weekly digest skipped (too_sparse)",
                    envelopeId = null,
                    extras = mapOf(
                        "reason" to "too_sparse",
                        "weekId" to window.targetSunday.toString(),
                        "envelopeCount" to inputs.size.toString()
                    )
                )
                "SKIPPED:too_sparse"
            }

            is DigestComposition.Composed -> persistDigest(window, composition, zone)
        }
    }

    private suspend fun persistDigest(
        window: DigestWindow,
        composition: DigestComposition.Composed,
        zone: ZoneId
    ): String {
        val now = clock()
        val envelope = buildDigestEntity(window, composition, zone, now)

        val auditExtras = JSONObject().apply {
            put("weekId", window.targetSunday.toString())
            put("envelopeCount", composition.derivedFromEnvelopeIds.size)
            put("locale", composition.locale)
            put("provenance", composition.provenance.name)
        }

        val successAudit = auditWriter.build(
            action = AuditAction.DIGEST_GENERATED,
            description = "Weekly digest generated for ${window.targetSunday}",
            envelopeId = envelope.id,
            extraJson = auditExtras.toString()
        )

        val inserted = try {
            backend.insertDigestTransaction(envelope, successAudit)
        } catch (t: Throwable) {
            return "FAILED:insert_failed"
        }

        return if (inserted) {
            "GENERATED:${envelope.id}"
        } else {
            // Partial unique index conflict — write a SKIPPED row out
            // of the failed transaction so we still record the attempt.
            writeAudit(
                action = AuditAction.DIGEST_SKIPPED,
                description = "Weekly digest skipped (already_exists)",
                envelopeId = null,
                extras = mapOf(
                    "reason" to "already_exists",
                    "weekId" to window.targetSunday.toString()
                )
            )
            "SKIPPED:already_exists"
        }
    }

    private fun buildDigestEntity(
        window: DigestWindow,
        composition: DigestComposition.Composed,
        zone: ZoneId,
        now: Long
    ): IntentEnvelopeEntity {
        val derivedJson = JSONArray().also { arr ->
            composition.derivedFromEnvelopeIds.forEach { arr.put(it) }
        }.toString()

        val nowZdt: ZonedDateTime = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val syntheticState = StateSnapshot(
            appCategory = AppCategory.OTHER,
            activityState = ActivityState.UNKNOWN,
            tzId = zone.id,
            hourLocal = nowZdt.hour,
            dayOfWeekLocal = nowZdt.dayOfWeek.value
        )

        return IntentEnvelopeEntity(
            id = UUID.randomUUID().toString(),
            contentType = ContentType.TEXT,
            textContent = composition.text,
            imageUri = null,
            textContentSha256 = sha256(composition.text),
            intent = Intent.REFERENCE,
            intentConfidence = null,
            intentSource = IntentSource.AUTO_AMBIGUOUS,
            intentHistoryJson = "[]",
            state = syntheticState,
            createdAt = now,
            dayLocal = window.targetSunday.toString(),
            kind = EnvelopeKind.DIGEST,
            derivedFromEnvelopeIdsJson = derivedJson,
            todoMetaJson = null
        )
    }

    private suspend fun writeAudit(
        action: AuditAction,
        description: String,
        envelopeId: String?,
        extras: Map<String, String>
    ) {
        val extraJson = JSONObject().also { obj ->
            extras.forEach { (k, v) -> obj.put(k, v) }
        }.toString()
        auditDao.insert(
            auditWriter.build(
                action = action,
                description = description,
                envelopeId = envelopeId,
                extraJson = extraJson
            )
        )
    }

    private fun IntentEnvelopeEntity.toDigestInput(): DigestInputEnvelope {
        // Title heuristic: first non-blank line up to 80 chars. Mirrors
        // EnvelopeCard's summary derivation closely enough that the LLM
        // sees the same cue the user did. We don't have day-header
        // salience scores wired through to the entity yet (002 generates
        // them at day-summary time but doesn't persist per-envelope), so
        // salience defaults to a uniform 0.5 — the composer's bucketing
        // by day still gives reasonable per-day variety.
        val title = textContent
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?.take(MAX_TITLE_CHARS)
            ?: "Untitled capture"
        return DigestInputEnvelope(
            id = id,
            title = title,
            intent = intent.name,
            appCategory = state.appCategory.name,
            dayLocal = dayLocal,
            salience = DEFAULT_SALIENCE
        )
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_INPUT_ENVELOPES = 100
        const val MAX_TITLE_CHARS = 80
        const val DEFAULT_SALIENCE = 0.5f
    }
}
