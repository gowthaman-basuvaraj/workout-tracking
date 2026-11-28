package com.gowthaman.workout.ui

import androidx.compose.runtime.Composable

/**
 * The five things a screen cannot do by itself.
 *
 * Everything else in this module is the same Compose on every platform. These are the
 * places where showing the same screen means asking two operating systems for genuinely
 * different things -- a share sheet, a folder, permission to post a notification -- and
 * no amount of abstraction makes them the same API.
 *
 * They are `remember` functions returning a lambda rather than an interface, because on
 * Android three of them must be registered during composition: an Activity result
 * launcher cannot be created on demand from a click handler. Expressing that honestly
 * here is better than an interface that happens to be unimplementable on the platform it
 * exists for.
 */

/** Shows a short, self-dismissing message. A toast on Android. */
@Composable
expect fun rememberToaster(): (String) -> Unit

/** Hands [text] to whatever the system uses for sharing. */
@Composable
expect fun rememberSharer(): (String) -> Unit

/**
 * Asks for a folder to keep the log in, and reports back what was chosen.
 *
 * The string handed to [onPicked] is opaque -- a SAF tree URI on Android -- and goes
 * straight to `AppModel.useFolder`, which does not interpret it either. Anything the
 * platform must do to keep access across restarts has already happened by then.
 */
@Composable
expect fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit

/**
 * Asks for permission to post reminders, reporting whether it was given.
 *
 * [NotificationPermission.needed] is false where there is nothing to ask for -- Android
 * before 13 posts without asking -- and the caller then treats it as already granted.
 */
@Composable
expect fun rememberNotificationPermission(
    onResult: (granted: Boolean) -> Unit,
): NotificationPermission

interface NotificationPermission {
    val needed: Boolean
    fun ask()
}

/**
 * Asks the device to confirm who is holding it.
 *
 * Only the prompt is a seam. The locked screen around it -- the title, the message, the
 * Unlock button -- is ordinary Compose and stays in common, so a second platform supplies
 * an authenticator rather than redrawing a screen.
 */
@Composable
expect fun rememberAuthenticator(): Authenticator

interface Authenticator {
    /**
     * Why this device cannot be asked, or null when it can.
     *
     * Not decoration. A lock the device can no longer satisfy -- the screen lock was
     * removed after the lock was switched on -- would shut someone out of their own log
     * permanently, to protect files a file manager can still read. The caller gives way
     * when this is non-null.
     */
    val unavailableReason: String?

    fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit)
}
