package com.capsule.app.ui.primitives

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIdentityResolverTest {
    @Test
    fun youtubeUrlCopiedFromBrowser_usesYouTubeGlyph() {
        val glyph = SourceIdentityResolver.glyphKind(
            textContent = "https://www.youtube.com/watch?v=abc123&utm_source=share",
            canonicalUrl = null,
            sourceAppLabel = "Brave",
            appCategory = "BROWSER",
        )

        assertEquals(SourceGlyphKind.youtube, glyph)
    }

    @Test
    fun shortYouTubeUrlCopiedFromMessages_usesYouTubeGlyph() {
        val glyph = SourceIdentityResolver.glyphKind(
            textContent = "watch this https://youtu.be/abc123",
            canonicalUrl = null,
            sourceAppLabel = "Messages",
            appCategory = "MESSAGING",
        )

        assertEquals(SourceGlyphKind.youtube, glyph)
    }

    @Test
    fun nonYouTubeBrowserCapture_usesBrowserGlyph() {
        val glyph = SourceIdentityResolver.glyphKind(
            textContent = "https://example.com/article",
            canonicalUrl = null,
            sourceAppLabel = "Brave",
            appCategory = "BROWSER",
        )

        assertEquals(SourceGlyphKind.chrome, glyph)
    }

    @Test
    fun youtubeDetectionCoversMobileAndNoCookieHosts() {
        assertTrue(SourceIdentityResolver.containsYouTubeUrl("https://m.youtube.com/shorts/abc123"))
        assertTrue(SourceIdentityResolver.containsYouTubeUrl("https://www.youtube-nocookie.com/embed/abc123"))
        assertFalse(SourceIdentityResolver.containsYouTubeUrl("https://notyoutube.com/watch?v=abc123"))
    }

    @Test
    fun youtubeDetectionCoversSchemeLessSharedLinks() {
        assertTrue(SourceIdentityResolver.containsYouTubeUrl("www.youtube.com/watch?v=abc123"))
        assertTrue(SourceIdentityResolver.containsYouTubeUrl("youtu.be/abc123"))
        assertTrue(SourceIdentityResolver.containsYouTubeUrl("music.youtube.com/watch?v=abc123"))
    }
}