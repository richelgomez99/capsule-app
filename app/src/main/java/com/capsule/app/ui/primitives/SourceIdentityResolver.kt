package com.capsule.app.ui.primitives

import java.net.URI
import java.util.Locale

object SourceIdentityResolver {
    fun glyphKind(
        textContent: String?,
        canonicalUrl: String?,
        sourceAppLabel: String?,
        appCategory: String?,
    ): SourceGlyphKind {
        if (containsYouTubeUrl(canonicalUrl) || containsYouTubeUrl(textContent)) {
            return SourceGlyphKind.youtube
        }

        val label = sourceAppLabel.orEmpty().lowercase(Locale.ROOT)
        if ("youtube" in label) return SourceGlyphKind.youtube
        if ("brave" in label || "chrome" in label || "browser" in label) return SourceGlyphKind.chrome
        if ("gmail" in label || "mail" in label) return SourceGlyphKind.gmail
        if ("message" in label || "sms" in label) return SourceGlyphKind.sms

        return when (appCategory.orEmpty().lowercase(Locale.ROOT)) {
            "browser" -> SourceGlyphKind.chrome
            "video" -> SourceGlyphKind.youtube
            "messaging" -> SourceGlyphKind.sms
            "social" -> SourceGlyphKind.twitter
            "reading" -> SourceGlyphKind.nyt
            "work_email" -> SourceGlyphKind.gmail
            else -> SourceGlyphKind.url
        }
    }

    fun containsYouTubeUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.splitToSequence(Regex("\\s+"))
            .map { it.trim(',', '.', ')', ']', '}', '>', '"', '\'') }
            .any { token ->
                val candidate = if (token.contains("://")) token else "https://$token"
                val host = runCatching { URI(candidate).host?.lowercase(Locale.ROOT) }.getOrNull()
                    ?: return@any false
                host == "youtu.be" ||
                    host == "youtube.com" ||
                    host.endsWith(".youtube.com") ||
                    host == "youtube-nocookie.com" ||
                    host.endsWith(".youtube-nocookie.com")
            }
    }
}