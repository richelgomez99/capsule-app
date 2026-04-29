package com.capsule.app.ai

/**
 * T048 — which locales are supported by the v1 on-device Nano for header
 * generation. Captures outside these locales short-circuit to English
 * generation or the structured fallback per tasks.md T048.
 *
 * Exposed as a single mutable-free `object` so the set can be extended in
 * a later slice without touching call sites.
 */
object LocaleSupport {
    /**
     * Language tags (BCP-47 primary subtag) that v1 Nano generates
     * headers for. Kept intentionally small — expanding is a decision
     * that should happen alongside eval on real traffic.
     */
    val NANO_SUPPORTED_LOCALES: Set<String> = setOf("en")

    /** True iff [languageTag] (e.g., "en", "en-US", "fr-FR") is supported. */
    fun isSupported(languageTag: String): Boolean {
        val primary = languageTag.substringBefore('-').lowercase()
        return primary in NANO_SUPPORTED_LOCALES
    }
}
