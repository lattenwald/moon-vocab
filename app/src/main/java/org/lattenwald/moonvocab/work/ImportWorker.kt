package org.lattenwald.moonvocab.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlin.coroutines.cancellation.CancellationException
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.MainActivity
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.domain.import.ImportProgress

class ImportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        val pipeline = container.pipeline
        val runs = container.importRuns
        try {
            setForeground(foregroundInfo(null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) { // Android 12+ refuses a foreground start while the app is in the background
            Log.w(TAG, "import runs without a foreground notification", e)
        }
        return try {
            val progress = { p: ImportProgress ->
                setProgressAsync(ImportWork.progress(p))
                setForegroundAsync(foregroundInfo(p))
                Unit
            }
            val summary = when (val job = ImportWork.job(inputData)) {
                ImportJob.RetryFailed -> pipeline.retryFailed(progress)
                is ImportJob.Import -> pipeline.run(job.request, progress)
            }
            runs.finished(summary)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            runs.failed(e.message ?: e::class.simpleName.orEmpty())
            Result.failure()
        }
    }

    private fun foregroundInfo(p: ImportProgress?): ForegroundInfo {
        val open = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, App.IMPORT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.import_notification_title))
            .setContentText(p?.let { applicationContext.resources.getQuantityString(R.plurals.import_progress, it.total, it.done, it.total) })
            .setSubText(p?.bookTitle)
            .setProgress(p?.total ?: 0, p?.done ?: 0, p == null)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val TAG = "ImportWorker"
        const val NOTIFICATION_ID = 1
    }
}
