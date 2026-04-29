package com.capsule.app.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T098 (spec/003): `ActionExecutorService` MUST be lazy-started.
 *
 * Android binder semantics already ensure a `<service>` with no `startService`
 * caller is only instantiated when something calls `bindService` against it.
 * Spec 003's contract is that the **only** trigger is `:ui` calling
 * `bindService(ACTION_BIND_EXECUTOR)`; in particular, `CapsuleOverlayService`
 * (which lives in the same `:capture` process) must NOT eagerly start it.
 *
 * This regression test grep-asserts the source so a future refactor that
 * accidentally adds `startService` / `bindService` to `ActionExecutorService`
 * from any service-lifecycle hook fails fast.
 *
 * Pure JVM — uses file IO so it runs without an Android runtime.
 */
class ActionExecutorServiceLazyStartRegressionTest {

    private fun resolve(rel: String): File {
        val direct = File(rel)
        if (direct.exists()) return direct
        val fromRepoRoot = File("app/$rel")
        check(fromRepoRoot.exists()) {
            "missing source file $rel (cwd=${File(".").absolutePath})"
        }
        return fromRepoRoot
    }

    @Test
    fun capsuleOverlayService_does_not_eager_start_action_executor() {
        val src = resolve("src/main/java/com/capsule/app/service/CapsuleOverlayService.kt").readText()
        assertFalse(
            "CapsuleOverlayService must not reference ActionExecutorService — 003 lazy-start invariant.",
            src.contains("ActionExecutorService")
        )
        assertFalse(
            "CapsuleOverlayService must not reference BIND_ACTION_EXECUTOR.",
            src.contains("BIND_ACTION_EXECUTOR")
        )
    }

    @Test
    fun capture_process_has_no_other_eager_startService_for_executor() {
        // Walk every Kotlin source under :capture / overlay / service / action
        // and ensure no one calls Context.startService(...) on ActionExecutorService.
        // The only legitimate trigger is bindService from :ui.
        val roots = listOf(
            "src/main/java/com/capsule/app/service",
            "src/main/java/com/capsule/app/overlay",
            "src/main/java/com/capsule/app/action",
        ).map(::resolve)

        val offenders = mutableListOf<String>()
        for (root in roots) {
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
                val text = f.readText()
                // Pattern: startService(... ActionExecutorService ...) or startForegroundService(...)
                val lines = text.lineSequence().toList()
                for ((idx, line) in lines.withIndex()) {
                    if (line.contains("ActionExecutorService") &&
                        (line.contains("startService") || line.contains("startForegroundService"))
                    ) {
                        offenders += "${f.path}:${idx + 1}: $line"
                    }
                }
            }
        }
        assertTrue(
            "ActionExecutorService must only be reached via bindService. Offenders:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun action_executor_service_declares_no_default_startCommand_path() {
        // Sanity: the service itself should not use START_STICKY / START_REDELIVER_INTENT
        // since it's binder-only. If a future change adds onStartCommand returning STICKY,
        // it would silently turn the lazy service into an always-on one.
        val src = resolve("src/main/java/com/capsule/app/action/ActionExecutorService.kt").readText()
        // Allowed: onStartCommand returning START_NOT_STICKY (binder fall-through).
        // Forbidden: START_STICKY or START_REDELIVER_INTENT.
        assertFalse(
            "ActionExecutorService must not declare START_STICKY (would defeat lazy-start).",
            src.contains("START_STICKY") || src.contains("START_REDELIVER_INTENT")
        )
    }
}
