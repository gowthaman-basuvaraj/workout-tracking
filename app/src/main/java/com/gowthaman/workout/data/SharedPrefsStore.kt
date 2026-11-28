package com.gowthaman.workout.data

import android.content.Context

/**
 * [KeyValueStore] over SharedPreferences.
 *
 * The file name and every key are the ones the app has always used, so an existing
 * install keeps its settings across this refactor rather than quietly reverting to the
 * defaults.
 *
 * `apply()` rather than `commit()`: the write goes to memory immediately and to disk on a
 * background thread, which is what a settings toggle wants. Losing the last write to a
 * kill -9 is a fair trade for never blocking the UI thread on a toggle.
 */
class SharedPrefsStore(context: Context) : KeyValueStore {

    private val sp = context.applicationContext
        .getSharedPreferences("workoutlog.settings", Context.MODE_PRIVATE)

    override fun getString(key: String, fallback: String?): String? = sp.getString(key, fallback)

    override fun getInt(key: String, fallback: Int): Int = sp.getInt(key, fallback)

    override fun getBoolean(key: String, fallback: Boolean): Boolean = sp.getBoolean(key, fallback)

    override fun getFloat(key: String, fallback: Float): Float = sp.getFloat(key, fallback)

    override fun write(block: (KeyValueStore.Writer) -> Unit) {
        val editor = sp.edit()
        block(object : KeyValueStore.Writer {
            override fun putString(key: String, value: String?) { editor.putString(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun putFloat(key: String, value: Float) { editor.putFloat(key, value) }
        })
        editor.apply()
    }
}
