package com.capsule.app.diary.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.provider.Settings
import android.widget.Toast
import com.capsule.app.data.ClusterCardModel
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.Intent
import com.capsule.app.diary.ActionPreviewSheet
import com.capsule.app.diary.ActionProposalChipRow
import com.capsule.app.diary.DayUiState
import com.capsule.app.diary.DiaryPagingSource
import com.capsule.app.diary.DiaryViewModel
import com.capsule.app.diary.EnvelopeDetailActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * T050 + T056 — diary screen with unbounded backscroll over non-empty days.
 *
 * If a [pagingSource] is provided, the screen hosts a [HorizontalPager]
 * whose pages map to ISO dates (today at index 0, older non-empty days
 * following). Swiping near the end triggers [DiaryPagingSource.loadMore]
 * to fetch the next 30-day batch.
 *
 * When [pagingSource] is null (legacy callers / tests), the screen
 * renders only the single day the VM currently observes.
 *
 * Empty / Loading / Error / Ready are rendered as exhaustive branches
 * over [DayUiState] — the sealed type enforces that the UI stays in
 * sync with VM reductions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier,
    onOpenSetup: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    pagingSource: DiaryPagingSource? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Orbit") },
                actions = {
                    val settingsAction = onOpenSettings ?: onOpenSetup
                    if (settingsAction != null) {
                        IconButton(onClick = settingsAction) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (pagingSource != null) {
                PagedDayHost(
                    pagingSource = pagingSource,
                    viewModel = viewModel,
                    state = state,
                    onOpenSetup = onOpenSetup
                )
            } else {
                RenderDayState(
                    state = state,
                    viewModel = viewModel,
                    onOpenSetup = onOpenSetup
                )
            }
        }
    }
}

