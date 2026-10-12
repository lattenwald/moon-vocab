package org.lattenwald.moonvocab

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.runBlocking
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.ui.settings.nightMode

class App : Application() {
    lateinit var container: AppContainer
        private set

    lateinit var startSettings: SettingsSnapshot
        private set

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(IMPORT_CHANNEL, getString(R.string.import_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        container = AppContainer(this)
        startSettings = runBlocking { container.settings.current() }
        AppCompatDelegate.setDefaultNightMode(startSettings.theme.nightMode())
    }

    companion object {
        const val IMPORT_CHANNEL = "import"
    }
}
