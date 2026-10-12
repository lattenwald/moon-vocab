package org.lattenwald.moonvocab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.ui.AppShell
import org.lattenwald.moonvocab.ui.ShareViewModel
import org.lattenwald.moonvocab.ui.theme.MoonVocabTheme

class MainActivity : AppCompatActivity() {
    private val share: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        container.appScope.launch { container.ankiCheck.runIfDue() }
        if (savedInstanceState == null) receive(intent)
        setContent {
            MoonVocabTheme {
                AppShell(share)
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
        // Below API 33 a foreign Parcelable among the extras throws BadParcelableException.
        val uri = runCatching { IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) }
            .onFailure { Log.w(TAG, "unreadable share", it) }
            .getOrNull()
        if (uri != null) share.offer(uri)
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
