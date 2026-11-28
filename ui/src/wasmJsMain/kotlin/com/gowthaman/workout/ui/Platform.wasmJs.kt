package com.gowthaman.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Stubs, so the canary can compile.
 *
 * This target is never shipped and never run. It exists to be built, because it is the
 * only thing that can tell us shared UI has reached for a Context, a Toast or java.time:
 * the Android target has all three and compiles them without a word.
 *
 * They are empty rather than TODO() on purpose. A `TODO()` would be a runtime failure
 * waiting for a run that never happens, and would read as unfinished work rather than as
 * a deliberate no-op. If this module ever does ship for the web, these are the five
 * places to start -- and the compiler will not let anyone forget them, because nothing
 * else here is allowed to be platform-specific.
 */

@Composable
actual fun rememberToaster(): (String) -> Unit = remember { {} }

@Composable
actual fun rememberSharer(): (String) -> Unit = remember { {} }

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit = remember { {} }

@Composable
actual fun rememberNotificationPermission(
    onResult: (granted: Boolean) -> Unit,
): NotificationPermission = remember {
    object : NotificationPermission {
        override val needed: Boolean get() = false
        override fun ask() = Unit
    }
}

@Composable
actual fun rememberAuthenticator(): Authenticator = remember {
    object : Authenticator {
        // Nothing to ask with, which is exactly the case the shared lock screen already
        // handles: it gives way rather than shutting anyone out.
        override val unavailableReason: String get() = "Not available here."
        override fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) = Unit
    }
}
