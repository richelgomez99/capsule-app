package com.capsule.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T037b — pattern-level coverage for [SensitivityScrubber].
 *
 * Includes true-positive, true-negative, and a benign-article regression
 * fixture that MUST yield zero redactions.
 */
class SensitivityScrubberTest {

    @Test
    fun emptyInput_noRedactions() {
        val result = SensitivityScrubber.scrub("")
        assertFalse(result.isScrubbed)
        assertEquals("", result.scrubbedText)
    }

    @Test
    fun awsAccessKey_isRedacted() {
        val input = "key=AKIAIOSFODNN7EXAMPLE end"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_AWS_ACCESS_KEY]"))
        assertEquals(1, result.redactionCountByType["AWS_ACCESS_KEY"])
    }

    @Test
    fun githubPat_isRedacted() {
        val input = "token ghp_abcdefghijklmnopqrstuvwxyz0123456789 works"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_GITHUB_TOKEN]"))
    }

    @Test
    fun openAiKey_isRedacted() {
        val input = "OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz0123"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_OPENAI_KEY]"))
    }

    @Test
    fun anthropicKey_isRedacted() {
        val input = "auth: sk-ant-api03-abcdefghijklmnop_qrstuvwxyz"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_ANTHROPIC_KEY]"))
    }

    @Test
    fun jwt_isRedacted() {
        val input = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.abcdefGHIJK"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_JWT]"))
    }

    @Test
    fun validCreditCard_isRedacted() {
        // Visa test card: 4111 1111 1111 1111 (Luhn valid)
        val input = "pay with 4111 1111 1111 1111 expires 12/26"
        val result = SensitivityScrubber.scrub(input)
        assertTrue(result.scrubbedText.contains("[REDACTED_CREDIT_CARD]"))
    }

    @Test
    fun sixteenDigitHash_isNotRedactedAsCreditCard() {
        // 1234567890123456 is NOT Luhn-valid — must not be redacted.
        val input = "build hash 1234567890123456 ok"
        val result = SensitivityScrubber.scrub(input)
        assertFalse(result.scrubbedText.contains("[REDACTED_CREDIT_CARD]"))
    }

    @Test
    fun ssn_isRedacted() {
        val result = SensitivityScrubber.scrub("SSN 123-45-6789 on file")
        assertTrue(result.scrubbedText.contains("[REDACTED_SSN]"))
    }

    @Test
    fun email_isRedacted() {
        val result = SensitivityScrubber.scrub("contact alice@example.com here")
        assertTrue(result.scrubbedText.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun phone_isRedacted() {
        val result = SensitivityScrubber.scrub("call +1 (415) 555-1212 now")
        assertTrue(result.scrubbedText.contains("[REDACTED_PHONE]"))
    }

    @Test
    fun ipv4_isRedacted() {
        val result = SensitivityScrubber.scrub("connect to 192.168.1.1 locally")
        assertTrue(result.scrubbedText.contains("[REDACTED_IPV4]"))
    }

    @Test
    fun awsLikeString_thatIsActuallyBuildHash_isNotRedacted() {
        // 40-char hex build hash (no slashes/plus) must NOT be redacted.
        val input = "commit 0123456789abcdef0123456789abcdef01234567 ok"
        val result = SensitivityScrubber.scrub(input)
        assertFalse(result.scrubbedText.contains("[REDACTED_AWS_SECRET_KEY]"))
    }

    @Test
    fun benignNewsArticle_yieldsZeroRedactions() {
        val article = """
            Scientists at the University of Helsinki announced yesterday that
            their new cold-water algae study showed promising results for
            carbon capture at scale. The research team spent three years
            measuring absorption rates across the Baltic Sea.
        """.trimIndent()
        val result = SensitivityScrubber.scrub(article)
        assertFalse(result.isScrubbed)
        assertEquals(article, result.scrubbedText)
    }

    @Test
    fun multiplePatternsInOneInput_allCounted() {
        val input = "reach me at alice@example.com or +1 (415) 555-1212, SSN 123-45-6789"
        val result = SensitivityScrubber.scrub(input)
        assertEquals(1, result.redactionCountByType["EMAIL"])
        assertEquals(1, result.redactionCountByType["PHONE"])
        assertEquals(1, result.redactionCountByType["SSN"])
    }
}
