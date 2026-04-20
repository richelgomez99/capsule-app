package com.capsule.app.data.model

/** Categorized foreground app at capture time. Raw package name is NOT stored. */
enum class AppCategory {
    WORK_EMAIL,
    MESSAGING,
    SOCIAL,
    BROWSER,
    VIDEO,
    READING,
    OTHER,
    UNKNOWN_SOURCE
}
