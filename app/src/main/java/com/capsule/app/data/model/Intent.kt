package com.capsule.app.data.model

/** The five v1 product intent labels + AMBIGUOUS (unassigned). */
enum class Intent {
    WANT_IT,
    REFERENCE,
    READ_LATER,
    FOR_SOMEONE,
    INTERESTING,
    AMBIGUOUS
}

fun String.toIntentOrAmbiguous(): Intent =
    runCatching { Intent.valueOf(this) }.getOrElse { Intent.AMBIGUOUS }
