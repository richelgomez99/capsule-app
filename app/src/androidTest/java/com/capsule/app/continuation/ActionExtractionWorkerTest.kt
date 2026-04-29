package com.capsule.app.continuation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.capsule.app.ai.extract.ActionExtractionWorker
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/**
 * T036 — outcome → [ListenableWorker.Result] mapping for
 * [ActionExtractionWorker]. Verifies the contract from
 * `specs/003-orbit-actions/contracts/action-extraction-contract.md` §5
 * and the [ContinuationEngine.MAX_ATTEMPTS]=3 backoff cap.
 *
 * Fakes the `:ml` binder via the worker's `repositoryBinder` test seam
 * (same pattern as [UrlHydrateWorkerTest]'s `gatewayBinder`). We use a
 * JDK [Proxy] instead of subclassing `IEnvelopeRepository.Stub` because
 * the worker only invokes one method (`extractActionsForEnvelope`); a
 * Proxy keeps the fake to a single closure rather than reimplementing
 * the entire AIDL surface.
 *
 * Coverage matrix:
 *  - `PROPOSED:N` → success
 *  - `NO_CANDIDATES` → success
 *  - `SKIPPED:non_regular_kind` → success (terminal — no retry)
 *  - `FAILED:nano_timeout` at attempts 0,1 → retry; at attempt 2 → failure
 *  - `UNAVAILABLE` → failure (no retry — registry empty is structural)
 *  - binder-throws → retry while below cap, failure at cap
 *  - binder unavailable (`null` from seam) → retry while below cap, failure at cap
 *  - missing `KEY_ENVELOPE_ID` → failure
 *  - unrecognised outcome string → failure (defensive — contract-violation gate)
 */
@RunWith(AndroidJUnit4::class)
class ActionExtractionWorkerTest {

    private lateinit var context: Context
    private val originalBinder = ActionExtractionWorker.repositoryBinder

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        ActionExtractionWorker.repositoryBinder = originalBinder
    }

    // ---- success-class outcomes ------------------------------------------

    @Test
    fun proposed_outcome_returnsSuccess() = runTest {
        useFakeRepoReturning("PROPOSED:2")
        assertEquals(ListenableWorker.Result.success(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun proposed_zero_count_stillSuccess() = runTest {
        // The contract allows `PROPOSED:0` even though the extractor
        // shouldn't emit it (NoCandidates would be the natural outcome).
        // The worker treats any `PROPOSED:` prefix as success because the
        // unit-of-work completed without a recoverable error.
        useFakeRepoReturning("PROPOSED:0")
        assertEquals(ListenableWorker.Result.success(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun no_candidates_returnsSuccess() = runTest {
        useFakeRepoReturning("NO_CANDIDATES")
        assertEquals(ListenableWorker.Result.success(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun skipped_non_regular_kind_returnsSuccess() = runTest {
        useFakeRepoReturning("SKIPPED:non_regular_kind")
        assertEquals(ListenableWorker.Result.success(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun skipped_sensitivity_changed_returnsSuccess() = runTest {
        useFakeRepoReturning("SKIPPED:sensitivity_changed")
        assertEquals(ListenableWorker.Result.success(), buildWorker(attempts = 0).doWork())
    }

    // ---- retry-class outcomes --------------------------------------------

    @Test
    fun failed_outcome_retries_below_cap_thenFailsAtCap() = runTest {
        useFakeRepoReturning("FAILED:nano_timeout")
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 1).doWork())
        // attempt #3 (index 2) is the last; +1 == MAX_ATTEMPTS=3 → failure.
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 2).doWork())
    }

    @Test
    fun binder_throws_retries_below_cap_thenFailsAtCap() = runTest {
        useThrowingRepo(RuntimeException("simulated binder throw"))
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 1).doWork())
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 2).doWork())
    }

    @Test
    fun binder_unavailable_retries_below_cap_thenFailsAtCap() = runTest {
        ActionExtractionWorker.repositoryBinder = { null }
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 1).doWork())
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 2).doWork())
    }

    // ---- terminal-failure outcomes (no retry) ----------------------------

    @Test
    fun unavailable_outcome_returnsFailure_immediately() = runTest {
        // `UNAVAILABLE` means the registry side reports a structural
        // problem (e.g., schema not registered, repository ctor missing
        // ActionExtractor). Retrying won't help — fail immediately.
        useFakeRepoReturning("UNAVAILABLE")
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun unrecognised_outcome_returnsFailure() = runTest {
        // Defensive gate: if `:ml` ever returns an outcome string not in
        // the {PROPOSED, NO_CANDIDATES, SKIPPED, FAILED, UNAVAILABLE}
        // grammar, the worker fails terminally — surfacing the
        // contract-violation rather than retrying forever.
        useFakeRepoReturning("WAT:something_unexpected")
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun missing_envelopeId_input_returnsFailure() = runTest {
        useFakeRepoReturning("PROPOSED:1") // shouldn't be reached
        val worker = TestListenableWorkerBuilder<ActionExtractionWorker>(context)
            .setInputData(Data.EMPTY)
            .setRunAttemptCount(0)
            .build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    // ---- helpers ---------------------------------------------------------

    private fun buildWorker(attempts: Int): ActionExtractionWorker {
        val input = Data.Builder()
            .putString(ActionExtractionWorker.KEY_ENVELOPE_ID, "env-1")
            .build()
        return TestListenableWorkerBuilder<ActionExtractionWorker>(context)
            .setInputData(input)
            .setRunAttemptCount(attempts)
            .build()
    }

    private fun useFakeRepoReturning(outcome: String) {
        val proxy = fakeRepo { method, _ ->
            when (method.name) {
                "extractActionsForEnvelope" -> outcome
                "asBinder" -> null
                else -> error("worker invoked unexpected AIDL method: ${method.name}")
            }
        }
        ActionExtractionWorker.repositoryBinder = { proxy }
    }

    private fun useThrowingRepo(toThrow: Throwable) {
        val proxy = fakeRepo { method, _ ->
            when (method.name) {
                "extractActionsForEnvelope" -> throw toThrow
                "asBinder" -> null
                else -> error("worker invoked unexpected AIDL method: ${method.name}")
            }
        }
        ActionExtractionWorker.repositoryBinder = { proxy }
    }

    private fun fakeRepo(
        handle: (java.lang.reflect.Method, Array<Any?>?) -> Any?
    ): IEnvelopeRepository {
        return Proxy.newProxyInstance(
            IEnvelopeRepository::class.java.classLoader,
            arrayOf(IEnvelopeRepository::class.java)
        ) { _, method, args -> handle(method, args) } as IEnvelopeRepository
    }
}
