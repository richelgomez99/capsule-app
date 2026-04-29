package com.capsule.app.diagnostics

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.capsule.app.ai.LlmProviderDiagnostics

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
        setContentView(root)

        refreshStatus()
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
