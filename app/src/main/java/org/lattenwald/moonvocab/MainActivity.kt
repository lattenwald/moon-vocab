package org.lattenwald.moonvocab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.ui.AppShell
import org.lattenwald.moonvocab.ui.theme.MoonVocabTheme

class MainActivity : AppCompatActivity() {
    private val sharedBackup = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        container.appScope.launch { container.ankiCheck.runIfDue() }
        if (savedInstanceState == null) receive(intent)
        setContent {
            MoonVocabTheme {
                AppShell(sharedBackup)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        // Recents relaunches a finished task with its original, already handled share.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        sharedBackup.value = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }
}
