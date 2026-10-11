package org.lattenwald.moonvocab.ui.common

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ichi2.anki.api.AddContentApi

@Composable
fun rememberAnkiPermissionRequest(onResult: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && activity != null && !activity.shouldShowRequestPermissionRationale(AddContentApi.READ_WRITE_PERMISSION)) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            )
        }
        onResult()
    }
    return remember(launcher) { { launcher.launch(AddContentApi.READ_WRITE_PERMISSION) } }
}