@Composable
private fun PagedDayHost(
    pagingSource: DiaryPagingSource,
    viewModel: DiaryViewModel,
    state: DayUiState,
    onOpenSetup: (() -> Unit)?
) {
    val days by pagingSource.days.collectAsState()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { days.size }
    )
    val scope = rememberCoroutineScope()

    // Tell the VM to observe whichever day is currently settled. Only fires
    // when the `currentPage` stops changing (i.e. after a swipe settles).
    LaunchedEffect(pagerState, days) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { idx ->
                days.getOrNull(idx)?.let { iso -> viewModel.observe(iso) }
            }
    }

    // Prefetch older days when the user approaches the end.
    LaunchedEffect(pagerState, days.size) {
        snapshotFlow { pagerState.currentPage to days.size }
            .distinctUntilChanged()
            .collect { (page, total) ->
                if (total > 0 && page >= total - PREFETCH_DISTANCE) {
                    pagingSource.loadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DayNavBar(
            currentPage = pagerState.currentPage,
            totalPages = days.size,
            onPrev = {
                // Older day = higher page index because `reverseLayout = true`.
                scope.launch {
                    val target = pagerState.currentPage + 1
                    if (target >= days.size) {
                        // Try to fetch the next batch first; it's safe to call
                        // repeatedly — the paging source dedupes + exhausts.
                        pagingSource.loadMore()
                    }
                    if (target < pagingSource.days.value.size) {
                        pagerState.animateScrollToPage(target)
                    }
                }
            },
            onNext = {
                scope.launch {
                    val target = pagerState.currentPage - 1
                    if (target >= 0) pagerState.animateScrollToPage(target)
                }
            }
        )

        HorizontalPager(
            state = pagerState,
            // T056 — today sits on the right edge; swiping right walks into older
            // non-empty days (matches a physical calendar flipping backwards).
            reverseLayout = true,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val iso = days.getOrNull(page)
            // Only the settled page's state is accurate; adjacent pages show a
            // lightweight placeholder until the VM swaps its observation.
            if (iso == null || page != pagerState.currentPage) {
                LoadingView()
            } else {
                RenderDayState(state = state, viewModel = viewModel, onOpenSetup = onOpenSetup)
            }
        }
    }
}

@Composable
private fun DayNavBar(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // T056 — button-based day navigation. Swipe still works, but buttons
    // make the unbounded backscroll UX discoverable.
    // `currentPage == 0` means today (reverseLayout inverts the mapping).
    val canGoNext = currentPage > 0
    // Prev is always enabled when we have at least today rendered: the
    // paging source transparently fetches the next batch on tap and
    // short-circuits once the history is exhausted.
    val canGoPrev = totalPages > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = canGoPrev) {
            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text("Older day")
        }
        TextButton(onClick = onNext, enabled = canGoNext) {
            Text("Newer day")
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

private const val PREFETCH_DISTANCE: Int = 3

@Composable
private fun RenderDayState(
    state: DayUiState,
    viewModel: DiaryViewModel,
    onOpenSetup: (() -> Unit)?
) {
    val context = LocalContext.current
    var pendingProposal by remember { mutableStateOf<ActionProposalParcel?>(null) }

    // T053 — surface the 5 s undo window via a Toast. Compose Snackbar
    // would be a cleaner host but the rest of Diary already uses Toasts
    // for transient feedback, so we match for visual consistency.
    val undoState by viewModel.undoState.collectAsState()
    LaunchedEffect(undoState?.executionId) {
        val s = undoState ?: return@LaunchedEffect
        val msg = when (s.outcome) {
            "DISPATCHED", "SUCCESS" -> "Added · tap notification to undo"
            "FAILED" -> "Couldn't add: ${s.outcomeReason ?: "unknown"}"
            "USER_CANCELLED" -> "Cancelled"
            else -> s.outcome
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    when (val s = state) {
        is DayUiState.Loading -> LoadingView()
        is DayUiState.Empty -> EmptyDayView(isoDate = s.isoDate, onOpenSetup = onOpenSetup)
        is DayUiState.Error -> ErrorView(message = s.message)
        is DayUiState.Ready -> DayContentView(
            state = s,
            viewModel = viewModel,
            onReassign = { id, intent ->
                viewModel.onReassignIntent(id, intent.name, reason = "DIARY_REASSIGN")
            },
            onRetry = { id ->
                viewModel.onRetryHydration(id)
                Toast.makeText(context, "Retrying enrichment\u2026", Toast.LENGTH_SHORT).show()
            },
            onDelete = { id ->
                viewModel.onDelete(id)
                Toast.makeText(context, "Moved to trash", Toast.LENGTH_SHORT).show()
            },
            onOpenDetail = { id ->
                context.startActivity(
                    EnvelopeDetailActivity.newIntent(context, id, dayLocal = s.isoDate)
                )
            },
            onProposalTap = { proposal -> pendingProposal = proposal }
        )
    }

    pendingProposal?.let { proposal ->
        ActionPreviewSheet(
            proposal = proposal,
            onConfirm = { editedArgsJson ->
                viewModel.onConfirmProposal(proposal, editedArgsJson)
                pendingProposal = null
            },
            onDismiss = {
                viewModel.onDismissProposal(proposal.id)
                pendingProposal = null
            }
        )
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyDayView(isoDate: String, onOpenSetup: (() -> Unit)? = null) {
    // T054 — empty-day copy. Kept short and un-judgmental per product tone.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Nothing saved yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Copy something, then tap the bubble.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onOpenSetup != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSetup) {
                    Text("Set up Orbit")
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Couldn't load today",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayContentView(
    state: DayUiState.Ready,
    viewModel: DiaryViewModel,
    onReassign: (String, Intent) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onProposalTap: (ActionProposalParcel) -> Unit
) {
    // Phase 11 Block 9 / T149 — reduce-motion preference. Compose has no
    // first-class API for this; we read `Settings.Global.ANIMATOR_DURATION_SCALE`
    // (== 0f means "Remove animations" in Developer Options + Accessibility).
    // Falls back to `false` if the setting is unreadable.
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // T149 — cluster slot renders ABOVE the day-header on cluster days
        // (spec 010 D6 revision: events outrank steady-state). Non-cluster
        // days (clusters.isEmpty()) skip the slot entirely.
        state.clusters.forEach { cluster ->
            item(key = "cluster-${cluster.clusterId}") {
                val cardState = cluster.toCardStateOrNull()
                if (cardState != null) {
                    ClusterSuggestionCard(
                        state = cardState,
                        onSummarize = { /* T-future: wired when ClusterSummariser lands (Block 6/10) */ },
                        onOpenAll = { /* T-future: cluster detail navigation */ },
                        onDismiss = { /* T-future: ClusterRepository.markDismissed */ },
                        onRetry = { /* T-future: re-enqueue summary */ },
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }

        // Sticky-ish day header (LazyColumn's stickyHeader API is overkill
        // for a single item; a plain `item` matches the Figma's "hero
        // paragraph that scrolls up with the rest" behaviour).
        item(key = "header-${state.isoDate}") {
            DayHeader(
                isoDate = state.isoDate,
                paragraph = state.header,
                generationLocale = state.generationLocale
            )
            Spacer(Modifier.height(8.dp))
        }

        state.threads.forEach { thread ->
            item(key = "thread-label-${thread.id}") {
                ThreadLabel(label = thread.appCategory)
            }
            items(thread.envelopes, key = { it.id }) { env ->
                EnvelopeCard(
                    envelope = env,
                    onReassign = onReassign,
                    onRetry = onRetry,
                    onDelete = onDelete,
                    onOpenDetail = onOpenDetail,
                    onToggleTodoItem = { id, index, done ->
                        viewModel.onToggleTodoItem(id, index, done)
                    }
                )
                // T051 — chip-row inline beneath the card. The flow is
                // owned by this item so swiping the envelope out of the
                // viewport correctly tears down the proposal observer.
                ActionProposalChipRow(
                    envelopeId = env.id,
                    proposalsFlow = viewModel.observeProposals(env.id),
                    onChipTap = onProposalTap
                )
            }
            item(key = "thread-space-${thread.id}") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(
    isoDate: String,
    paragraph: String,
    generationLocale: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = isoDate,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        if (paragraph.isNotBlank()) {
            Text(
                text = paragraph,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            // If the header was generated in English for a non-English
            // device, surface a small hint so the user knows why.
            if (generationLocale.isNotBlank() && generationLocale != java.util.Locale.getDefault().language) {
                Text(
                    text = "Generated in English",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThreadLabel(label: String) {
    Text(
        text = label.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

// ----------------------------------------------------------------------
// Phase 11 Block 9 / T149 — ClusterCardModel → ClusterSuggestionCardState
// ----------------------------------------------------------------------
//
// Bare-minimum projection. Block 6 (ClusterSummariser) will replace the
// placeholder bodyText / bullets / sourceCategories once Nano summary
// output is wired. Until then the card surfaces honestly: it shows the
// cluster exists and how many captures fed it, without inventing a
// summary it can't yet produce.
//
// Mapping:
//   SURFACED, TAPPED      → Surfaced(...)
//   ACTING                → Acting(...)
//   ACTED                 → null  (Block 6 supplies bullets; render
//                                  nothing rather than fabricate them)
//   FAILED                → Failed(retryCount=0)
//   FORMING/DISMISSED/AGED_OUT → null  (DAO already excludes these but
//                                       the case must be exhaustive)
//
// `headerLabel`, `timeRangeLabel`, `sourceCategories` are pre-formatted
// here so the card stays locale-agnostic.
private fun ClusterCardModel.toCardStateOrNull(): ClusterSuggestionCardState? {
    val header = "Cluster · ${members.size} captures"
    val timeRange = formatBucketRange(timeBucketStart, timeBucketEnd)
    val sources = emptyList<String>() // Block 6 fills from envelope appCategory join.
    val placeholderBody = "${members.size} captures across this period."

    return when (state) {
        ClusterState.SURFACED, ClusterState.TAPPED -> ClusterSuggestionCardState.Surfaced(
            headerLabel = header,
            timeRangeLabel = timeRange,
            sourceCategories = sources,
            bodyText = placeholderBody,
        )
        ClusterState.ACTING -> ClusterSuggestionCardState.Acting(
            headerLabel = header,
            timeRangeLabel = timeRange,
            sourceCategories = sources,
            bodyText = placeholderBody,
        )
        ClusterState.FAILED -> ClusterSuggestionCardState.Failed(
            headerLabel = header,
            timeRangeLabel = timeRange,
            sourceCategories = sources,
            retryCount = 0,
        )
        ClusterState.ACTED,
        ClusterState.FORMING,
        ClusterState.DISMISSED,
        ClusterState.AGED_OUT -> null
    }
}

private val bucketRangeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE h:mma", Locale.US)

private fun formatBucketRange(startMillis: Long, endMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).format(bucketRangeFormatter)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).format(bucketRangeFormatter)
    return "$start → $end"
}
