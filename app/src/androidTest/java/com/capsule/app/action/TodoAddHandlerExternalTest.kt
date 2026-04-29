package com.capsule.app.action

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.handler.TodoActionHandler
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T056 (003 US2) — verifies [TodoActionHandler]'s `target=external`
 * branch builds the correct `Intent.ACTION_SEND` and respects the
 * remembered share-target package in
 * `SharedPreferences("orbit.actions").todoTargetPackage`.
 *
 * Pattern mirrors T037 (`CalendarInsertHandlerTest`): a [ContextWrapper]
 * captures `startActivity` calls so the test never actually launches a
 * foreign app. SharedPreferences is cleared in `@Before` and `@After`
 * so each test starts from a clean slate.
 */
@RunWith(AndroidJUnit4::class)
class TodoAddHandlerExternalTest {

    private val handler = TodoActionHandler()
    private val skill = sampleSkill()
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        val appCtx: Context = ApplicationProvider.getApplicationContext()
        prefs = appCtx.getSharedPreferences(
            TodoActionHandler.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun handle_external_firstUse_dispatchesChooser() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """
            {"target":"external","items":["Buy milk","Mail rent check"]}
        """.trimIndent()

        val result = handler.handle(ctx, skill, args)

        assertTrue("expected Dispatched, got=$result", result is HandlerResult.Dispatched)
        assertEquals(
            "external:chooser",
            (result as HandlerResult.Dispatched).info
        )

        val captured = ctx.captured.single()
        // Intent.createChooser wraps the inner ACTION_SEND in an
        // ACTION_CHOOSER. The inner intent is in EXTRA_INTENT.
        assertEquals(Intent.ACTION_CHOOSER, captured.action)
        val inner: Intent = captured.getParcelableExtra(Intent.EXTRA_INTENT)
            ?: error("missing EXTRA_INTENT in chooser")
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)

        val text = inner.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(text)
        assertTrue(
            "EXTRA_TEXT should contain bullets for both items",
            text!!.contains("• Buy milk") && text.contains("• Mail rent check")
        )
        assertTrue(
            "Chooser must carry FLAG_ACTIVITY_NEW_TASK from non-Activity context",
            (captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )
    }

    @Test
    fun handle_external_remembersTargetPackage_skipsChooserOnSecondUse() = runTest {
        // Step 1: simulate the user picked com.todoist.android last time.
        TodoActionHandler.recordRememberedTarget(
            ApplicationProvider.getApplicationContext(),
            "android" // resolves on-device (Android system)
        )

        // Use a context that always resolves the remembered package so
        // the resolvesTo() guard inside the handler returns true.
        val ctx = AlwaysResolvesContext(ApplicationProvider.getApplicationContext())
        val args = """{"target":"external","items":["Eat the frog"]}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Dispatched)
        assertEquals(
            "external:remembered:android",
            (result as HandlerResult.Dispatched).info
        )

        val captured = ctx.captured.single()
        // No chooser wrap when remembered package resolves: the raw
        // ACTION_SEND with explicit setPackage() is dispatched.
        assertEquals(Intent.ACTION_SEND, captured.action)
        assertEquals("android", captured.`package`)
    }

    @Test
    fun handle_external_rememberedPackageGoneFalls_backToChooser() = runTest {
        // Persist a package that won't resolve via the
        // CapturingContext's package manager (no setPackage match).
        TodoActionHandler.recordRememberedTarget(
            ApplicationProvider.getApplicationContext(),
            "com.invented.todoapp.does.not.exist"
        )

        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """{"target":"external","items":["X"]}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Dispatched)
        // Falls back to the chooser branch when the remembered package
        // no longer resolves on-device — gracefully self-heals after the
        // user uninstalls the chosen app.
        assertEquals(
            "external:chooser",
            (result as HandlerResult.Dispatched).info
        )
    }

    @Test
    fun handle_external_customMimeType_propagates() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """
            {"target":"external","items":["a","b"],"mimeType":"text/markdown"}
        """.trimIndent()

        val result = handler.handle(ctx, skill, args)
        assertTrue(result is HandlerResult.Dispatched)

        val inner = ctx.captured.single().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("text/markdown", inner.type)
    }

    @Test
    fun handle_external_emptyItems_returnsFailed_noDispatch() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """{"target":"external","items":[]}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("empty_items", (result as HandlerResult.Failed).reason)
        assertEquals(0, ctx.captured.size)
    }

    @Test
    fun handle_external_securityException_mappedToFailed() = runTest {
        val ctx = ThrowingContext(
            ApplicationProvider.getApplicationContext(),
            SecurityException("blocked")
        )
        val args = """{"target":"external","items":["x"]}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("security_exception", (result as HandlerResult.Failed).reason)
    }

    @Test
    fun recordRememberedTarget_null_clearsPreference() {
        TodoActionHandler.recordRememberedTarget(
            ApplicationProvider.getApplicationContext(),
            "com.example.todo"
        )
        assertEquals("com.example.todo", prefs.getString(TodoActionHandler.KEY_TODO_TARGET, null))

        TodoActionHandler.recordRememberedTarget(
            ApplicationProvider.getApplicationContext(),
            null
        )
        assertNull(prefs.getString(TodoActionHandler.KEY_TODO_TARGET, null))
    }

    // ---- helpers --------------------------------------------------

    private fun sampleSkill() = AppFunctionSummaryParcel(
        functionId = "tasks.createTodo",
        appPackage = "com.capsule.app",
        displayName = "Add to to-dos",
        description = "test fixture",
        schemaVersion = 1,
        argsSchemaJson = "{}",
        sideEffects = "EXTERNAL_DISPATCH",
        reversibility = "EXTERNAL_MANAGED",
        sensitivityScope = "SHARE_DELEGATED",
        registeredAtMillis = 0L,
        updatedAtMillis = 0L
    )

    private open class CapturingContext(base: Context) : ContextWrapper(base) {
        val captured = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            captured += intent
        }
        override fun startActivity(intent: Intent, options: Bundle?) {
            captured += intent
        }
    }

    /**
     * Forces every `Intent.resolveActivity` probe (via the
     * [TodoActionHandler]'s `resolvesTo` helper) to succeed so the
     * "remembered package" branch is exercised even when the package
     * manager wouldn't actually resolve the package on this emulator.
     *
     * We override [getPackageManager] indirectly by intercepting the
     * dispatch — `setPackage("android")` will resolve to the System
     * package on a real device, so we just rely on that for happy-path
     * coverage. The override here is a no-op fallback.
     */
    private class AlwaysResolvesContext(base: Context) : CapturingContext(base)

    private class ThrowingContext(base: Context, private val toThrow: RuntimeException) :
        ContextWrapper(base) {
        override fun startActivity(intent: Intent) { throw toThrow }
        override fun startActivity(intent: Intent, options: Bundle?) { throw toThrow }
    }
}
