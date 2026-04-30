package com.capsule.app.diagnostics

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.capsule.app.ai.LlmProviderDiagnostics
import com.capsule.app.RuntimeFlags
import com.capsule.app.ai.NanoLlmProvider

/**
 * T097 (spec/003) — debug-build only.
 *
 * Lets a developer flip [LlmProviderDiagnostics.forceNanoUnavailable] so the
 * `:ml` provider methods that 003 routes through ([NanoLlmProvider.summarize]
 * for Weekly Digest, [NanoLlmProvider.extractActions] for Orbit Actions) throw
 * [com.capsule.app.ai.NanoUnavailableException] without de-provisioning AICore
 * on the device. Used by quickstart §6 N2.
 *
 * **No production exposure**: this Activity lives only in the `debug` source
 * set. The release manifest never sees the `<activity>` declaration in
 * `app/src/debug/AndroidManifest.xml`, so a release APK has no entry point
 * that can flip the flag.
 *
 * The Activity is intentionally unstyled (no Compose, no theme, no resources)
 * to keep the debug source set independent of the app's resource graph and to
 * minimise the attack surface a release build would inherit if the manifest
 * merge ever leaked the entry.
 */
class DiagnosticsActivity : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Capsule diagnostics (debug only)"
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }

        statusView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }

        val toggleButton = Button(this).apply {
            text = "Toggle Force-Nano-UNAVAILABLE"
            setOnClickListener {
                LlmProviderDiagnostics.forceNanoUnavailable =
                    !LlmProviderDiagnostics.forceNanoUnavailable
                refreshStatus()
            }
        }

        val resetButton = Button(this).apply {
            text = "Reset (Nano available)"
            setOnClickListener {
                LlmProviderDiagnostics.forceNanoUnavailable = false
                refreshStatus()
            }
        }

        root.addView(title)
        root.addView(statusView)
        root.addView(toggleButton)
        root.addView(resetButton)

        // T157 — cluster diagnostics block (debug-only). Three controls:
        //   1. Toggle cluster emit          — flips RuntimeFlags.clusterEmitEnabled.
        //   2. Force-emit synthetic cluster — flips RuntimeFlags.devClusterForceEmit.
        //   3. Reset cluster modelLabel lock — restores Nano.MODEL_LABEL.
        // Status text refreshed after each press so the device-under-test
        // reflects the live state of the kill switches.
        val clusterTitle = TextView(this).apply {
            text = "Cluster diagnostics"
            textSize = 16f
            setPadding(0, 32, 0, 16)
        }

        val clusterStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }

        fun refreshClusterStatus() {
            val emit = RuntimeFlags.clusterEmitEnabled
            val force = RuntimeFlags.devClusterForceEmit
            val lock = RuntimeFlags.clusterModelLabelLock
            clusterStatus.text = buildString {
                append("clusterEmitEnabled=$emit\n")
                append("devClusterForceEmit=$force\n")
                append("modelLabelLock=$lock")
            }
        }

        val toggleClusterEmit = Button(this).apply {
            text = "Toggle cluster emit"
            setOnClickListener {
                RuntimeFlags.clusterEmitEnabled = !RuntimeFlags.clusterEmitEnabled
                refreshClusterStatus()
            }
        }

        val toggleDevForceEmit = Button(this).apply {
            text = "Force-emit synthetic cluster"
            setOnClickListener {
                RuntimeFlags.devClusterForceEmit = !RuntimeFlags.devClusterForceEmit
                refreshClusterStatus()
            }
        }

        val resetModelLabelLock = Button(this).apply {
            text = "Reset cluster modelLabel lock"
            setOnClickListener {
                RuntimeFlags.clusterModelLabelLock = NanoLlmProvider.MODEL_LABEL
                refreshClusterStatus()
            }
        }

        root.addView(clusterTitle)
        root.addView(clusterStatus)
        root.addView(toggleClusterEmit)
        root.addView(toggleDevForceEmit)
        root.addView(resetModelLabelLock)
        setContentView(root)

        refreshStatus()
        refreshClusterStatus()
    }

    private fun refreshStatus() {
        val state = if (LlmProviderDiagnostics.forceNanoUnavailable) {
            "Nano forced UNAVAILABLE — extractActions() and summarize() will throw."
        } else {
            "Nano available (default)."
        }
        statusView.text = state
    }
}
