package com.capsule.app.audit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T091 — host activity for [AuditLogScreen].
 */
class AuditLogActivity : ComponentActivity() {

    private lateinit var client: BinderAuditLogClient
    private lateinit var viewModel: AuditLogViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        client = BinderAuditLogClient(applicationContext)
        val provider = object : AuditLogProvider {
            override suspend fun entriesForDay(isoDate: String): List<AuditEntryParcel> =
                client.entriesForDay(isoDate)
        }
        viewModel = AuditLogViewModel(provider = provider)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { client.connect() }
        }

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AuditLogScreen(
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
        if (::client.isInitialized) client.disconnect()
        super.onDestroy()
    }
}
