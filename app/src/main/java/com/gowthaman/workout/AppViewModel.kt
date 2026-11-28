package com.gowthaman.workout

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gowthaman.workout.data.Prefs
import com.gowthaman.workout.data.Repo
import com.gowthaman.workout.data.SafStore
import com.gowthaman.workout.data.SharedPrefsStore
import com.gowthaman.workout.notify.AndroidReminderScheduler
import kotlinx.coroutines.Dispatchers

/**
 * Android's lifecycle, wrapped around the app.
 *
 * All this does is build [AppModel] out of the Android-shaped pieces -- a SAF-backed
 * folder, SharedPreferences, AlarmManager -- and lend it a scope that survives a rotation.
 * The screens take the model, not this, so none of them can reach a Context by accident.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(SharedPrefsStore(app))

    private val repo = Repo(
        prefs = prefs,
        // The location string is a SAF tree URI here. :core never looks inside it.
        openStore = { location -> SafStore(app, Uri.parse(location)) },
        bundledDoc = { name ->
            runCatching {
                app.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrDefault("")
        },
        io = Dispatchers.IO,
    )

    val model = AppModel(
        prefs = prefs,
        repo = repo,
        scheduler = AndroidReminderScheduler(app),
        scope = viewModelScope,
    )
}

/**
 * Keeps access to a picked folder across restarts, then starts using it.
 *
 * The grant is the Android-only half of choosing a folder and belongs with the picker
 * that produced the URI. Without it the URI works until the process dies and then
 * silently stops, which looks exactly like lost data.
 */
fun AppModel.useFolder(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
    useFolder(uri.toString())
}
