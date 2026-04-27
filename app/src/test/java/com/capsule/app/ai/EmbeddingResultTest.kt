package com.capsule.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T126 — equality + hashCode + dimensionality-mismatch detection for
 * [EmbeddingResult].
 *
 * Why hand-rolled equals matters: [FloatArray]'s default `equals` is
 * identity-based, so two embeddings with byte-identical vectors would
 * compare unequal. The cluster engine and DAO de-dup paths rely on value
 * equality, so we lock the contract here.
 */
class EmbeddingResultTest {

    @Test
    fun `equals true for identical vectors and metadata`() {
        val a = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        val b = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `equals false when vectors differ even by one float`() {
        val a = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        val b = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.4f), "nano-v4", 3)
        assertNotEquals(a, b)
    }

    @Test
    fun `equals false across different model labels`() {
        val a = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        val b = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v5", 3)
        assertNotEquals(a, b)
    }

    @Test
    fun `equals false across different dimensionality`() {
        val a = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        val b = EmbeddingResult(floatArrayOf(0.1f, 0.2f), "nano-v4", 2)
        assertNotEquals(a, b)
    }

    @Test
    fun `init throws when vector size disagrees with dimensionality`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            EmbeddingResult(floatArrayOf(0.1f, 0.2f), "nano-v4", 3)
        }
        assertTrue(ex.message!!.contains("dimensionality=3"))
    }

    @Test
    fun `equals reflexive symmetric`() {
        val a = EmbeddingResult(floatArrayOf(1f, 2f, 3f), "nano-v4", 3)
        val b = EmbeddingResult(floatArrayOf(1f, 2f, 3f), "nano-v4", 3)
        // reflexive
        assertTrue(a == a)
        // symmetric
        assertTrue(a == b)
        assertTrue(b == a)
        // null-safety
        assertFalse(a.equals(null))
        // type-safety
        assertFalse(a.equals("not an embedding"))
    }

    @Test
    fun `toString redacts vector contents`() {
        val a = EmbeddingResult(floatArrayOf(0.1f, 0.2f, 0.3f), "nano-v4", 3)
        // Vector floats must NOT leak through toString — debug logs are
        // not allowed to carry raw embedding values (Principle II surface).
        val s = a.toString()
        assertTrue(s.contains("nano-v4"))
        assertTrue(s.contains("dimensionality=3"))
        assertFalse(s.contains("0.1"))
        assertFalse(s.contains("0.2"))
    }
}
