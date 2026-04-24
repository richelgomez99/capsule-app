package com.capsule.app.diary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.audit.DebugCounters
import com.capsule.app.diary.ui.DiaryScreen
import com.capsule.app.permission.OverlayPermissionHelper
import com.capsule.app.settings.SettingsActivity
import com.capsule.app.ui.MainActivity
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * T052 + T053 — launcher entry point for User Story 2.
 *
 * Owns the [BinderDiaryRepository] binding lifecycle (onStart/onStop) and
 * hosts [DiaryScreen] with a [DiaryViewModel] wired to today's ISO date.
 *
 * T055 — the `:ml` bind is **pre-warmed** in `onCreate` via an async
 * `lifecycleScope.launch`, so by the time the user's eye reaches the
 * screen the service connection is ready and the first day-page emission
 * arrives inside the P50 ≤ 1 s target.
 */
class DiaryActivity : ComponentActivity() {

    private lateinit var repository: BinderDiaryRepository
    private lateinit var viewModel: DiaryViewModel
    private lateinit var pagingSource: DiaryPagingSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // T105 — dev-only diary-open counter (no-op in release).
        DebugCounters.incDiaryOpen(this)

        repository = BinderDiaryRepository(applicationContext)
        viewModel = DiaryViewModel(
            repository = repository,
            threadGrouper = ThreadGrouper(),
            dayHeaderGenerator = DayHeaderGenerator(NanoLlmProvider())
        )
        pagingSource = DiaryPagingSource(repository)

        // T055 — pre-warm the :ml binding off the main thread so first
        // render isn't blocked on bindService round-trip.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.connect() }
            // T056 — fetch the first batch of older non-empty days in the
            // background so the pager has something to swipe into by the
            // time the user starts backscrolling.
            runCatching { pagingSource.loadMore() }
        }

        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewModel.observe(today)

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DiaryScreen(
                        viewModel = viewModel,
                        onOpenSetup = { openSetup() },
                        onOpenSettings = { openSettings() },
                        pagingSource = pagingSource
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // First-run convenience: if the overlay permission hasn't been granted
        // yet, the bubble can't appear, so the empty-state copy ("tap the
        // bubble") is a dead end. Route the user into the setup flow
        // (MainActivity) on foreground so they can grant permissions and
        // start the overlay service. Users who already completed setup skip
        // this entirely.
        if (!routedToSetupOnce && !OverlayPermissionHelper.canDrawOverlays(this)) {
            routedToSetupOnce = true
            openSetup()
        }
    }

    private fun openSetup() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onDestroy() {
        repository.disconnect()
        super.onDestroy()
    }

    private var routedToSetupOnce = false
}
