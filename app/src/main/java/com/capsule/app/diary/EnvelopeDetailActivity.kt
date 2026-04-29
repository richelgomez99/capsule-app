package com.capsule.app.diary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.capsule.app.diary.ui.EnvelopeDetailScreen
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * T055b — host for [EnvelopeDetailScreen]. Launched from [com.capsule.app.diary.ui.EnvelopeCard]
 * via an explicit intent with [EXTRA_ENVELOPE_ID]. Not launcher-visible,
 * not exported.
 *
 * Binding lifecycle: owns its own [BinderDiaryRepository] (so the screen
 * can be opened from anywhere that wants to deep-link a capture). Pre-warm
 * happens in `onCreate` mirroring [DiaryActivity]. Disconnects in `onDestroy`.
 */
class EnvelopeDetailActivity : ComponentActivity() {

    private lateinit var repository: BinderDiaryRepository
    private lateinit var viewModel: EnvelopeDetailViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val envelopeId = intent.getStringExtra(EXTRA_ENVELOPE_ID)
        if (envelopeId.isNullOrBlank()) {
            finish()
            return
        }

        repository = BinderDiaryRepository(applicationContext)
        viewModel = EnvelopeDetailViewModel(
            envelopeId = envelopeId,
            repository = repository
        )

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.connect() }
        }

        // Close on archive / delete.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finished.collectLatest { done ->
                    if (done) finish()
                }
            }
        }

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    EnvelopeDetailScreen(
                        viewModel = viewModel,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroy() {
        if (::repository.isInitialized) repository.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ENVELOPE_ID = "com.capsule.app.extra.ENVELOPE_ID"
        const val EXTRA_DAY_LOCAL = "com.capsule.app.extra.DAY_LOCAL"

        /** Convenience builder used by [com.capsule.app.diary.ui.EnvelopeCard]. */
        fun newIntent(
            context: android.content.Context,
            envelopeId: String,
            dayLocal: String?
        ): Intent = Intent(context, EnvelopeDetailActivity::class.java).apply {
            putExtra(EXTRA_ENVELOPE_ID, envelopeId)
            if (dayLocal != null) putExtra(EXTRA_DAY_LOCAL, dayLocal)
        }
    }
}
