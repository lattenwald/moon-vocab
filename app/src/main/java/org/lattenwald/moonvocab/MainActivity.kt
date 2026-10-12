package org.lattenwald.moonvocab

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.launch
import org.lattenwald.moonvocab.ui.AppShell
import org.lattenwald.moonvocab.ui.theme.MoonVocabTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        container.appScope.launch { container.ankiCheck.runIfDue() }
        setContent {
            MoonVocabTheme {
                AppShell()
            }
        }
    }
}
