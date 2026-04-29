package com.capsule.app.net

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * T062 — wraps jsoup + Readability4J. Returns `(title, readableHtml)` where
 * `readableHtml` is cleaned via jsoup's basic safelist (strips `<script>`,
 * `<style>`, event handlers, iframes, etc.) and truncated to 200 KB.
 *
 * Runs inside :net so no cross-process IPC is needed for the extraction.
 * Returns `(null, null)` on any failure — callers map that to errorKind="unknown"
 * or simply a best-effort empty body.
 */
class ReadabilityExtractor(
    private val maxReadableBytes: Int = 200 * 1024,
) {
    data class Extracted(val title: String?, val readableHtml: String?)

    fun extract(finalUrl: String, rawHtml: String): Extracted {
        if (rawHtml.isBlank()) return Extracted(null, null)
        return try {
            val r4j = Readability4J(finalUrl, rawHtml)
            val article = r4j.parse()
            val title = article.title?.takeIf { it.isNotBlank() }
                ?: Jsoup.parse(rawHtml).title().takeIf { it.isNotBlank() }
            val dirtyHtml = article.articleContent?.html().orEmpty()
            val sanitised = sanitise(dirtyHtml.ifBlank { rawHtml })
            Extracted(title, sanitised.truncateUtf8(maxReadableBytes))
        } catch (_: Exception) {
            Extracted(null, null)
        }
    }

    private fun sanitise(html: String): String {
        val safelist = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "figure", "figcaption", "article", "section")
        return Jsoup.clean(html, safelist)
    }

    private fun String.truncateUtf8(maxBytes: Int): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return this
        // Truncate on a valid UTF-8 boundary by decoding with REPLACE semantics.
        val trimmed = String(bytes, 0, maxBytes, Charsets.UTF_8)
        // String() may include a trailing replacement char for a split codepoint; drop it.
        return if (trimmed.isNotEmpty() && trimmed.last() == '\uFFFD') trimmed.dropLast(1) else trimmed
    }
}
