package com.capsule.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalUrlHasherTest {

    @Test
    fun hash_isStable_forIdenticalInput() {
        val a = CanonicalUrlHasher.hash("https://example.com/a?x=1")
        val b = CanonicalUrlHasher.hash("https://example.com/a?x=1")
        assertEquals(a, b)
    }

    @Test
    fun hash_lowercasesHost() {
        val a = CanonicalUrlHasher.hash("https://Example.COM/a")
        val b = CanonicalUrlHasher.hash("https://example.com/a")
        assertEquals(a, b)
    }

    @Test
    fun hash_stripsFragment() {
        val a = CanonicalUrlHasher.hash("https://example.com/a#section")
        val b = CanonicalUrlHasher.hash("https://example.com/a")
        assertEquals(a, b)
    }

    @Test
    fun hash_stripsUtmAndFbclidAndGclid() {
        val tracked = CanonicalUrlHasher.hash(
            "https://example.com/a?utm_source=twitter&utm_medium=social&fbclid=xyz&gclid=abc&keep=1"
        )
        val clean = CanonicalUrlHasher.hash("https://example.com/a?keep=1")
        assertEquals(clean, tracked)
    }

    @Test
    fun hash_sortsRemainingQueryParams() {
        val a = CanonicalUrlHasher.hash("https://example.com/a?b=2&a=1&c=3")
        val b = CanonicalUrlHasher.hash("https://example.com/a?a=1&b=2&c=3")
        assertEquals(a, b)
    }

    @Test
    fun hash_stripsTrailingSlash_butPreservesRoot() {
        val a = CanonicalUrlHasher.hash("https://example.com/path/")
        val b = CanonicalUrlHasher.hash("https://example.com/path")
        assertEquals(a, b)

        val root1 = CanonicalUrlHasher.hash("https://example.com/")
        val rootEmpty = CanonicalUrlHasher.hash("https://example.com")
        // Per canonicalize: root "/" is preserved, empty path is stripped.
        assertNotEquals(root1, rootEmpty)
    }

    @Test
    fun hash_differentUrls_produceDifferentHashes() {
        val a = CanonicalUrlHasher.hash("https://example.com/a")
        val b = CanonicalUrlHasher.hash("https://example.com/b")
        assertNotEquals(a, b)
    }

    @Test
    fun hash_isHex64() {
        val h = CanonicalUrlHasher.hash("https://example.com")
        assertEquals(64, h.length)
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun hash_differentSchemes_produceDifferentHashes() {
        val https = CanonicalUrlHasher.hash("https://example.com/a")
        val http = CanonicalUrlHasher.hash("http://example.com/a")
        assertNotEquals(https, http)
    }

    @Test
    fun hash_stripsDefaultPorts() {
        val withPort = CanonicalUrlHasher.hash("https://example.com:443/a")
        val noPort = CanonicalUrlHasher.hash("https://example.com/a")
        assertEquals(noPort, withPort)
    }

    @Test
    fun hash_keepsNonDefaultPort() {
        val withPort = CanonicalUrlHasher.hash("https://example.com:8443/a")
        val noPort = CanonicalUrlHasher.hash("https://example.com/a")
        assertNotEquals(noPort, withPort)
    }

    @Test
    fun hash_malformedUrl_isStableNotCrashing() {
        val a = CanonicalUrlHasher.hash("not a url at all")
        val b = CanonicalUrlHasher.hash("not a url at all")
        assertEquals(a, b)
    }

    @Test
    fun hash_mixedCaseScheme_normalized() {
        val a = CanonicalUrlHasher.hash("HTTPS://example.com/a")
        val b = CanonicalUrlHasher.hash("https://example.com/a")
        assertEquals(a, b)
    }
}
