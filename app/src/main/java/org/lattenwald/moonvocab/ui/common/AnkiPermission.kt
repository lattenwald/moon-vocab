package org.lattenwald.moonvocab.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.ichi2.anki.api.AddContentApi
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App

private const val TAG = "AnkiPermission"
private const val ANKIDROID_MARKET = "market://details?id=com.ichi2.anki"
private const val ANKIDROID_WEB = "https://play.google.com/store/apps/details?id=com.ichi2.anki"

@Composable
fun rememberAnkiPermissionRequest(onResult: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    // Saveable: the activity can be recreated while the system dialog shows.
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && askedBefore && activity != null && !activity.shouldShowRequestPermissionRationale(AddContentApi.READ_WRITE_PERMISSION)) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            )
        }
        onResult()
    }
    return remember(launcher, scope, container) {
        {
            scope.launch {
                askedBefore = try {
                    withContext(container.io) {
                        val asked = container.settings.current().ankiAsked
                        if (!asked) container.settings.update { it.copy(ankiAsked = true) }
                        asked
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "ankiAsked write failed", e)
                    false
                }
                launcher.launch(AddContentApi.READ_WRITE_PERMISSION)
            }
        }
    }
}

fun openAnkiDroidStore(context: Context) {
    for (uri in listOf(ANKIDROID_MARKET, ANKIDROID_WEB)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app opens $uri", e)
        }
    }
}
