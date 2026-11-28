package com.gowthaman.workout.data

/**
 * Somewhere small and device-local to keep settings.
 *
 * The companion to [DocumentStore], and the same idea: the data folder holds what the
 * person logged, this holds what this particular device is set to. They are kept apart on
 * purpose -- one folder can be synced to several devices, and which of them buzzes at
 * 09:00 is not a property of the data.
 *
 * Typed accessors rather than strings-for-everything, for one reason that matters more
 * than tidiness: an existing install already has these values stored as ints, booleans and
 * floats. A port that could only carry strings would need every one of them migrated on
 * first launch, and a migration that goes wrong silently resets somebody's settings. Every
 * backing store worth using -- SharedPreferences, NSUserDefaults -- is typed anyway.
 *
 * Reads take their fallback with them, so a key that has never been written is not an
 * error and no store has to know the defaults. The defaults live in [Prefs], once.
 */
interface KeyValueStore {

    fun getString(key: String, fallback: String?): String?

    fun getInt(key: String, fallback: Int): Int

    fun getBoolean(key: String, fallback: Boolean): Boolean

    fun getFloat(key: String, fallback: Float): Float

    /**
     * Applies several writes as a single commit.
     *
     * Batched because settings are written in groups and a half-written group is a
     * genuinely confusing state to recover from -- the unit changed but the increment did
     * not, and nothing says why.
     */
    fun write(block: (Writer) -> Unit)

    interface Writer {
        fun putString(key: String, value: String?)
        fun putInt(key: String, value: Int)
        fun putBoolean(key: String, value: Boolean)
        fun putFloat(key: String, value: Float)
    }
}
