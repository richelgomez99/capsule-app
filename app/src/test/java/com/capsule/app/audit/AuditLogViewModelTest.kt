package com.capsule.app.audit

import com.capsule.app.data.ipc.AuditEntryParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AuditLogViewModelTest {

    private fun entry(id: String, action: String, at: Long = 0L) = AuditEntryParcel(
        id = id,
        atMillis = at,
        action = action,
        description = "desc-$id",
        envelopeId = null,
        extraJson = null
    )

    private class FakeProvider(
        private val byDate: MutableMap<String, List<AuditEntryParcel>>
    ) : AuditLogProvider {
        val calls = mutableListOf<String>()
        override suspend fun entriesForDay(isoDate: String): List<AuditEntryParcel> {
            calls += isoDate
            return byDate[isoDate].orEmpty()
        }
    }

    @Test
    fun `loads today on init and groups action counts`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val today = LocalDate.of(2025, 1, 15)
        val provider = FakeProvider(
            mutableMapOf(
                "2025-01-15" to listOf(
                    entry("a", "ENVELOPE_CREATED"),
                    entry("b", "ENVELOPE_CREATED"),
                    entry("c", "NETWORK_FETCH")
                )
            )
        )

        val vm = AuditLogViewModel(provider, today = { today }, scopeOverride = scope)
        scope.advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s is AuditLogUiState.Ready)
        s as AuditLogUiState.Ready
        assertEquals(today, s.selectedDay)
        assertEquals(3, s.entries.size)
        assertEquals(2, s.groupCounts["ENVELOPE_CREATED"])
        assertEquals(1, s.groupCounts["NETWORK_FETCH"])
        assertEquals(listOf("2025-01-15"), provider.calls)
    }

    @Test
    fun `selectDay reloads for requested date`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val today = LocalDate.of(2025, 1, 15)
        val provider = FakeProvider(
            mutableMapOf(
                "2025-01-15" to emptyList(),
                "2025-01-14" to listOf(entry("x", "CONTINUATION_COMPLETED"))
            )
        )
        val vm = AuditLogViewModel(provider, today = { today }, scopeOverride = scope)
        scope.advanceUntilIdle()

        vm.selectDay(LocalDate.of(2025, 1, 14))
        scope.advanceUntilIdle()

        val s = vm.state.value as AuditLogUiState.Ready
        assertEquals(LocalDate.of(2025, 1, 14), s.selectedDay)
        assertEquals(1, s.entries.size)
        assertTrue(provider.calls.contains("2025-01-14"))
    }

    @Test
    fun `provider failure surfaces Error`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val provider = object : AuditLogProvider {
            override suspend fun entriesForDay(isoDate: String): List<AuditEntryParcel> =
                throw IllegalStateException("boom")
        }
        val vm = AuditLogViewModel(provider, today = { LocalDate.now() }, scopeOverride = scope)
        scope.advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s is AuditLogUiState.Error)
        assertEquals("boom", (s as AuditLogUiState.Error).message)
    }
}
