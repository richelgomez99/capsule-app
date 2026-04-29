package com.capsule.app.diary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/**
 * T064 (003 US2) JVM unit tests for [parseTodoItems] backing
 * [EnvelopeCard]'s derived-to-do checklist row.
 *
 * Format reference (`IntentEnvelopeEntity.todoMetaJson`):
 *
 *     {
 *       "items": [
 *         {"text": "...", "done": false, "dueEpochMillis": 1745164800000},
 *         {"text": "...", "done": true,  "dueEpochMillis": null}
 *       ],
 *       "derivedFromProposalId": "..."
 *     }
 */
class EnvelopeCardTodoParseTest {

    @Test
    fun parseTodoItems_threeItems_roundTripsTextDoneAndDue() {
        val json = JSONObject().apply {
            put("derivedFromProposalId", "p-1")
            put("items", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "Buy milk")
                    put("done", false)
                    put("dueEpochMillis", 1_745_164_800_000L)
                })
                put(JSONObject().apply {
                    put("text", "Mail rent check")
                    put("done", true)
                    put("dueEpochMillis", JSONObject.NULL)
                })
                put(JSONObject().apply {
                    put("text", "Renew passport")
                    put("done", false)
                })
            })
        }.toString()

        val items = parseTodoItems(json)

        assertEquals(3, items.size)

        assertEquals("Buy milk", items[0].text)
        assertEquals(false, items[0].done)
        assertEquals(1_745_164_800_000L, items[0].dueEpochMillis)

        assertEquals("Mail rent check", items[1].text)
        assertEquals(true, items[1].done)
        assertNull(items[1].dueEpochMillis)

        assertEquals("Renew passport", items[2].text)
        assertEquals(false, items[2].done)
        assertNull(items[2].dueEpochMillis)
    }

    @Test
    fun parseTodoItems_negativeDue_treatedAsNull() {
        // Defensive: the writer is supposed to use NULL not -1, but the
        // reader has to be tolerant of legacy/buggy rows.
        val json = """
            {"items":[{"text":"Eat","done":false,"dueEpochMillis":-1}]}
        """.trimIndent()
        val parsed = parseTodoItems(json)
        assertEquals(1, parsed.size)
        assertNull(parsed[0].dueEpochMillis)
    }

    @Test
    fun parseTodoItems_blankOrMissingText_dropped() {
        val json = """
            {"items":[
              {"text":"   ","done":false},
              {"done":true},
              {"text":"valid","done":false}
            ]}
        """.trimIndent()
        val parsed = parseTodoItems(json)
        assertEquals(1, parsed.size)
        assertEquals("valid", parsed[0].text)
    }

    @Test
    fun parseTodoItems_missingDoneFlag_defaultsFalse() {
        val json = """{"items":[{"text":"a"},{"text":"b"}]}"""
        val parsed = parseTodoItems(json)
        assertEquals(2, parsed.size)
        assertTrue(parsed.none { it.done })
    }

    @Test
    fun parseTodoItems_emptyArray_returnsEmpty() {
        assertEquals(0, parseTodoItems("""{"items":[]}""").size)
    }

    @Test
    fun parseTodoItems_missingItemsKey_returnsEmpty() {
        assertEquals(0, parseTodoItems("""{"derivedFromProposalId":"p"}""").size)
    }

    @Test
    fun parseTodoItems_malformedJson_returnsEmpty() {
        assertEquals(0, parseTodoItems("not a json").size)
        assertEquals(0, parseTodoItems("").size)
        assertEquals(0, parseTodoItems("[]").size)  // top-level array, not object
    }

    @Test
    fun parseTodoItems_nonObjectItem_skipped() {
        // Each item MUST be an object — bare strings (passed by the
        // upstream LlmProvider before normalisation) should be tolerated
        // by skipping rather than crashing.
        val json = """{"items":["raw-string", {"text":"ok"}, 42]}"""
        val parsed = parseTodoItems(json)
        assertEquals(1, parsed.size)
        assertEquals("ok", parsed[0].text)
    }

    @Test
    fun parseTodoItems_textIsTrimmed() {
        val json = """{"items":[{"text":"  spaced  ","done":false}]}"""
        val parsed = parseTodoItems(json)
        assertEquals("spaced", parsed[0].text)
    }
}
