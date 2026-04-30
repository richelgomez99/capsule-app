package com.capsule.app.action

import com.capsule.app.data.model.AppFunctionSideEffect
import com.capsule.app.data.model.Reversibility
import com.capsule.app.data.model.SensitivityScope

/**
 * Hand-curated schema list the registry seeds at boot until the KSP-generated
 * `BUILT_IN_APP_FUNCTION_SCHEMAS` constant lands. The list MUST stay in sync
 * with the actual handler implementations under `com.capsule.app.action.handler`.
 *
 * This is the v1.1 surface — three skills only:
 *  - `calendar.createEvent`         (US1, EXTERNAL_INTENT, EXTERNAL_MANAGED)
 *  - `tasks.createTodo`             (US2, LOCAL_DB_WRITE,  REVERSIBLE_24H)
 *  - `share.delegate`               (negative-path: refuse + log, NONE)
 *
 * The `argsSchemaJson` strings are inlined here (rather than loaded from
 * `assets/`) so the registry seeds deterministically in unit tests with no
 * Android `Context`.
 */
data class AppFunctionSchema(
    val functionId: String,
    val appPackage: String,
    val displayName: String,
    val description: String,
    val schemaVersion: Int,
    val argsSchemaJson: String,
    val sideEffects: AppFunctionSideEffect,
    val reversibility: Reversibility,
    val sensitivityScope: SensitivityScope
)

object BuiltInAppFunctionSchemas {

    private const val ORBIT_PKG = "com.capsule.app"

    /** US1 — propose-then-launch a Calendar intent for the user. */
    val CALENDAR_CREATE_EVENT = AppFunctionSchema(
        functionId = "calendar.createEvent",
        appPackage = ORBIT_PKG,
        displayName = "Add to Calendar",
        description = "Create a calendar event with a title, time, and optional location.",
        schemaVersion = 1,
        argsSchemaJson = """
            {
              "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["title", "startEpochMillis"],
              "properties": {
                "title":           { "type": "string", "minLength": 1, "maxLength": 200 },
                "startEpochMillis":{ "type": "integer", "minimum": 0 },
                "endEpochMillis":  { "type": "integer", "minimum": 0 },
                "location":        { "type": "string", "maxLength": 256 },
                "notes":           { "type": "string", "maxLength": 4000 },
                "tzId":            { "type": "string" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        sideEffects = AppFunctionSideEffect.EXTERNAL_INTENT,
        reversibility = Reversibility.EXTERNAL_MANAGED,
        sensitivityScope = SensitivityScope.PERSONAL
    )

    /** US2 — append a follow-up to-do envelope into Orbit itself. */
    val TASKS_CREATE_TODO = AppFunctionSchema(
        functionId = "tasks.createTodo",
        appPackage = ORBIT_PKG,
        displayName = "Add to-do",
        description = "Create a follow-up to-do envelope inside Orbit.",
        schemaVersion = 1,
        argsSchemaJson = """
            {
              "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["title"],
              "properties": {
                "title":          { "type": "string", "minLength": 1, "maxLength": 200 },
                "dueEpochMillis": { "type": "integer", "minimum": 0 },
                "notes":          { "type": "string", "maxLength": 4000 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        sideEffects = AppFunctionSideEffect.LOCAL_DB_WRITE,
        reversibility = Reversibility.REVERSIBLE_24H,
        sensitivityScope = SensitivityScope.PERSONAL
    )

    /** Negative-path stub — share-out is refused in v1.1 and audit-logged. */
    val SHARE_DELEGATE = AppFunctionSchema(
        functionId = "share.delegate",
        appPackage = ORBIT_PKG,
        displayName = "Share to another app",
        description = "Share envelope content out to another app. Disabled in v1.1.",
        schemaVersion = 1,
        argsSchemaJson = """
            {
              "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["targetMimeType"],
              "properties": {
                "targetMimeType": { "type": "string" },
                "subject":        { "type": "string", "maxLength": 200 },
                "text":           { "type": "string", "maxLength": 4000 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        sideEffects = AppFunctionSideEffect.EXTERNAL_INTENT,
        reversibility = Reversibility.NONE,
        sensitivityScope = SensitivityScope.SHARE_DELEGATED
    )

    /**
     * Spec 002 Phase 11 Block 13 / T161 — `cluster.summarize` AppFunction.
     * Local-DB write (DERIVED envelope) with reversible 24h soft-delete
     * window (the derived envelope can be deleted like any other and
     * the cluster will retry on FAILED → ACTING). PERSONAL scope —
     * runs entirely on-device through the local-preferring LlmProvider.
     *
     * `proposalId` is optional because cluster.summarize is invoked from
     * the cluster card, NOT from an `action_proposal` row produced by
     * the per-envelope extractor. Forward-compat: when a future
     * Sonnet-driven planner emits a proposal pointing at a cluster, the
     * same schema accepts it.
     */
    val CLUSTER_SUMMARIZE = AppFunctionSchema(
        functionId = "cluster.summarize",
        appPackage = ORBIT_PKG,
        displayName = "Summarize cluster",
        description = "Generate cited bullets from a research-session cluster's members.",
        schemaVersion = 1,
        argsSchemaJson = """
            {
              "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["clusterId"],
              "properties": {
                "clusterId":  { "type": "string", "minLength": 1, "maxLength": 64 },
                "proposalId": { "type": "string", "minLength": 1, "maxLength": 64 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        sideEffects = AppFunctionSideEffect.LOCAL_DB_WRITE,
        reversibility = Reversibility.REVERSIBLE_24H,
        sensitivityScope = SensitivityScope.PERSONAL
    )

    val ALL: List<AppFunctionSchema> = listOf(
        CALENDAR_CREATE_EVENT,
        TASKS_CREATE_TODO,
        SHARE_DELEGATE,
        CLUSTER_SUMMARIZE
    )
}
