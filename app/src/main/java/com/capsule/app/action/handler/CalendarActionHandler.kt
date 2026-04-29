package com.capsule.app.action.handler

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.capsule.app.action.ActionHandler
import com.capsule.app.action.HandlerResult
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import org.json.JSONException
import org.json.JSONObject

/**
 * T049 — concrete `calendar.createEvent` handler. Fires
 * [Intent.ACTION_INSERT] against [CalendarContract.Events] so the
 * system Calendar app handles account selection + final user
 * confirmation (research §4).
 *
 * Required args (validated by [com.capsule.app.ai.extract.ActionExtractor]
 * at extract time and re-checked in `ActionExecutorService` at execute
 * time):
 *  - title: String                → Events.TITLE
 *  - startEpochMillis: Long       → CalendarContract.EXTRA_EVENT_BEGIN_TIME
 *
 * Optional args:
 *  - endEpochMillis: Long         (defaults to start + 1h per research §4)
 *  - location: String             → Events.EVENT_LOCATION
 *  - notes: String                → Events.DESCRIPTION
 *  - tzId: String                 → Events.EVENT_TIMEZONE
 *
 * Lives in `:capture`. No network. No DB writes.
 *
 * Reversibility: [com.capsule.app.data.model.Reversibility.EXTERNAL_MANAGED] —
 * once the user confirms in the system Calendar app, Orbit cannot retract.
 * The 5s in-app undo only suppresses the Orbit-side audit; see
 * specs/003-orbit-actions/contracts/action-execution-contract.md §5.
 */
class CalendarActionHandler : ActionHandler {
    override suspend fun handle(
        context: Context,
        skill: AppFunctionSummaryParcel,
        argsJson: String,
        repository: IEnvelopeRepository?
    ): HandlerResult {
        val started = System.nanoTime()
        val args: JSONObject = try {
            JSONObject(argsJson)
        } catch (e: JSONException) {
            return HandlerResult.Failed(elapsedMs(started), "args_parse_failed", e)
        }

        val title = args.optString("title").takeIf { it.isNotBlank() }
            ?: return HandlerResult.Failed(elapsedMs(started), "missing_title")
        val startEpoch = args.optLong("startEpochMillis", -1L)
            .takeIf { it >= 0 }
            ?: return HandlerResult.Failed(elapsedMs(started), "missing_start_time")
        val endEpoch = args.optLong("endEpochMillis", -1L)
            .takeIf { it >= 0 }
            ?: (startEpoch + DEFAULT_DURATION_MS)

        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpoch)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpoch)

        args.optString("location").takeIf { it.isNotBlank() }?.let {
            intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it)
        }
        args.optString("notes").takeIf { it.isNotBlank() }?.let {
            intent.putExtra(CalendarContract.Events.DESCRIPTION, it)
        }
        args.optString("tzId").takeIf { it.isNotBlank() }?.let {
            intent.putExtra(CalendarContract.Events.EVENT_TIMEZONE, it)
        }

        return try {
            context.startActivity(intent)
            HandlerResult.Dispatched(
                latencyMs = elapsedMs(started),
                info = "calendar_intent_fired"
            )
        } catch (e: ActivityNotFoundException) {
            HandlerResult.Failed(elapsedMs(started), "no_handler", e)
        } catch (e: SecurityException) {
            // Some OEM Calendar implementations require additional
            // permissions on certain extras (e.g., attendee lists). v1.1
            // never sends those, so this should not fire — if it does,
            // surface the structured failure for forensic inspection.
            HandlerResult.Failed(elapsedMs(started), "security_exception", e)
        }
    }

    companion object {
        /** Research §4: end-time defaults to +1h when Nano didn't extract one. */
        private const val DEFAULT_DURATION_MS: Long = 60L * 60L * 1000L
    }
}

internal fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000L
