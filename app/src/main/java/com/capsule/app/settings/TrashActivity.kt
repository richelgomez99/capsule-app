package com.capsule.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.capsule.app.ui.theme.CapsuleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T091a — host for [TrashScreen]. Launched from Settings.
 */
class TrashActivity : ComponentActivity() {

    private lateinit var repository: BinderTrashRepository
    private lateinit var viewModel: TrashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = BinderTrashRepository(applicationContext)
        viewModel = TrashViewModel(repository = repository)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { repository.connect() }
        }

        setContent {
            CapsuleTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TrashScreen(
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
        if (::repository.isInitialized) repository.disconnect()
        super.onDestroy()
    }
}
