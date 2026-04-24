package com.capsule.app.capture

/**
 * Synchronous regex-based PII/secret redaction pass applied to captured text
 * in the `:capture` process **before** it is placed into an
 * `IntentEnvelopeDraftParcel` (T037a).
 *
 * Constitution Principle V (Data Minimization By Default) + Principle VIII
 * (Collect Only What You Use): redact first, then persist. Only the redacted
 * text ever leaves `:capture`.
 *
 * Each match is replaced with `[REDACTED_<TYPE>]`. Returns a [ScrubResult]
 * carrying the scrubbed text plus a count-only breakdown by type so the
 * audit log can record that a capture was scrubbed (via `CAPTURE_SCRUBBED`)
 * without leaking *what* was scrubbed.
 *
 * False-positive management: patterns are intentionally conservative. Luhn
 * validation is applied to credit-card candidates to avoid redacting build
 * hashes or version numbers that happen to look card-shaped.
 */
object SensitivityScrubber {

    data class ScrubResult(
        val scrubbedText: String,
        val redactionCountByType: Map<String, Int>
    ) {
        val isScrubbed: Boolean get() = redactionCountByType.isNotEmpty()
    }

    /**
     * Order matters: more specific patterns (provider-prefixed keys) run
     * before generic ones (JWT, IP, email) so a GitHub PAT is not
     * misclassified as a generic token.
     */
    private data class Rule(
        val type: String,
        val regex: Regex,
        val validate: ((MatchResult) -> Boolean)? = null
    )

    private val rules: List<Rule> = listOf(
        // AWS access key ids (AKIA...) and secret keys (40-char base64ish).
        Rule("AWS_ACCESS_KEY", Regex("""\bAKIA[0-9A-Z]{16}\b""")),
        Rule("AWS_SECRET_KEY", Regex("""(?<![A-Za-z0-9/+=])[A-Za-z0-9/+=]{40}(?![A-Za-z0-9/+=])"""),
            validate = { m ->
                // Require at least one digit AND one letter AND one slash/plus
                // to avoid matching git hashes or base64 image blobs.
                val s = m.value
                s.any(Char::isDigit) && s.any(Char::isLetter) && (s.contains('/') || s.contains('+'))
            }),
        // GitHub personal access tokens.
        Rule("GITHUB_TOKEN", Regex("""\bghp_[A-Za-z0-9]{36,255}\b""")),
        Rule("GITHUB_TOKEN", Regex("""\bgithub_pat_[A-Za-z0-9_]{20,}\b""")),
        // OpenAI / Anthropic keys.
        Rule("ANTHROPIC_KEY", Regex("""\bsk-ant-[A-Za-z0-9\-_]{20,}\b""")),
        Rule("OPENAI_KEY", Regex("""\bsk-[A-Za-z0-9]{20,}\b""")),
        // JWT: three dot-separated base64url segments.
        Rule("JWT", Regex("""\beyJ[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}\.[A-Za-z0-9_\-]{5,}\b""")),
        // Credit card (Luhn validated). 13–19 digits, optional spaces/dashes.
        Rule("CREDIT_CARD", Regex("""\b(?:\d[ -]?){12,18}\d\b"""), validate = { m ->
            passesLuhn(m.value.filter(Char::isDigit))
        }),
        // US SSN.
        Rule("SSN", Regex("""\b\d{3}-\d{2}-\d{4}\b""")),
        // Email.
        Rule("EMAIL", Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")),
        // US phone number.
        Rule("PHONE", Regex("""(?<!\d)\+?1?[-. ]?\(?\d{3}\)?[-. ]?\d{3}[-. ]?\d{4}(?!\d)""")),
        // IPv4.
        Rule("IPV4", Regex("""\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\b"""))
    )

    fun scrub(input: String): ScrubResult {
        if (input.isEmpty()) return ScrubResult(input, emptyMap())

        val counts = mutableMapOf<String, Int>()
        var working = input

        for (rule in rules) {
            working = rule.regex.replace(working) { match ->
                if (rule.validate?.invoke(match) == false) {
                    match.value
                } else {
                    counts.merge(rule.type, 1, Int::plus)
                    "[REDACTED_${rule.type}]"
                }
            }
        }

        return ScrubResult(working, counts.toMap())
    }

    private fun passesLuhn(digits: String): Boolean {
        if (digits.length < 13 || digits.length > 19) return false
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }
}
