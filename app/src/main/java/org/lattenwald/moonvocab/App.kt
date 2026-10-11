package org.lattenwald.moonvocab

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(IMPORT_CHANNEL, getString(R.string.import_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        container = AppContainer(this)
    }

    companion object {
        const val IMPORT_CHANNEL = "import"
    }
}
