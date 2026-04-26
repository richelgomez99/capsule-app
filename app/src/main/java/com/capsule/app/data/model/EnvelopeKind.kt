package com.capsule.app.data.model

/**
 * Discriminator for what kind of envelope a row represents (003 v1.1).
 *
 * - [REGULAR]: every 002 envelope. Default after migration.
 * - [DIGEST]: weekly summary envelope produced by `WeeklyDigestWorker`.
 *   Carries `derivedFromEnvelopeIdsJson` linking to its source envelopes.
 * - [DERIVED]: forward-compat reservation for spec 012 / 008 derived
 *   artefacts (agent summaries, structured lists). Not produced in v1.1.
 *
 * Including [DERIVED] in v1.1 prevents a future enum migration when later
 * specs land. See `specs/003-orbit-actions/data-model.md` §"Enum Extensions".
 */
enum class EnvelopeKind {
    REGULAR,
    DIGEST,
    DERIVED
}
