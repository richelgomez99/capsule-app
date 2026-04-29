package com.capsule.app.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T099 (spec/003): regression-locks the AndroidManifest permission set.
 *
 * Spec 003 (Orbit Actions) MUST NOT add a single new `<uses-permission>` element
 * relative to the spec 002 baseline (per research.md §4 + Constitution Principle VIII +
 * action-execution-contract.md §6 — calendar/to-do writes flow through other apps'
 * Activity intents, never our own permissions).
 *
 * Any future addition forces an explicit, reviewed update to [EXPECTED_PERMISSIONS]
 * with a code-review prompt to justify the new permission.
 */
class ManifestPermissionsRegressionTest {

    /**
     * Frozen permission set as of spec/003 Phase 8 (commit 5c39bf5).
     * Adding/removing a permission requires updating this list.
     */
    private val EXPECTED_PERMISSIONS = setOf(
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.INTERNET",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.SYSTEM_ALERT_WINDOW",
    )

    /**
     * Permissions that 003 explicitly forbids. Adding any of these is a hard fail
     * (would mean writes are happening inside our process rather than via external
     * Activity intents).
     */
    private val FORBIDDEN_PERMISSIONS = setOf(
        "android.permission.WRITE_CALENDAR",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_EXTERNAL_STORAGE",
    )

    private fun manifestFile(): File {
        // Gradle JVM-tests cwd is the module root (`app/`). Try both layouts.
        val direct = File("src/main/AndroidManifest.xml")
        if (direct.exists()) return direct
        val fromRepoRoot = File("app/src/main/AndroidManifest.xml")
        check(fromRepoRoot.exists()) { "AndroidManifest.xml not found from cwd=${File(".").absolutePath}" }
        return fromRepoRoot
    }

    private fun parsePermissions(): Set<String> {
        val xml = manifestFile().readText()
        val regex = Regex("""<uses-permission[^/>]*android:name="([^"]+)"""")
        return regex.findAll(xml).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun manifest_permission_set_matches_expected() {
        val actual = parsePermissions()
        val added = actual - EXPECTED_PERMISSIONS
        val removed = EXPECTED_PERMISSIONS - actual
        assertTrue(
            "AndroidManifest permissions drifted. Added=$added Removed=$removed. " +
                "Update EXPECTED_PERMISSIONS only with explicit review.",
            added.isEmpty() && removed.isEmpty()
        )
    }

    @Test
    fun manifest_does_not_request_forbidden_permissions() {
        val actual = parsePermissions()
        val forbiddenPresent = actual.intersect(FORBIDDEN_PERMISSIONS)
        assertEquals(
            "Spec 003 forbids these permissions (writes flow through external Activities): $forbiddenPresent",
            emptySet<String>(),
            forbiddenPresent
        )
    }

    @Test
    fun manifest_permission_count_is_eleven() {
        // Tripwire: any net add/remove fails fast even if EXPECTED list is edited carelessly.
        assertEquals(11, parsePermissions().size)
    }
}
