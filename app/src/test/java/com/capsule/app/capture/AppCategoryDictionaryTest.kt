package com.capsule.app.capture

import com.capsule.app.data.model.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryDictionaryTest {

    @Test
    fun nullPackage_isUnknownSource() {
        assertEquals(AppCategory.UNKNOWN_SOURCE, AppCategoryDictionary.categorize(null))
    }

    @Test
    fun blankPackage_isUnknownSource() {
        assertEquals(AppCategory.UNKNOWN_SOURCE, AppCategoryDictionary.categorize(""))
        assertEquals(AppCategory.UNKNOWN_SOURCE, AppCategoryDictionary.categorize("   "))
    }

    @Test
    fun unknownPackage_isOther_notUnknownSource() {
        // "We saw a foreground app, we just don't have it in the dictionary" != "we couldn't see one"
        assertEquals(AppCategory.OTHER, AppCategoryDictionary.categorize("com.some.random.app"))
    }

    @Test
    fun gmail_isWorkEmail() {
        assertEquals(AppCategory.WORK_EMAIL, AppCategoryDictionary.categorize("com.google.android.gm"))
    }

    @Test
    fun outlook_isWorkEmail() {
        assertEquals(
            AppCategory.WORK_EMAIL,
            AppCategoryDictionary.categorize("com.microsoft.office.outlook")
        )
    }

    @Test
    fun whatsapp_isMessaging() {
        assertEquals(AppCategory.MESSAGING, AppCategoryDictionary.categorize("com.whatsapp"))
    }

    @Test
    fun signal_isMessaging() {
        assertEquals(
            AppCategory.MESSAGING,
            AppCategoryDictionary.categorize("org.thoughtcrime.securesms")
        )
    }

    @Test
    fun slack_isMessaging() {
        assertEquals(AppCategory.MESSAGING, AppCategoryDictionary.categorize("com.Slack"))
    }

    @Test
    fun instagram_isSocial() {
        assertEquals(AppCategory.SOCIAL, AppCategoryDictionary.categorize("com.instagram.android"))
    }

    @Test
    fun tiktok_isSocial() {
        assertEquals(
            AppCategory.SOCIAL,
            AppCategoryDictionary.categorize("com.zhiliaoapp.musically")
        )
    }

    @Test
    fun bluesky_isSocial() {
        assertEquals(AppCategory.SOCIAL, AppCategoryDictionary.categorize("bsky.app"))
    }

    @Test
    fun chrome_isBrowser() {
        assertEquals(AppCategory.BROWSER, AppCategoryDictionary.categorize("com.android.chrome"))
    }

    @Test
    fun firefox_isBrowser() {
        assertEquals(AppCategory.BROWSER, AppCategoryDictionary.categorize("org.mozilla.firefox"))
    }

    @Test
    fun youtube_isVideo() {
        assertEquals(
            AppCategory.VIDEO,
            AppCategoryDictionary.categorize("com.google.android.youtube")
        )
    }

    @Test
    fun netflix_isVideo() {
        assertEquals(AppCategory.VIDEO, AppCategoryDictionary.categorize("com.netflix.mediaclient"))
    }

    @Test
    fun spotify_isVideo() {
        // Music/audio streamers live in VIDEO bucket (v1 schema has no audio category).
        assertEquals(AppCategory.VIDEO, AppCategoryDictionary.categorize("com.spotify.music"))
    }

    @Test
    fun kindle_isReading() {
        assertEquals(AppCategory.READING, AppCategoryDictionary.categorize("com.amazon.kindle"))
    }

    @Test
    fun pocket_isReading() {
        assertEquals(AppCategory.READING, AppCategoryDictionary.categorize("com.pocket.app"))
    }

    @Test
    fun nytimes_isReading() {
        assertEquals(AppCategory.READING, AppCategoryDictionary.categorize("com.nytimes.android"))
    }
}
