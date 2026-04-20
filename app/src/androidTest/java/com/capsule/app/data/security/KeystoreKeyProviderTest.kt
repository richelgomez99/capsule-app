package com.capsule.app.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented contract test: KeystoreKeyProvider generates and persists
 * a consistent 32-byte passphrase across calls.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreKeyProviderTest {

    @Test
    fun passphraseIs32Bytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        assertEquals(32, passphrase.size)
    }

    @Test
    fun passphraseIsStableAcrossCalls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val second = KeystoreKeyProvider.getOrCreatePassphrase(context)
        assertArrayEquals(first, second)
    }

    @Test
    fun passphraseContainsNonZeroBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        // Extremely unlikely all 32 bytes are zero
        assertTrue("Passphrase should contain non-zero bytes", passphrase.any { it != 0.toByte() })
    }
}
