package com.capsule.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    private val validator = UrlValidator()

    private fun invalid(url: String): UrlValidator.Validation.Invalid {
        val v = validator.validate(url)
        assertTrue("expected Invalid for $url but got $v", v is UrlValidator.Validation.Invalid)
        return v as UrlValidator.Validation.Invalid
    }

    private fun valid(url: String): UrlValidator.Validation.Valid {
        val v = validator.validate(url)
        assertTrue("expected Valid for $url but got $v", v is UrlValidator.Validation.Valid)
        return v as UrlValidator.Validation.Valid
    }

    @Test fun blank_isInvalidUrl() = assertEquals("invalid_url", invalid("").errorKind)

    @Test fun whitespace_isInvalidUrl() = assertEquals("invalid_url", invalid("   ").errorKind)

    @Test fun malformed_isInvalidUrl() = assertEquals("invalid_url", invalid("ht tp://foo").errorKind)

    @Test fun http_isNotHttps() = assertEquals("not_https", invalid("http://example.com/").errorKind)

    @Test fun ftp_isNotHttps() = assertEquals("not_https", invalid("ftp://example.com/").errorKind)

    @Test fun localhost_isBlocked() = assertEquals("blocked_host", invalid("https://localhost/").errorKind)

    @Test fun loopbackIp_isBlocked() = assertEquals("blocked_host", invalid("https://127.0.0.1/").errorKind)

    @Test fun rfc1918_isBlocked() {
        assertEquals("blocked_host", invalid("https://10.0.0.1/").errorKind)
        assertEquals("blocked_host", invalid("https://192.168.1.1/").errorKind)
        assertEquals("blocked_host", invalid("https://172.16.0.1/").errorKind)
    }

    @Test fun linkLocal_isBlocked() =
        assertEquals("blocked_host", invalid("https://169.254.1.1/").errorKind)

    @Test fun cgnat_isBlocked() =
        assertEquals("blocked_host", invalid("https://100.64.0.1/").errorKind)

    @Test fun onion_isBlocked() =
        assertEquals("blocked_host", invalid("https://example.onion/").errorKind)

    @Test fun nonStandardPort_isBlockedScheme() =
        assertEquals("blocked_scheme", invalid("https://example.com:8080/").errorKind)

    @Test fun port443_isValid() {
        valid("https://example.com:443/")
    }

    @Test fun defaultHttpsPort_isValid() {
        valid("https://example.com/path")
    }

    @Test fun typicalPublicHost_isValid() {
        val v = valid("https://example.com/foo?bar=1")
        assertEquals("example.com", v.host)
    }

    @Test fun lax_validator_acceptsHttpForTests() {
        val lax = UrlValidator(requireHttps = false)
        val v = lax.validate("http://example.com/")
        assertTrue(v is UrlValidator.Validation.Valid)
    }
}
