package com.capsule.app.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.diary.BinderDiaryRepository
import com.capsule.app.diary.DayHeaderGenerator
import com.capsule.app.diary.DiaryActivity
import com.capsule.app.diary.DiaryPagingSource
import com.capsule.app.diary.DiaryViewModel
import com.capsule.app.diary.ThreadGrouper
import com.capsule.app.diary.ui.DiaryScreen
import com.capsule.app.permission.OverlayPermissionHelper
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T103b — hosts the Diary in read-only mode when the user has declined
 * SYSTEM_ALERT_WINDOW or POST_NOTIFICATIONS twice.
 *
 * Differences from [DiaryActivity]:
 *  - does NOT start `CapsuleOverlayService`;
 *  - shows a persistent top banner linking to the system permission
 *    settings so the user can opt back in;
 *  - on resume, if both permissions are now available, clears the
 *    `reducedMode` flag and hands off to [DiaryActivity].
 */
class ReducedModeActivity : ComponentActivity() {

    private lateinit var repository: BinderDiaryRepository
    private lateinit var viewModel: DiaryViewModel
    private lateinit var pagingSource: DiaryPagingSource
    private lateinit var prefs: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = OnboardingPreferences(this)
        repository = BinderDiaryRepository(applicationContext)
        viewModel = DiaryViewModel(
            repository = repository,
            threadGrouper = ThreadGrouper(),
            dayHeaderGenerator = DayHeaderGenerator(NanoLlmProvider())
        )
        pagingSource = DiaryPagingSource(repository)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.connect() }
            runCatching { pagingSource.loadMore() }
            viewModel.observe(LocalDate.now().toString())
        }

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ReducedModeBanner(onTap = {
                            startActivity(
                                OverlayPermissionHelper.buildOverlayPermissionIntent(this@ReducedModeActivity)
                            )
                        })
                        DiaryScreen(
                            viewModel = viewModel,
                            onOpenSetup = null,
                            onOpenSettings = null,
                            pagingSource = pagingSource
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionsNowGranted(this)) {
            prefs.reducedMode = false
            startActivity(Intent(this, DiaryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }

    override fun onDestroy() {
        if (::repository.isInitialized) repository.disconnect()
        super.onDestroy()
    }
}

@Composable
private fun ReducedModeBanner(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = "Capture is off. Tap to enable.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "You can still read past captures here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private fun permissionsNowGranted(context: Context): Boolean {
    val overlay = OverlayPermissionHelper.canDrawOverlays(context)
    val notif = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    return overlay && notif
}
