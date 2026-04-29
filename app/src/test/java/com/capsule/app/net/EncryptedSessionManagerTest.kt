package com.capsule.app.net

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.ExperimentalTime

/**
 * Spec 014 T014-019b — unit tests for [EncryptedSessionManager] using the
 * [EncryptedSessionManager.SessionStorage] seam (in-memory fake) so we don't
 * need the Android Keystore. Robolectric is intentionally NOT on the
 * project's classpath — see runbook for the SessionStorage fallback.
 */
@OptIn(ExperimentalTime::class)
class EncryptedSessionManagerTest {

    private class FakeStorage : EncryptedSessionManager.SessionStorage {
        var slot: String? = null
        override fun put(json: String) { slot = json }
        override fun get(): String? = slot
        override fun clear() { slot = null }
    }

    private fun sampleSession(): UserSession = UserSession(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        providerRefreshToken = null,
        providerToken = null,
        expiresIn = 3_600L,
        tokenType = "bearer",
        user = null,
        type = "",
    )

    @Test
    fun `save then load round-trips a session`() = runTest {
        val storage = FakeStorage()
        val manager = EncryptedSessionManager(storage = storage)

        val original = sampleSession()
        manager.saveSession(original)

        assertNotNull("storage should hold encoded JSON", storage.slot)
        val loaded = manager.loadSession()
        assertEquals(original.accessToken, loaded.accessToken)
        assertEquals(original.refreshToken, loaded.refreshToken)
        assertEquals(original.expiresIn, loaded.expiresIn)
        assertEquals(original.tokenType, loaded.tokenType)
    }

    @Test
    fun `loadSessionOrNull returns null on fresh storage`() = runTest {
        val manager = EncryptedSessionManager(storage = FakeStorage())
        assertNull(manager.loadSessionOrNull())
    }

    @Test
    fun `loadSession throws on fresh storage`() = runTest {
        val manager = EncryptedSessionManager(storage = FakeStorage())
        try {
            manager.loadSession()
            fail("expected loadSession() to throw when no session is stored")
        } catch (expected: IllegalStateException) {
            assertTrue(
                "error message should mention missing session",
                expected.message.orEmpty().contains("session", ignoreCase = true),
            )
        }
    }

    @Test
    fun `delete clears the stored session`() = runTest {
        val storage = FakeStorage()
        val manager = EncryptedSessionManager(storage = storage)

        manager.saveSession(sampleSession())
        assertNotNull(storage.slot)

        manager.deleteSession()
        assertNull(storage.slot)
        assertNull(manager.loadSessionOrNull())
    }
}
