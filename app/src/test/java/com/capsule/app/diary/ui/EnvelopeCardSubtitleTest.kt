package com.capsule.app.diary.ui

import com.capsule.app.data.ipc.EnvelopeViewParcel
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeCardSubtitleTest {

    @Test
    fun buildSubtitle_prefersSourceAppLabelOverCategory() {
        val subtitle = buildSubtitle(envelope(appCategory = "VIDEO", sourceAppLabel = "YouTube"))

        assertTrue(subtitle.startsWith("from YouTube"))
    }

    @Test
    fun buildSubtitle_fallsBackToCategoryWhenSourceAppLabelMissing() {
        val subtitle = buildSubtitle(envelope(appCategory = "VIDEO", sourceAppLabel = null))

        assertTrue(subtitle.startsWith("from Video"))
    }

    private fun envelope(
        appCategory: String,
        sourceAppLabel: String?
    ): EnvelopeViewParcel = EnvelopeViewParcel(
        id = "env-1",
        contentType = "TEXT",
        textContent = "https://youtube.com/watch?v=abc",
        imageUri = null,
        intent = "REFERENCE",
        intentSource = "AUTO_LOCAL",
        createdAtMillis = 1_745_164_800_000L,
        dayLocal = "2025-04-20",
        isArchived = false,
        title = null,
        domain = null,
        excerpt = null,
        summary = null,
        appCategory = appCategory,
        activityState = "UNKNOWN",
        hourLocal = 0,
        dayOfWeekLocal = 7,
        sourceAppLabel = sourceAppLabel
    )
}
