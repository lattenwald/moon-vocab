package org.lattenwald.moonvocab.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.anki.AnkiAvailability

private const val TAG = "AnkiPermission"
private const val PERMISSION = AddContentApi.READ_WRITE_PERMISSION
private const val ANKIDROID_MARKET = "market://details?id=com.ichi2.anki"
private const val ANKIDROID_WEB = "https://play.google.com/store/apps/details?id=com.ichi2.anki"

@Composable
fun rememberAnkiPermissionRequest(onResult: () -> Unit = {}): () -> Unit {
    val activity = LocalActivity.current
    val container = (LocalContext.current.applicationContext as App).container
    val scope = rememberCoroutineScope()
    // Saveable: the activity can be recreated while the system dialog shows.
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    var inFlight by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        inFlight = false
        // No rationale before or after: the system answered without showing a dialog.
        if (!granted && askedBefore && !rationaleBefore && activity != null && !activity.shouldShowRequestPermissionRationale(PERMISSION)) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            )
        }
        onResult()
    }
    return remember(launcher, scope, container, activity) {
        {
            if (!inFlight) {
                inFlight = true
                scope.launch {
                    askedBefore = try {
                        withContext(container.io) {
                            val asked = container.settings.current().ankiAsked
                            if (!asked) container.settings.update { it.copy(ankiAsked = true) }
                            asked
                        }
                    } catch (e: CancellationException) {
                        inFlight = false
                        throw e
                    } catch (e: IOException) {
                        Log.e(TAG, "ankiAsked write failed", e)
                        false
                    }
                    rationaleBefore = activity?.shouldShowRequestPermissionRationale(PERMISSION) == true
                    try {
                        launcher.launch(PERMISSION)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "no permission dialog", e)
                        inFlight = false
                    }
                }
            }
        }
    }
}

@StringRes
fun AnkiAvailability.problemText(): Int? = when (this) {
    AnkiAvailability.NOT_INSTALLED -> R.string.inbox_banner_not_installed
    AnkiAvailability.NO_PERMISSION -> R.string.inbox_banner_no_permission
    AnkiAvailability.NOT_SET_UP -> R.string.inbox_banner_not_set_up
    AnkiAvailability.READY -> null
}

val AnkiAvailability?.isDown: Boolean get() = this != null && this != AnkiAvailability.READY

class AnkiFix(@StringRes val label: Int, val onClick: () -> Unit)

@Composable
fun ankiFix(availability: AnkiAvailability?): AnkiFix? {
    val context = LocalContext.current
    val requestAnki = rememberAnkiPermissionRequest()
    return remember(availability, context, requestAnki) {
        when (availability) {
            AnkiAvailability.NOT_INSTALLED -> AnkiFix(R.string.inbox_banner_get) { openAnkiDroidStore(context) }
            AnkiAvailability.NO_PERMISSION -> AnkiFix(R.string.inbox_banner_allow, requestAnki)
            AnkiAvailability.NOT_SET_UP -> AnkiFix(R.string.anki_open) { openAnkiDroid(context) }
            AnkiAvailability.READY, null -> null
        }
    }
}

fun openAnkiDroid(context: Context) {
    val launch = AddContentApi.getAnkiDroidPackageName(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
    if (launch == null) return openAnkiDroidStore(context)
    try {
        context.startActivity(launch)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "AnkiDroid won't open", e)
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
