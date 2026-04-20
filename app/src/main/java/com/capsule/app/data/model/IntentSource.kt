package com.capsule.app.data.model

/** How the intent was assigned to an envelope. */
enum class IntentSource {
    USER_CHIP,
    PREDICTED_SILENT,
    AUTO_AMBIGUOUS,
    FALLBACK,
    DIARY_REASSIGN
}
