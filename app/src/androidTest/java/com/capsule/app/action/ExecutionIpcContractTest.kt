package com.capsule.app.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.ipc.IActionExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T040 — IPC contract for [ActionExecutorService] per
 * `specs/003-orbit-actions/contracts/action-execution-contract.md` §2.
 *
 * Three invariants:
 *  1. **Binding reachability** — `:ui` (this process, in-test) can resolve
 *     and bind the executor service via `BIND_ACTION_EXECUTOR` and obtain
 *     a working [IActionExecutor] proxy.
 *  2. **Foreign-package isolation** — the manifest declaration carries
 *     `android:exported="false"` so any foreign package that synthesises
 *     the same intent action gets `SecurityException`. Verified
 *     structurally via [PackageManager.getServiceInfo] rather than
 *     attempted-bind (a separate test-APK install in a foreign UID is
 *     out of scope for v1.1; the manifest invariant is the gate).
 *  3. **Round-trip latency** — `cancelWithinUndoWindow` IPC round-trip
 *     for an unknown executionId (a pure binder round-trip with no DB
 *     writes; the call returns `false` immediately) MUST stay under
 *     the 200ms p99 budget across 50 calls.
 *
 * The full `execute(...)` happy-path latency is intentionally NOT
 * measured here — it depends on the `:ml` binder being bound and the
 * encrypted Room DB being unlocked, which is more aptly the territory
 * of T101 (Polish phase end-to-end perf gate).
 */
@RunWith(AndroidJUnit4::class)
class ExecutionIpcContractTest {

    private lateinit var context: Context
    private var connection: ServiceConnection? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        ActionExecutorService.clearExpiredForTesting()
    }

    @Test
    fun manifestDeclaration_isNotExported() {
        // The whole no-foreign-binder guarantee rests on this single
        // attribute. Catch a regression at compile-test time rather than
        // discover it post-ship.
        val component = ComponentName(
            context.packageName,
            "com.capsule.app.action.ActionExecutorService"
        )
        val info = context.packageManager.getServiceInfo(component, 0)
        assertFalse(
            "ActionExecutorService MUST declare android:exported=\"false\" " +
                "(action-execution-contract.md §2). Foreign packages binding " +
                "the service would bypass the sensitivity-scope re-check.",
            info.exported
        )
    }

    @Test
    fun envelopeRepositoryService_isAlsoNotExported() {
        // Defence-in-depth: the executor's correctness depends on
        // EnvelopeRepositoryService also being process-internal. If a
        // future change accidentally exports it, foreign apps could hit
        // recordActionInvocation directly.
        val component = ComponentName(
            context.packageName,
            "com.capsule.app.data.ipc.EnvelopeRepositoryService"
        )
        val info = context.packageManager.getServiceInfo(component, 0)
        assertFalse(info.exported)
    }

    @Test
    fun bindActionExecutor_returnsWorkingProxy() = runBlocking {
        val executor = withTimeout(BIND_TIMEOUT_MS) { bindExecutor() }
        assertNotNull("bindService must yield a non-null proxy", executor)
        // Liveness check: the binder is alive and pingable. The IBinder
        // contract guarantees pingBinder() returns true for a live remote.
        assertTrue(
            "binder must be alive immediately after bind",
            executor!!.asBinder().pingBinder()
        )
    }

    @Test
    fun cancelWithinUndoWindow_unknownId_returnsFalse_underLatencyBudget() = runBlocking {
        val executor = withTimeout(BIND_TIMEOUT_MS) { bindExecutor() }!!

        // Warm up the binder thread + JIT so the first call's setup cost
        // doesn't pollute the percentile.
        repeat(WARMUP_ITERATIONS) {
            executor.cancelWithinUndoWindow("warmup-$it")
        }

        val samples = LongArray(SAMPLE_ITERATIONS)
        for (i in 0 until SAMPLE_ITERATIONS) {
            val started = System.nanoTime()
            val result = executor.cancelWithinUndoWindow("nonexistent-id-$i")
            samples[i] = (System.nanoTime() - started) / 1_000_000L
            // Contract: unknown id → false (not in pendingUndo, not expired).
            assertFalse("unknown executionId must not report cancelled", result)
        }

        samples.sort()
        val p50 = samples[(SAMPLE_ITERATIONS * 0.50).toInt()]
        val p99 = samples[(SAMPLE_ITERATIONS * 0.99).toInt().coerceAtMost(SAMPLE_ITERATIONS - 1)]
        val max = samples.last()

        assertTrue(
            "cancelWithinUndoWindow IPC p99=${p99}ms exceeded 200ms budget " +
                "(p50=${p50}ms, max=${max}ms over $SAMPLE_ITERATIONS samples). " +
                "See action-execution-contract.md §2 latency budget.",
            p99 < 200L
        )
    }

    // ---- helpers ---------------------------------------------------------

    private suspend fun bindExecutor(): IActionExecutor? {
        val deferred = CompletableDeferred<IActionExecutor?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (!deferred.isCompleted) {
                    deferred.complete(IActionExecutor.Stub.asInterface(service))
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) { /* no-op */ }
        }
        connection = conn

        val intent = Intent(ActionExecutorService.ACTION_BIND_EXECUTOR).apply {
            `package` = context.packageName
        }
        val started = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        assertTrue("bindService must succeed for own-package service", started)

        return deferred.await()
    }

    companion object {
        private const val BIND_TIMEOUT_MS = 5_000L
        private const val WARMUP_ITERATIONS = 5
        private const val SAMPLE_ITERATIONS = 50
    }
}
