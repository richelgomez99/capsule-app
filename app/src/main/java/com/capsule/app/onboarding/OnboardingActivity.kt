package com.capsule.app.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.capsule.app.diary.DiaryActivity
import com.capsule.app.permission.OverlayPermissionHelper
import com.capsule.app.permission.UsageAccessHelper
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.delay

/**
 * T096 / T097 / T098 / T099 / T100 / T101 / T102 / T103 / T103a — first-run
 * onboarding host.
 *
 * Four sequential steps:
 *  1. POST_NOTIFICATIONS (runtime)
 *  2. SYSTEM_ALERT_WINDOW (Settings deep-link; result polled on resume)
 *  3. PACKAGE_USAGE_STATS (Settings deep-link; result polled on resume)
 *  4. ACTIVITY_RECOGNITION (runtime)
 *
 * Each step:
 *  - shows a rationale Compose sheet first (not the system dialog);
 *  - on "Enable" issues the actual request / deep-link;
 *  - on grant/decline writes a `PERMISSION_GRANTED` / `PERMISSION_REVOKED`
 *    audit row;
 *  - exposes a "Grant later" escape that advances without granting and
 *    leaves the missing-permission banner in the Diary (T103).
 *
 * Reduced mode (T103a/b):
 *  - Declining notifications or overlay twice trips a rationale modal.
 *    A second decline sets `reducedMode=true` and routes to
 *    [ReducedModeActivity] on finish instead of [DiaryActivity].
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var prefs: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = OnboardingPreferences(this)

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    OnboardingFlow(
                        onFinished = { complete() }
                    )
                }
            }
        }
    }

    private fun complete() {
        prefs.completed = true
        val next = if (prefs.reducedMode) {
            Intent(this, ReducedModeActivity::class.java)
        } else {
            Intent(this, DiaryActivity::class.java)
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(next)
        finish()
    }
}

@Composable
private fun OnboardingFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    // T103a — track decline counts for the two structurally required permissions.
    var notificationDeclineCount by remember { mutableIntStateOf(0) }
    var overlayDeclineCount by remember { mutableIntStateOf(0) }
    var showReducedRationale by remember { mutableStateOf(false) }

    fun advance() {
        if (step + 1 >= totalSteps) onFinished() else step += 1
    }

    fun handleReducedIfNeeded(): Boolean {
        // Returns true if we intercepted for reduced-mode rationale.
        val overlayOk = OverlayPermissionHelper.canDrawOverlays(context)
        val notifOk = hasNotificationPermission(context)
        if (!overlayOk && overlayDeclineCount >= 2) {
            prefs.reducedMode = true
            onFinished()
            return true
        }
        if (!notifOk && notificationDeclineCount >= 2) {
            prefs.reducedMode = true
            onFinished()
            return true
        }
        if ((!overlayOk && overlayDeclineCount == 1) ||
            (!notifOk && notificationDeclineCount == 1)
        ) {
            showReducedRationale = true
            return true
        }
        return false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (step + 1f) / totalSteps },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (step) {
                0 -> NotificationStep(
                    onResult = { granted ->
                        PermissionAudit.record(
                            context,
                            PermissionAudit.Permission.POST_NOTIFICATIONS,
                            granted
                        )
                        if (!granted) notificationDeclineCount += 1
                        if (handleReducedIfNeeded()) return@NotificationStep
                        advance()
                    },
                    onSkip = { advance() }
                )

                1 -> OverlayStep(
                    onResult = { granted ->
                        PermissionAudit.record(
                            context,
                            PermissionAudit.Permission.SYSTEM_ALERT_WINDOW,
                            granted
                        )
                        if (!granted) overlayDeclineCount += 1
                        if (handleReducedIfNeeded()) return@OverlayStep
                        advance()
                    },
                    onSkip = { advance() }
                )

                2 -> UsageAccessStep(
                    onResult = { granted ->
                        PermissionAudit.record(
                            context,
                            PermissionAudit.Permission.PACKAGE_USAGE_STATS,
                            granted
                        )
                        advance()
                    },
                    onSkip = { advance() }
                )

                3 -> ActivityRecognitionStep(
                    onResult = { granted ->
                        PermissionAudit.record(
                            context,
                            PermissionAudit.Permission.ACTIVITY_RECOGNITION,
                            granted
                        )
                        advance()
                    },
                    onSkip = { advance() }
                )
            }
        }
    }

    if (showReducedRationale) {
        AlertDialog(
            onDismissRequest = { showReducedRationale = false },
            title = { Text("Orbit needs this to capture") },
            text = {
                Text(
                    "Without notifications and overlay permission, Orbit cannot show " +
                        "the capture bubble or run its background service. You can still " +
                        "browse the Diary in read-only mode, or grant the permission to " +
                        "enable capture."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReducedRationale = false
                    // User acknowledged — let them retry the current step.
                }) { Text("Try again") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReducedRationale = false
                    prefs.reducedMode = true
                    onFinished()
                }) { Text("Use read-only mode") }
            }
        )
    }
}

@Composable
private fun NotificationStep(
    onResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    StepScaffold(
        title = "Enable notifications",
        body = "Orbit runs a tiny foreground service so your captures and " +
            "enrichments keep working when you switch apps. Android requires " +
            "notifications for foreground services on this version.",
        primaryLabel = if (hasNotificationPermission(context)) "Continue" else "Enable",
        onPrimary = {
            if (hasNotificationPermission(context)) {
                onResult(true)
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onSkip = onSkip
    )
}

@Composable
private fun OverlayStep(
    onResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    OnResumeCheck(
        check = { OverlayPermissionHelper.canDrawOverlays(context) },
        onChanged = { granted -> if (granted) onResult(true) }
    )

    StepScaffold(
        title = "Allow the capture bubble",
        body = "Orbit draws a small floating bubble on top of other apps " +
            "whenever you copy text or capture a screenshot. You can drag " +
            "it anywhere or flick it off-screen to hide it.",
        primaryLabel = if (OverlayPermissionHelper.canDrawOverlays(context)) "Continue" else "Open settings",
        onPrimary = {
            if (OverlayPermissionHelper.canDrawOverlays(context)) {
                onResult(true)
            } else {
                context.startActivity(
                    OverlayPermissionHelper.buildOverlayPermissionIntent(context)
                )
            }
        },
        secondaryLabel = if (OverlayPermissionHelper.canDrawOverlays(context)) null else "Not now",
        onSecondary = { onResult(false) },
        onSkip = onSkip
    )
}

@Composable
private fun UsageAccessStep(
    onResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    OnResumeCheck(
        check = { UsageAccessHelper.hasUsageAccess(context) },
        onChanged = { granted -> if (granted) onResult(true) }
    )

    val has = UsageAccessHelper.hasUsageAccess(context)
    StepScaffold(
        title = "Let Orbit see which app was in front",
        body = "When you capture, Orbit records the app category you were in " +
            "so the Diary can thread captures under the right source. Grant " +
            "usage access, find Orbit in the list, and flip the switch.",
        primaryLabel = if (has) "Continue" else "Open settings",
        onPrimary = {
            if (has) onResult(true)
            else context.startActivity(UsageAccessHelper.buildUsageAccessIntent())
        },
        secondaryLabel = if (has) null else "Not now",
        onSecondary = { onResult(false) },
        onSkip = onSkip
    )
}

@Composable
private fun ActivityRecognitionStep(
    onResult: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    StepScaffold(
        title = "Activity recognition (optional)",
        body = "Orbit uses walking/stationary hints to label captures in the " +
            "Diary (\"captured while walking\"). Local only — no location " +
            "is collected.",
        primaryLabel = if (hasActivityRecognition(context)) "Finish" else "Enable",
        onPrimary = {
            if (hasActivityRecognition(context)) onResult(true)
            else launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        },
        onSkip = onSkip
    )
}

@Composable
private fun StepScaffold(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth()
            ) { Text(primaryLabel) }
            if (secondaryLabel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                    Text(secondaryLabel)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Grant later")
            }
        }
    }
}

/**
 * Polls the [check] predicate on every ON_RESUME and fires [onChanged]
 * when it flips to true. Used by the two Settings-deep-link steps to
 * auto-advance when the user returns with the permission flipped on.
 */
@Composable
private fun OnResumeCheck(
    check: () -> Boolean,
    onChanged: (Boolean) -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onChanged(check())
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Also re-poll briefly after resume in case the system takes a beat
    // to propagate the permission grant back to this process.
    LaunchedEffect(Unit) {
        repeat(5) {
            delay(250)
            if (check()) {
                onChanged(true)
                return@LaunchedEffect
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun hasActivityRecognition(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
