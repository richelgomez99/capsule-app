package com.capsule.app.permission

import android.os.Build

data class BatteryGuideInfo(
    val manufacturer: String,
    val instructions: String,
    val settingsAction: String? = null
)

/**
 * Detects OEM via Build.MANUFACTURER and returns manufacturer-specific
 * battery optimization guidance for the 7 aggressive OEMs.
 */
object BatteryOptimizationGuide {

    fun getGuide(): BatteryGuideInfo? {
        val manufacturer = Build.MANUFACTURER.lowercase()

        return when {
            manufacturer.contains("samsung") -> BatteryGuideInfo(
                manufacturer = "Samsung",
                instructions = "Settings → Battery → Background usage limits → Never sleeping apps → Add Orbit",
                settingsAction = "com.samsung.android.sm.ACTION_BATTERY_USAGE_LIST"
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> BatteryGuideInfo(
                manufacturer = "Xiaomi",
                instructions = "Settings → Battery → App battery saver → Orbit → No restrictions. Also: Security app → Permissions → Autostart → Enable Orbit",
                settingsAction = "miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY"
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> BatteryGuideInfo(
                manufacturer = "Huawei",
                instructions = "Settings → Battery → App launch → Orbit → Manage manually → Enable all three toggles (Auto-launch, Secondary launch, Run in background)",
                settingsAction = "huawei.intent.action.HSM_PROTECTED_APPS"
            )
            manufacturer.contains("oneplus") -> BatteryGuideInfo(
                manufacturer = "OnePlus",
                instructions = "Settings → Battery → Battery optimization → All apps → Orbit → Don't optimize",
                settingsAction = null
            )
            manufacturer.contains("oppo") -> BatteryGuideInfo(
                manufacturer = "Oppo",
                instructions = "Settings → Battery → More battery settings → Optimize battery use → Orbit → Don't optimize. Also: Settings → App management → Orbit → Energy saver → Allow background running",
                settingsAction = null
            )
            manufacturer.contains("vivo") -> BatteryGuideInfo(
                manufacturer = "Vivo",
                instructions = "Settings → Battery → High background power consumption → Enable Orbit. Also: i Manager → App manager → Autostart manager → Enable Orbit",
                settingsAction = null
            )
            manufacturer.contains("realme") -> BatteryGuideInfo(
                manufacturer = "Realme",
                instructions = "Settings → Battery → More battery settings → Optimize battery use → Orbit → Don't optimize. Also: Settings → App management → Orbit → Auto-launch → Allow",
                settingsAction = null
            )
            else -> null
        }
    }
}
