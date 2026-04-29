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
    ENVELOPE_INVALIDATED,

    // 002 amendment Phase 11 additions — cluster engine (T117):
    /** ClusterDetectionWorker produced a new cluster row. */
    CLUSTER_FORMED,
    /** Cluster transitioned from FORMING -> SURFACED (visible in Diary). */
    CLUSTER_SURFACED,
    /** User tapped any action affordance on the cluster card. */
    CLUSTER_TAPPED,
    /** Cluster transitioned to ACTING (Nano inference in flight). Persisted BEFORE inference per FR-012-031. */
    CLUSTER_ACTING,
    /** Cluster reached terminal ACTED — Summarize bullets rendered with citations. */
    CLUSTER_ACTED,
    /** Nano inference returned error/null/timeout, or sanitizer rejected the output. */
    CLUSTER_FAILED,
    /** User dismissed the cluster card (swipe or ×). */
    CLUSTER_DISMISSED,
    /** Cluster aged out (>7 days SURFACED without action) per FR-039. */
    CLUSTER_AGED_OUT,
    /** Cluster auto-dismissed because surviving member count fell below 3 (FR-038). */
    CLUSTER_ORPHANED
}
