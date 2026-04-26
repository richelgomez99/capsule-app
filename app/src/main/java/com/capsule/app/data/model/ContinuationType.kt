package com.capsule.app.data.model

enum class ContinuationType {
    URL_HYDRATE,
    // 003 v1.1 additions:
    /** Run on charger+wifi, produces 0..N action_proposal rows. */
    ACTION_EXTRACT,
    /** User-initiated; no scheduling constraints; dispatches an Intent via `:capture`. */
    ACTION_EXECUTE,
    /** Periodic Sunday 06:00 local; produces 0..1 DIGEST envelope per Sunday. */
    WEEKLY_DIGEST
}
