package com.capsule.app.capture

import com.capsule.app.data.model.AppCategory

/**
 * T037 — maps a foreground package name to an [AppCategory] bucket. The raw
 * package name is categorized and then **discarded**: only the bucket is ever
 * persisted on an [com.capsule.app.data.entity.IntentEnvelopeEntity] (research.md
 * §State Signal Collection).
 *
 * This is intentionally a lossy mapping: we do not try to cover every app on
 * the Play Store. We cover a curated set of ~200 commonly-used packages
 * grouped into 7 categories. Anything unmapped → [AppCategory.OTHER] (the
 * signal "an app was foregrounded, we just don't know which bucket").
 *
 * Distinguish from [AppCategory.UNKNOWN_SOURCE], which means "we could not
 * determine the foreground app at all" — typically because
 * `PACKAGE_USAGE_STATS` was not granted.
 */
object AppCategoryDictionary {

    private val work_email = setOf(
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider",
        "ch.protonmail.android",
        "com.fsck.k9",
        "com.readdle.spark",
        "com.superhuman.mail",
        "me.bluemail.mail",
        "com.hey.hey"
    )

    private val messaging = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "com.facebook.mlite",
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thoughtcrime.securesms", // Signal
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.Slack",
        "com.discord",
        "com.microsoft.teams",
        "us.zoom.videomeetings",
        "com.skype.raider",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.kakao.talk",
        "com.tencent.mm", // WeChat
        "com.google.android.apps.dynamite", // Google Chat
        "com.beeper.android",
        "com.cisco.webex.meetings"
    )

    private val social = setOf(
        "com.instagram.android",
        "com.instagram.barcelona", // Threads
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok international variant
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
        "tv.twitch.android.app",
        "com.tumblr",
        "com.bereal.ft",
        "bsky.app", // Bluesky
        "com.mastodon.android",
        "org.joinmastodon.android",
        "com.vk.android",
        "com.google.android.apps.plus"
    )

    private val browser = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "com.microsoft.emmx", // Edge
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "acr.browser.lightning",
        "com.yandex.browser",
        "org.torproject.torbrowser"
    )

    private val video = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient", // Prime Video
        "com.hbo.hbonow",
        "com.hbo.max",
        "com.wbd.stream", // Max
        "com.hulu.plus",
        "com.apple.atve.androidtv.appletv",
        "com.apple.android.music",
        "com.spotify.music",
        "com.pandora.android",
        "tv.plex.labs",
        "com.plexapp.android",
        "com.vimeo.android.videoapp",
        "com.paramount.android.pplus",
        "com.peacocktv.peacockandroid",
        "com.cbs.app"
    )

    private val reading = setOf(
        "com.google.android.apps.magazines", // Google News
        "com.apple.atve.androidtv.news",
        "com.nytimes.android",
        "com.washingtonpost.rainbow",
        "com.economist.lamarr",
        "com.theatlantic.android",
        "com.newyorker.android",
        "com.wsj.reader_sp",
        "flipboard.app",
        "com.pocket.app", // Mozilla Pocket
        "com.instapaper.android",
        "com.getpocket.android",
        "com.medium.reader",
        "com.substack.android",
        "com.amazon.kindle",
        "com.google.android.apps.books", // Play Books
        "com.audible.application",
        "com.overdrive.mobile.android.libby",
        "com.kobobooks.android",
        "com.goodreads",
        "com.feedly.android",
        "com.nononsenseapps.feeder",
        "com.readwise.reader"
    )

    /**
     * Returns the [AppCategory] bucket for a package name, or
     * [AppCategory.OTHER] if the package is unknown to the dictionary.
     *
     * Returns [AppCategory.UNKNOWN_SOURCE] only when [packageName] is null or
     * blank — i.e., we truly could not determine the foreground app.
     */
    fun categorize(packageName: String?): AppCategory {
        if (packageName.isNullOrBlank()) return AppCategory.UNKNOWN_SOURCE
        return when (packageName) {
            in work_email -> AppCategory.WORK_EMAIL
            in messaging -> AppCategory.MESSAGING
            in social -> AppCategory.SOCIAL
            in browser -> AppCategory.BROWSER
            in video -> AppCategory.VIDEO
            in reading -> AppCategory.READING
            else -> AppCategory.OTHER
        }
    }
}
