package com.capsule.app.action

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.handler.TodoActionHandler
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * T055 (003 US2) — verifies [TodoActionHandler]'s `target=local` branch
 * forwards correctly through the `:ml` `IEnvelopeRepository` binder.
 *
 * Uses a JDK [Proxy] for [IEnvelopeRepository] (same pattern as T036
 * `ActionExtractionWorkerTest`) so the test stays handler-scoped — no
 * Room runtime, no service binding. Covers:
 *
 *  - happy path: 3-item input → one binder call with `itemsJson` round-tripped
 *    and a `Success("local:3")` returned
 *  - missing `parentEnvelopeId` → `Failed("missing_parent")` and zero binder calls
 *  - missing `proposalId` → `Failed("missing_proposal_id")`
 *  - missing `items` → `Failed("missing_items")`
 *  - empty `items` array → `Failed("empty_items")`
 *  - null repository → `Failed("ml_binder_unavailable")`
 *  - `RemoteException` from binder → `Failed("binder_remote_exception")`
 *  - mixed item shapes (string + object) → both forwarded verbatim
 *
 * Source envelope mutation is verified separately by T059
 * `ActionDoesNotMutateEnvelopeTest`.
 */
@RunWith(AndroidJUnit4::class)
class TodoAddHandlerLocalTest {

    private val handler = TodoActionHandler()
    private val skill = sampleSkill()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun handle_threeItems_returnsSuccessLocal3_andCallsBinderOnce() = runTest {
        val recorder = RecordingRepoProxy(returnedIds = listOf("e1", "e2", "e3"))
        val args = JSONObject().apply {
            put("target", "local")
            put("parentEnvelopeId", "parent-1")
            put("proposalId", "p-1")
            put("items", JSONArray().apply {
                put("Buy milk")
                put("Mail rent check")
                put(JSONObject().apply {
                    put("text", "Renew passport")
                    put("dueEpochMillis", 1_745_164_800_000L)
                })
            })
        }.toString()

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue("expected Success, got=$result", result is HandlerResult.Success)
        assertEquals("local:3", (result as HandlerResult.Success).info)
        assertEquals(1, recorder.calls.size)

        val call = recorder.calls.single()
        assertEquals("createDerivedTodoEnvelope", call.method)
        assertEquals("parent-1", call.args[0])
        assertEquals("p-1", call.args[2])
        // itemsJson must round-trip the original 3-item shape.
        val forwarded = JSONArray(call.args[1] as String)
        assertEquals(3, forwarded.length())
        assertEquals("Buy milk", forwarded.optString(0))
        assertEquals(
            "Renew passport",
            forwarded.optJSONObject(2)?.optString("text")
        )
    }

    @Test
    fun handle_missingParent_returnsFailed_andZeroBinderCalls() = runTest {
        val recorder = RecordingRepoProxy()
        val args = """{"target":"local","proposalId":"p-1","items":["a","b"]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_parent", (result as HandlerResult.Failed).reason)
        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun handle_missingProposalId_returnsFailed() = runTest {
        val recorder = RecordingRepoProxy()
        val args = """{"target":"local","parentEnvelopeId":"e1","items":["a"]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_proposal_id", (result as HandlerResult.Failed).reason)
        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun handle_missingItems_returnsFailed() = runTest {
        val recorder = RecordingRepoProxy()
        val args = """{"target":"local","parentEnvelopeId":"e1","proposalId":"p1"}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_items", (result as HandlerResult.Failed).reason)
        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun handle_emptyItemsArray_returnsFailed() = runTest {
        val recorder = RecordingRepoProxy()
        val args = """{"target":"local","parentEnvelopeId":"e1","proposalId":"p1","items":[]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("empty_items", (result as HandlerResult.Failed).reason)
        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun handle_nullRepository_returnsFailed_mlBinderUnavailable() = runTest {
        val args = """{"target":"local","parentEnvelopeId":"e1","proposalId":"p1","items":["a"]}"""

        val result = handler.handle(context, skill, args, repository = null)

        assertTrue(result is HandlerResult.Failed)
        assertEquals(
            "ml_binder_unavailable",
            (result as HandlerResult.Failed).reason
        )
    }

    @Test
    fun handle_binderRemoteException_returnsFailed() = runTest {
        val recorder = RecordingRepoProxy(throwOnCall = android.os.RemoteException("dead"))
        val args = """{"target":"local","parentEnvelopeId":"e1","proposalId":"p1","items":["a"]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals(
            "binder_remote_exception",
            (result as HandlerResult.Failed).reason
        )
    }

    @Test
    fun handle_genericThrowable_mappedToCreateDerivedFailed() = runTest {
        val recorder = RecordingRepoProxy(
            throwOnCall = IllegalStateException("DB locked")
        )
        val args = """{"target":"local","parentEnvelopeId":"e1","proposalId":"p1","items":["a"]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertEquals(
            "create_derived_failed",
            (result as HandlerResult.Failed).reason
        )
    }

    @Test
    fun handle_unknownTarget_returnsFailedWithReason() = runTest {
        val recorder = RecordingRepoProxy()
        val args = """{"target":"telepathy","parentEnvelopeId":"e1","proposalId":"p1","items":["a"]}"""

        val result = handler.handle(context, skill, args, recorder.proxy)

        assertTrue(result is HandlerResult.Failed)
        assertTrue(
            (result as HandlerResult.Failed).reason.startsWith("unknown_target:")
        )
        assertEquals(0, recorder.calls.size)
    }

    @Test
    fun handle_malformedArgsJson_returnsFailed_argsParseFailed() = runTest {
        val recorder = RecordingRepoProxy()
        val result = handler.handle(context, skill, "not json", recorder.proxy)
        assertTrue(result is HandlerResult.Failed)
        assertEquals("args_parse_failed", (result as HandlerResult.Failed).reason)
        assertEquals(0, recorder.calls.size)
    }

    // ---- helpers --------------------------------------------------

    private fun sampleSkill() = AppFunctionSummaryParcel(
        functionId = "tasks.createTodo",
        appPackage = "com.capsule.app",
        displayName = "Add to to-dos",
        description = "test fixture",
        schemaVersion = 1,
        argsSchemaJson = "{}",
        sideEffects = "INTERNAL_WRITE",
        reversibility = "REVERSIBLE",
        sensitivityScope = "PUBLIC",
        registeredAtMillis = 0L,
        updatedAtMillis = 0L
    )

    /**
     * JDK Proxy fake for [IEnvelopeRepository] that records every method
     * call and lets the test inject return values or thrown exceptions.
     * Avoids the boilerplate of subclassing the AIDL stub.
     */
    private class RecordingRepoProxy(
        private val returnedIds: List<String> = emptyList(),
        private val throwOnCall: Throwable? = null
    ) {
        data class Call(val method: String, val args: List<Any?>)

        val calls = mutableListOf<Call>()

        val proxy: IEnvelopeRepository = Proxy.newProxyInstance(
            IEnvelopeRepository::class.java.classLoader,
            arrayOf(IEnvelopeRepository::class.java)
        ) { _, method: Method, rawArgs: Array<Any?>? ->
            calls += Call(method.name, rawArgs?.toList() ?: emptyList())
            throwOnCall?.let { throw it }
            when (method.name) {
                "createDerivedTodoEnvelope" -> returnedIds
                "asBinder" -> null  // not used by handler
                else -> defaultFor(method.returnType)
            }
        } as IEnvelopeRepository

        private fun defaultFor(t: Class<*>): Any? = when (t) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Void.TYPE -> null
            else -> null
        }
    }
}
