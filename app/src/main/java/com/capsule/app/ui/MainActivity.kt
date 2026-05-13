package com.capsule.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.permission.BatteryGuideInfo
import com.capsule.app.permission.BatteryOptimizationGuide
import com.capsule.app.permission.OverlayPermissionHelper
import com.capsule.app.permission.UsageAccessHelper
import com.capsule.app.settings.QuietRowDescription
import com.capsule.app.settings.QuietRowTitle
import com.capsule.app.settings.QuietRule
import com.capsule.app.settings.QuietSettingSection
import com.capsule.app.settings.QuietSettingsColors
import com.capsule.app.service.CapsuleOverlayService
import com.capsule.app.ui.primitives.MonoLabel
import com.capsule.app.ui.theme.CapsuleTheme
import com.capsule.app.ui.theme.LocalRuntimeFlags
import com.capsule.app.ui.tokens.CapsuleType

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "capsule_overlay_prefs"
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var isServiceEnabled by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasUsageAccess by mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = OverlayPermissionHelper.canDrawOverlays(this)
        if (hasOverlayPermission && isServiceEnabled) {
            startOverlayService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && hasOverlayPermission) {
            startOverlayService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        isServiceEnabled = prefs.getBoolean("service_enabled", false)
        hasOverlayPermission = OverlayPermissionHelper.canDrawOverlays(this)
        hasUsageAccess = UsageAccessHelper.hasUsageAccess(this)

        setContent {
            CapsuleTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceEnabled = prefs.getBoolean("service_enabled", false)
        hasOverlayPermission = OverlayPermissionHelper.canDrawOverlays(this)
        hasUsageAccess = UsageAccessHelper.hasUsageAccess(this)
    }

    @Composable
    private fun MainScreen() {
        if (LocalRuntimeFlags.current.useNewVisualLanguage) {
            QuietMainScreen()
            return
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Orbit",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capture overlay",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Toggle row
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Enable floating bubble",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = isServiceEnabled,
                            onCheckedChange = { enabled ->
                                onToggleChanged(enabled)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Overlay permission status
                if (!hasOverlayPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Overlay permission required",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                overlayPermissionLauncher.launch(
                                    OverlayPermissionHelper.buildOverlayPermissionIntent(this@MainActivity)
                                )
                            }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Usage Access — special permission needed so
                // StateSnapshotCollector can resolve which app was in
                // the foreground at capture time. Without it every
                // envelope is threaded as "Unknown source".
                if (!hasUsageAccess) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Usage Access (optional)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lets Orbit attribute captures to the app you copied from (Messages, Chrome, etc.). Without this, everything is labelled \"Unknown source\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                startActivity(UsageAccessHelper.buildUsageAccessIntent())
                            }) {
                                Text("Grant Usage Access")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Battery optimization guide (OEM-specific)
                BatteryGuideCard()
            }
        }
    }

    @Composable
    private fun QuietMainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(QuietSettingsColors.BgDeep)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp)) {
                MonoLabel(
                    text = "// Capture setup",
                    color = QuietSettingsColors.CreamDim,
                    size = 9.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Orbit",
                    color = QuietSettingsColors.Cream,
                    style = TextStyle(
                        fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                QuietRowDescription("Turn on the floating capture bubble and keep source attribution healthy.")
            }

            QuietSettingSection(label = "Floating bubble") {
                QuietSetupToggleRow(
                    title = "Enable floating bubble",
                    description = "Runs the overlay service so Orbit can capture from anywhere.",
                    checked = isServiceEnabled,
                    onCheckedChange = ::onToggleChanged,
                )
            }

            QuietSettingSection(label = "Permissions") {
                QuietSetupActionRow(
                    title = if (hasOverlayPermission) "Overlay permission" else "Overlay permission required",
                    description = if (hasOverlayPermission) {
                        "Android allows Orbit to show the capture bubble above other apps."
                    } else {
                        "Allow Orbit to show the capture bubble above other apps before turning it on."
                    },
                    action = if (hasOverlayPermission) "Review" else "Grant",
                    onClick = {
                        overlayPermissionLauncher.launch(
                            OverlayPermissionHelper.buildOverlayPermissionIntent(this@MainActivity)
                        )
                    },
                )
                QuietSetupActionRow(
                    title = if (hasUsageAccess) "Usage access" else "Usage access optional",
                    description = if (hasUsageAccess) {
                        "Android allows Orbit to attribute captures to the app you copied from."
                    } else {
                        "Lets Orbit attribute captures to the app you copied from. Without it, captures are labelled Unknown source."
                    },
                    action = if (hasUsageAccess) "Review" else "Grant",
                    onClick = { startActivity(UsageAccessHelper.buildUsageAccessIntent()) },
                )
            }

            QuietBatteryGuideSection()

            Spacer(Modifier.height(28.dp))
        }
    }

    @Composable
    private fun QuietSetupToggleRow(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                QuietRowTitle(title)
                Spacer(Modifier.height(3.dp))
                QuietRowDescription(description)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        QuietRule()
    }

    @Composable
    private fun QuietSetupActionRow(
        title: String,
        description: String,
        action: String,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                QuietRowTitle(title)
                Spacer(Modifier.height(3.dp))
                QuietRowDescription(description)
            }
            TextButton(onClick = onClick) {
                Text(action, color = QuietSettingsColors.Accent)
            }
        }
        QuietRule()
    }

    @Composable
    private fun QuietBatteryGuideSection() {
        val guide = BatteryOptimizationGuide.getGuide() ?: return
        QuietSettingSection(label = "Battery") {
            QuietSetupActionRow(
                title = "${guide.manufacturer} battery optimization",
                description = guide.instructions,
                action = "Open",
                onClick = { openBatterySettings(guide) },
            )
        }
    }

    @Composable
    private fun BatteryGuideCard() {
        val guide = BatteryOptimizationGuide.getGuide() ?: return

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${guide.manufacturer} Battery Optimization",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = guide.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { openBatterySettings(guide) }) {
                    Text("Open Battery Settings")
                }
            }
        }
    }

    private fun openBatterySettings(guide: BatteryGuideInfo) {
        val candidates = buildList {
            guide.settingsAction?.let { add(Intent(it)) }
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            add(Intent(Settings.ACTION_SETTINGS))
        }

        for (intent in candidates) {
            if (intent.resolveActivity(packageManager) == null) continue
            runCatching { startActivity(intent) }
                .onSuccess { return }
        }
    }

    private fun onToggleChanged(enabled: Boolean) {
        isServiceEnabled = enabled
        prefs.edit().putBoolean("service_enabled", enabled).apply()

        if (enabled) {
            if (!hasOverlayPermission) {
                overlayPermissionLauncher.launch(
                    OverlayPermissionHelper.buildOverlayPermissionIntent(this)
                )
                return
            }
            OverlayPermissionHelper.requestNotificationPermission(notificationPermissionLauncher)
            startOverlayService()
        } else {
            stopOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, CapsuleOverlayService::class.java).apply {
            action = CapsuleOverlayService.ACTION_START_OVERLAY
        }
        startForegroundService(intent)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, CapsuleOverlayService::class.java).apply {
            action = CapsuleOverlayService.ACTION_STOP_OVERLAY
        }
        startService(intent)
    }
}
