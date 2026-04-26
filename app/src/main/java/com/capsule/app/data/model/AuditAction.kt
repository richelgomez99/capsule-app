package com.capsule.app.data.model

/** Closed set of auditable actions for v1 + v1.1. */
enum class AuditAction {
    ENVELOPE_CREATED,
    ENVELOPE_ARCHIVED,
    ENVELOPE_DELETED,
    ENVELOPE_SOFT_DELETED,
    ENVELOPE_RESTORED,
    ENVELOPE_HARD_PURGED,
    INTENT_SUPERSEDED,
    INFERENCE_RUN,
    NETWORK_FETCH,
    CONTINUATION_SCHEDULED,
    CONTINUATION_COMPLETED,
    CAPTURE_SCRUBBED,
    URL_DEDUPE_HIT,
    PERMISSION_REDUCED_MODE_ENTERED,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    EXPORT_STARTED,
    EXPORT_COMPLETED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,

    // 003 v1.1 additions — Orbit Actions:
    /** ACTION_EXTRACT continuation produced a proposal. */
    ACTION_PROPOSED,
    /** User swiped/declined a proposal in Diary. */
    ACTION_DISMISSED,
    /** User tapped Confirm on the preview card. */
    ACTION_CONFIRMED,
    /** ACTION_EXECUTE worker dispatched the Intent successfully. */
    ACTION_EXECUTED,
    /** Dispatch failed (no app, intent rejected, schema mismatch, user cancel within undo window, etc). */
    ACTION_FAILED,
    /** Schema row inserted/updated in `appfunction_skill`. */
    APPFUNCTION_REGISTERED,
    /** WeeklyDigestWorker produced a DIGEST envelope. */
    DIGEST_GENERATED,
    /** WeeklyDigestWorker ran but produced nothing (sparse window, already exists, fallback failure). */
    DIGEST_SKIPPED,
    /** A DIGEST or DERIVED envelope was soft-deleted because all its source envelopes were deleted. */
    ENVELOPE_INVALIDATED
}
