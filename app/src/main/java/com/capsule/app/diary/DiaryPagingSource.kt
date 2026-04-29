package com.capsule.app.diary

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * T056 — paginates non-empty days for the Diary's `HorizontalPager`.
 *
 * Invariants:
 *   * `days[0]` is always **today** (even when today has no content).
 *   * `days[1..]` are non-empty days older than today, newest first, as
 *     returned by [DiaryRepository.distinctDayLocalsWithContent].
 *   * Duplicates (if the repository happens to return `today` as its
 *     first item because today had content) are skipped.
 *   * `loadMore()` is idempotent once the source is exhausted.
 *
 * Not tied to androidx.paging because a flat `StateFlow<List<String>>`
 * plugs directly into Compose's `HorizontalPager` via `pageCount = days.size`.
 */
class DiaryPagingSource(
    private val repository: DiaryRepository,
    private val todayIsoProvider: () -> String = { LocalDate.now().toString() },
    private val pageSize: Int = DEFAULT_PAGE_SIZE
) {

    private val today: String = todayIsoProvider()

    private val _days = MutableStateFlow(listOf(today))
    val days: StateFlow<List<String>> = _days.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var exhausted: Boolean = false

    /** Total non-empty days already fetched from the repository. */
    @Volatile
    private var repoOffset: Int = 0

    /**
     * Fetches the next batch of older non-empty days and appends them.
     * Safe to call from `LaunchedEffect(pagerState.currentPage)`: the
     * mutex serialises overlapping triggers.
     */
    suspend fun loadMore() {
        if (exhausted) return
        mutex.withLock {
            if (exhausted) return
            val batch = repository.distinctDayLocalsWithContent(
                limit = pageSize,
                offset = repoOffset
            )
            repoOffset += batch.size
            val existing = _days.value.toHashSet()
            val additions = batch.filter { it !in existing }
            if (additions.isEmpty()) {
                exhausted = true
                return
            }
            _days.value = _days.value + additions
            if (batch.size < pageSize) exhausted = true
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 30
    }
}
