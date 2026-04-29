package com.capsule.app.data

import com.capsule.app.action.BuiltInAppFunctionSchemas
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T032 — sanity / shape proof for the v1.1 built-in `argsSchemaJson` constants.
 *
 * **Scope (intentionally limited)**: this test verifies the *shape* of each
 * built-in's JSON Schema document — well-formed JSON, expected top-level
 * fields (`type`, `required`, `properties`, `additionalProperties`), every
 * `required` entry referenced by `properties`, and `additionalProperties:false`
 * across the board (so the model can't smuggle extra keys past validation).
 *
 * **Out of scope**: full JSON Schema 2020-12 validation against a candidate
 * `argsJson` instance. That requires a validator dependency
 * (`com.networknt:json-schema-validator` or equivalent) which is not yet on
 * the classpath. The full positive/negative-instance test is deferred until
 * the validator lands as part of T021/T091 (NanoLlmProvider full impl +
 * ActionExecutor schema re-validation).
 *
 * Until then this test catches the high-frequency regressions:
 *   - someone edits `BuiltInAppFunctionSchemas` and pastes invalid JSON
 *   - someone adds a `required` field but forgets to declare it in `properties`
 *   - someone removes `additionalProperties:false` (which would let the model
 *     dump arbitrary keys and silently widen the surface)
 *
 * appfunction-registry-contract.md §8.
 */
class SchemaValidationTest {

    @Test
    fun allBuiltInSchemas_areWellFormedJson() {
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            try {
                JSONObject(schema.argsSchemaJson)
            } catch (t: Throwable) {
                throw AssertionError(
                    "argsSchemaJson for ${schema.functionId} is not valid JSON: ${t.message}",
                    t
                )
            }
        }
    }

    @Test
    fun allBuiltInSchemas_haveExpectedTopLevelFields() {
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            val obj = JSONObject(schema.argsSchemaJson)
            assertEquals(
                "${schema.functionId}: top-level type must be 'object'",
                "object",
                obj.optString("type")
            )
            assertTrue(
                "${schema.functionId}: must declare additionalProperties:false",
                obj.has("additionalProperties") && !obj.optBoolean("additionalProperties", true)
            )
            assertNotNull(
                "${schema.functionId}: must declare a 'properties' object",
                obj.optJSONObject("properties")
            )
        }
    }

    @Test
    fun allRequiredFields_areDeclaredInProperties() {
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            val obj = JSONObject(schema.argsSchemaJson)
            val required = obj.optJSONArray("required") ?: continue
            val properties = obj.getJSONObject("properties")
            for (i in 0 until required.length()) {
                val key = required.getString(i)
                assertTrue(
                    "${schema.functionId}: required field '$key' missing from properties",
                    properties.has(key)
                )
            }
        }
    }

    @Test
    fun calendarSchema_carriesExpectedShape() {
        val obj = JSONObject(BuiltInAppFunctionSchemas.CALENDAR_CREATE_EVENT.argsSchemaJson)
        val required = obj.getJSONArray("required")
        val requiredNames = (0 until required.length()).map { required.getString(it) }.toSet()
        assertTrue("calendar.createEvent must require title", "title" in requiredNames)
        assertTrue(
            "calendar.createEvent must require startEpochMillis",
            "startEpochMillis" in requiredNames
        )

        val properties = obj.getJSONObject("properties")
        assertEquals(
            "title type",
            "string",
            properties.getJSONObject("title").getString("type")
        )
        assertEquals(
            "startEpochMillis must be an integer (millis since epoch)",
            "integer",
            properties.getJSONObject("startEpochMillis").getString("type")
        )
    }

    @Test
    fun todoSchema_titleRequired_dueOptional() {
        val obj = JSONObject(BuiltInAppFunctionSchemas.TASKS_CREATE_TODO.argsSchemaJson)
        val required = obj.getJSONArray("required")
        val requiredNames = (0 until required.length()).map { required.getString(it) }.toSet()
        assertEquals("tasks.createTodo only requires title in v1.1", setOf("title"), requiredNames)

        val properties = obj.getJSONObject("properties")
        // dueEpochMillis is declared but not required → optional.
        assertTrue("dueEpochMillis must be declared as optional", properties.has("dueEpochMillis"))
    }

    @Test
    fun shareSchema_isNegativePathStub_butWellShaped() {
        val obj = JSONObject(BuiltInAppFunctionSchemas.SHARE_DELEGATE.argsSchemaJson)
        val required = obj.getJSONArray("required")
        val requiredNames = (0 until required.length()).map { required.getString(it) }.toSet()
        assertTrue("share.delegate must require targetMimeType", "targetMimeType" in requiredNames)
    }
}
