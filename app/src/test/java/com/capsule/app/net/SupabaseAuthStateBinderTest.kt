package com.capsule.app.net

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseAuthStateBinderTest {

    @Test
    fun returnsTokenWhenProviderYieldsNonBlank() = runTest {
        val binder = SupabaseAuthStateBinder { "eyJhbGciOiJIUzI1NiJ9.payload.sig" }
        assertEquals("eyJhbGciOiJIUzI1NiJ9.payload.sig", binder.currentJwt())
    }

    @Test
    fun returnsNullWhenProviderYieldsNull() = runTest {
        val binder = SupabaseAuthStateBinder { null }
        assertNull(binder.currentJwt())
    }

    @Test
    fun returnsNullWhenProviderYieldsBlank() = runTest {
        val binder = SupabaseAuthStateBinder { "   " }
        assertNull(binder.currentJwt())
    }

    @Test
    fun returnsNullWhenProviderThrows() = runTest {
        val binder = SupabaseAuthStateBinder { error("session-store boom") }
        assertNull(binder.currentJwt())
    }
}
