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
                instructions = "Settings → Battery → Background usage limits → Never sleeping apps → Add Capsule",
                settingsAction = "com.samsung.android.sm.ACTION_BATTERY_USAGE_LIST"
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> BatteryGuideInfo(
                manufacturer = "Xiaomi",
                instructions = "Settings → Battery → App battery saver → Capsule → No restrictions. Also: Security app → Permissions → Autostart → Enable Capsule",
                settingsAction = "miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY"
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> BatteryGuideInfo(
                manufacturer = "Huawei",
                instructions = "Settings → Battery → App launch → Capsule → Manage manually → Enable all three toggles (Auto-launch, Secondary launch, Run in background)",
                settingsAction = "huawei.intent.action.HSM_PROTECTED_APPS"
            )
            manufacturer.contains("oneplus") -> BatteryGuideInfo(
                manufacturer = "OnePlus",
                instructions = "Settings → Battery → Battery optimization → All apps → Capsule → Don't optimize",
                settingsAction = null
            )
            manufacturer.contains("oppo") -> BatteryGuideInfo(
                manufacturer = "Oppo",
                instructions = "Settings → Battery → More battery settings → Optimize battery use → Capsule → Don't optimize. Also: Settings → App management → Capsule → Energy saver → Allow background running",
                settingsAction = null
            )
            manufacturer.contains("vivo") -> BatteryGuideInfo(
                manufacturer = "Vivo",
                instructions = "Settings → Battery → High background power consumption → Enable Capsule. Also: i Manager → App manager → Autostart manager → Enable Capsule",
                settingsAction = null
            )
            manufacturer.contains("realme") -> BatteryGuideInfo(
                manufacturer = "Realme",
                instructions = "Settings → Battery → More battery settings → Optimize battery use → Capsule → Don't optimize. Also: Settings → App management → Capsule → Auto-launch → Allow",
                settingsAction = null
            )
            else -> null
        }
    }
}
