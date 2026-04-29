package com.capsule.app.action.handler

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.capsule.app.action.ActionHandler
import com.capsule.app.action.HandlerResult
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * T060 — `tasks.createTodo` handler. Two execution paths gated by
 * `args.target`:
 *
 *  - `local`  (default): writes N derived envelopes via the `:ml`
 *    binder's [IEnvelopeRepository.createDerivedTodoEnvelope]. Source
 *    envelope is NOT mutated (Principle III). Returns
 *    [HandlerResult.Success] with `info=local:N`.
 *
 *  - `external`: fires `Intent.ACTION_SEND` with `EXTRA_TEXT`. First
 *    use opens a chooser; the user's selection is persisted in
 *    `SharedPreferences("orbit.actions").todoTargetPackage` so the
 *    second use dispatches directly without the sheet
 *    (action-execution-contract.md §4 step 3).
 *
 * Required args:
 *  - parentEnvelopeId: String (local target only — read by repo to
 *    stamp `derivedFromProposalId` provenance and link the audit row)
 *  - items: JSONArray — each entry is either a plain string ("buy
 *    milk") or an object `{"text":"…","dueEpochMillis":<long>}`.
 *
 * Optional args:
 *  - target: "local" | "external"      (default "local")
 *  - proposalId: String                 (used in audit row's `extra`)
 *  - mimeType: String                   (external only; default
 *    "text/plain")
 *
 * Lives in `:capture`. Local-target writes go via the binder, so this
 * handler still respects Principle VI (no direct DB access from
 * `:capture`).
 */
class TodoActionHandler : ActionHandler {

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

        val items: JSONArray = parseItems(args)
            ?: return HandlerResult.Failed(elapsedMs(started), "missing_items")
        if (items.length() == 0) {
            return HandlerResult.Failed(elapsedMs(started), "empty_items")
        }

        return when (val target = args.optString("target", "local")) {
            "local"    -> dispatchLocal(args, items, repository, started)
            "external" -> dispatchExternal(context, items, args, started)
            else       -> HandlerResult.Failed(elapsedMs(started), "unknown_target:$target")
        }
    }

    // ---- target=local ------------------------------------------------

    private fun dispatchLocal(
        args: JSONObject,
        items: JSONArray,
        repository: IEnvelopeRepository?,
        started: Long
    ): HandlerResult {
        val repo = repository ?: return HandlerResult.Failed(
            elapsedMs(started), "ml_binder_unavailable"
        )
        val parentId = args.optString("parentEnvelopeId").takeIf { it.isNotBlank() }
            ?: return HandlerResult.Failed(elapsedMs(started), "missing_parent")
        val proposalId = args.optString("proposalId").takeIf { it.isNotBlank() }
            ?: return HandlerResult.Failed(elapsedMs(started), "missing_proposal_id")

        return try {
            val newIds = repo.createDerivedTodoEnvelope(parentId, items.toString(), proposalId)
            HandlerResult.Success(elapsedMs(started), "local:${newIds.size}")
        } catch (e: android.os.RemoteException) {
            HandlerResult.Failed(elapsedMs(started), "binder_remote_exception", e)
        } catch (t: Throwable) {
            HandlerResult.Failed(elapsedMs(started), "create_derived_failed", t)
        }
    }

    // ---- target=external ---------------------------------------------

    private fun dispatchExternal(
        context: Context,
        items: JSONArray,
        args: JSONObject,
        started: Long
    ): HandlerResult {
        val text = renderItemsAsText(items)
        val mimeType = args.optString("mimeType").takeIf { it.isNotBlank() } ?: "text/plain"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberedPkg = prefs.getString(KEY_TODO_TARGET, null)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            if (rememberedPkg != null && resolvesTo(context, sendIntent, rememberedPkg)) {
                sendIntent.setPackage(rememberedPkg)
                context.startActivity(sendIntent)
                HandlerResult.Dispatched(elapsedMs(started), "external:remembered:$rememberedPkg")
            } else {
                // First-use chooser. The user's selection isn't returned
                // through ACTION_SEND directly; the UI calls
                // [recordRememberedTarget] on the chooser callback.
                val chooser = Intent.createChooser(sendIntent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                HandlerResult.Dispatched(elapsedMs(started), "external:chooser")
            }
        } catch (e: ActivityNotFoundException) {
            HandlerResult.Failed(elapsedMs(started), "no_handler", e)
        } catch (e: SecurityException) {
            HandlerResult.Failed(elapsedMs(started), "security_exception", e)
        }
    }

    // ---- Helpers -----------------------------------------------------

    private fun parseItems(args: JSONObject): JSONArray? {
        val raw = args.opt("items") ?: return null
        return when (raw) {
            is JSONArray -> raw
            is String -> runCatching { JSONArray(raw) }.getOrNull()
            else -> null
        }
    }

    private fun renderItemsAsText(items: JSONArray): String = buildString {
        for (i in 0 until items.length()) {
            val text = when (val raw = items.opt(i)) {
                is String -> raw
                is JSONObject -> raw.optString("text")
                else -> ""
            }.trim()
            if (text.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append("• ").append(text)
            }
        }
    }

    private fun resolvesTo(context: Context, intent: Intent, pkg: String): Boolean {
        val probe = Intent(intent).setPackage(pkg)
        return probe.resolveActivity(context.packageManager) != null
    }

    private fun elapsedMs(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L

    companion object {
        const val PREFS_NAME = "orbit.actions"
        const val KEY_TODO_TARGET = "todoTargetPackage"

        /**
         * Persist the user's chosen share-target package. Called from
         * UI after the chooser sheet closes (or from the T065 "Forget
         * remembered to-do app" affordance with `pkg=null`).
         */
        fun recordRememberedTarget(context: Context, pkg: String?) {
            val editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
            if (pkg == null) editor.remove(KEY_TODO_TARGET) else editor.putString(KEY_TODO_TARGET, pkg)
            editor.apply()
        }

        /** True when the named package is currently installed + resolves to ACTION_SEND. */
        fun isPackageInstalledForShare(context: Context, pkg: String): Boolean = try {
            context.packageManager.getApplicationInfo(pkg, 0)
            val probe = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(pkg)
                putExtra(Intent.EXTRA_TEXT, "")
            }
            probe.resolveActivity(context.packageManager) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
