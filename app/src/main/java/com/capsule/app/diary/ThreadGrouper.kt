package com.capsule.app.diary

import com.capsule.app.data.ipc.EnvelopeViewParcel

/**
 * T047 — pure, deterministic thread grouper for the diary.
 *
 * Implements the heuristic from research.md §9 Thread Grouping:
 *
 *   An envelope joins a thread iff all three hold:
 *     1. Same day (guaranteed by caller — one grouper call per day).
 *     2. Same [com.capsule.app.data.model.AppCategory] (via
 *        [EnvelopeViewParcel.appCategory]).
 *     3. Either captured within [PROXIMITY_WINDOW_MS] of another envelope
 *        in the thread, OR cosine-similar (≥ [SIMILARITY_THRESHOLD]) to
 *        any envelope in the thread via the injected [similarity] seam.
 *
 * Degraded mode — if [similarity] always returns 0f (e.g., Nano
 * unavailable), the grouper falls back to (same category ∧ time window)
 * which is still useful, just coarser. v1 ships with a no-op similarity
 * because `NanoLlmProvider` is stubbed; a later slice can inject a real
 * embedding-backed similarity function without touching this class.
 *
 * Determinism:
 *   - Input list is sorted by `createdAtMillis` ascending before grouping
 *     so the output is independent of input order.
 *   - Threads are emitted in the order their first envelope was captured.
 *   - Envelopes within a thread are sorted by `createdAtMillis` ascending.
 *
 * The grouper is a `class` with an injected similarity function rather
 * than an object so tests can exercise both the proximity and similarity
 * paths without a live LLM.
 */
class ThreadGrouper(
    /**
     * Cosine similarity in [0f, 1f] between two envelopes' text. Default
     * is a no-op returning 0f which disables the similarity merge rule
     * (proximity-only grouping). In a later slice the diary VM will inject
     * a Nano-backed embedding similarity.
     */
    private val similarity: (EnvelopeViewParcel, EnvelopeViewParcel) -> Float = { _, _ -> 0f },
    private val proximityWindowMs: Long = PROXIMITY_WINDOW_MS,
    private val similarityThreshold: Float = SIMILARITY_THRESHOLD
) {

    /**
     * Group [envelopes] into [DiaryThread]s per the §9 heuristic.
     *
     * Caller is responsible for ensuring all envelopes share the same
     * `dayLocal`; the grouper does not cross-day check.
     */
    fun group(envelopes: List<EnvelopeViewParcel>): List<DiaryThread> {
        if (envelopes.isEmpty()) return emptyList()

        val sorted = envelopes.sortedBy { it.createdAtMillis }
        val threads = mutableListOf<MutableList<EnvelopeViewParcel>>()

        for (env in sorted) {
            val target = threads.firstOrNull { thread ->
                thread.first().appCategory == env.appCategory && joinsThread(env, thread)
            }
            if (target != null) {
                target.add(env)
            } else {
                threads.add(mutableListOf(env))
            }
        }

        return threads.map { members ->
            DiaryThread(
                id = members.first().id, // deterministic: first envelope's id
                appCategory = members.first().appCategory,
                startedAtMillis = members.first().createdAtMillis,
                envelopes = members.toList()
            )
        }
    }

    private fun joinsThread(
        candidate: EnvelopeViewParcel,
        thread: List<EnvelopeViewParcel>
    ): Boolean {
        // Proximity: within PROXIMITY_WINDOW_MS of the MOST RECENT envelope
        // in the thread. Using the latest — not the first — so a long thread
        // can keep accruing late-in-the-day captures that are each within 30
        // min of the previous one.
        val latest = thread.maxOf { it.createdAtMillis }
        if (kotlin.math.abs(candidate.createdAtMillis - latest) <= proximityWindowMs) {
            return true
        }
        // Similarity: ≥ threshold against ANY envelope in the thread.
        return thread.any { member ->
            similarity(candidate, member) >= similarityThreshold
        }
    }

    companion object {
        /** 30 minutes, per research.md §9. */
        const val PROXIMITY_WINDOW_MS: Long = 30L * 60L * 1000L
        /** 0.75, per research.md §9. */
        const val SIMILARITY_THRESHOLD: Float = 0.75f
    }
}

/**
 * A grouped run of envelopes that share a common app category and are
 * linked by temporal proximity and/or topical similarity.
 *
 * @property id stable thread id — the id of the first envelope in the
 *   thread. Stable across regenerations as long as that envelope is not
 *   deleted.
 * @property appCategory the [com.capsule.app.data.model.AppCategory]
 *   shared by every envelope in the thread (`.name`).
 * @property startedAtMillis epoch millis of the earliest envelope in the
 *   thread — drives sort order in the diary.
 * @property envelopes envelopes sorted by `createdAtMillis` ascending.
 */
data class DiaryThread(
    val id: String,
    val appCategory: String,
    val startedAtMillis: Long,
    val envelopes: List<EnvelopeViewParcel>
)
