package com.capsule.app.action

import com.capsule.app.action.handler.CalendarActionHandler
import com.capsule.app.action.handler.ShareActionHandler
import com.capsule.app.action.handler.TodoActionHandler

/**
 * Static dispatch table from `functionId` to its in-process [ActionHandler].
 *
 * Kept as a plain object (no DI) so the `:capture` process can construct
 * its executor without dragging the rest of the graph in. New v1.1
 * skills register here at compile time.
 *
 * If an `argsJson` payload references an unknown `functionId`, the
 * executor service writes `outcome=FAILED, reason="unknown_skill"` —
 * never silently no-ops.
 */
object ActionHandlerRegistry {

    private val handlers: Map<String, ActionHandler> = mapOf(
        "calendar.createEvent" to CalendarActionHandler(),
        "tasks.createTodo"     to TodoActionHandler(),
        "share.delegate"       to ShareActionHandler()
    )

    fun lookup(functionId: String): ActionHandler? = handlers[functionId]

    fun knownFunctionIds(): Set<String> = handlers.keys
}
