package com.gowthaman.workout.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/**
 * Android's answers to the five things a screen cannot do by itself.
 *
 * All the Activity-result plumbing lives here. Each of these is registered during
 * composition because that is the only time a launcher can be created -- which is why
 * the common declarations are `remember` functions and not an interface.
 */

@Composable
actual fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
actual fun rememberSharer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share summary"))
        }
    }
}

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // The grant has to be taken here, while the pick is still fresh. Without it
            // the URI works until the process dies and then silently stops, which looks
            // exactly like lost data.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onPicked(uri.toString())
        }
    }
    return { launcher.launch(null) }
}

@Composable
actual fun rememberNotificationPermission(
    onResult: (granted: Boolean) -> Unit,
): NotificationPermission {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }

    return remember(context, launcher) {
        object : NotificationPermission {
            // Android 13 added the runtime permission; before that, posting is allowed.
            override val needed: Boolean
                get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED

            override fun ask() = launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
actual fun rememberAuthenticator(): Authenticator {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    return remember(context, activity) {
        object : Authenticator {
            override val unavailableReason: String?
                get() = Biometrics.unavailableReason(context)
                    ?: if (activity == null) "Could not show the unlock prompt." else null

            override fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
                val host = activity
                if (host == null) {
                    onFailure("Could not show the unlock prompt.")
                    return
                }
                Biometrics.prompt(
                    activity = host,
                    onSuccess = onSuccess,
                    // The framework can fail without saying why; the shared screen always
                    // wants something to show.
                    onFailure = { reason -> onFailure(reason ?: "Could not verify it was you.") },
                )
            }
        }
    }
}

/** Compose hands out a context; the biometric prompt wants the activity behind it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
