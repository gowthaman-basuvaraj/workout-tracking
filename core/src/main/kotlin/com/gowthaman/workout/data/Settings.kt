package com.gowthaman.workout.data

/**
 * User preferences, as a plain value type.
 *
 * Lives in :core because the progression engine and the stats take it as input, and the
 * desktop CLI needs the same shape. How it is persisted is a frontend concern: the phone
 * uses SharedPreferences, the CLI uses defaults plus what it can infer from the data.
 */
data class Settings(
    /** Persisted SAF tree uri of the user's data folder. Null until they pick one. */
    val folderUri: String? = null,
    /** How often progressive overload is proposed: 7, 15 or 30 days. */
    val overloadPeriodDays: Int = 7,
    val weightUnit: String = Units.KG,
    val distanceUnit: String = Units.KM,
    /** Smallest weight step the user can actually load. */
    val weightIncrement: Double = 2.5,
    /** Double-progression rep window: climb to repMax, then add weight and reset to repMin. */
    val repMin: Int = 8,
    val repMax: Int = 12,
    /** For pure-bodyweight moves: add load once reps pass this. */
    val bodyweightRepCeiling: Int = 15,
    /** Optional, in kg. Used to make bodyweight-exercise volume comparable. */
    val userBodyweightKg: Double = 0.0,
    /** Which window the dashboard reports on. */
    val dashboardPeriod: Period = Period.DEFAULT,
) {
    val overloadLabel: String
        get() = when (overloadPeriodDays) {
            7 -> "Every 7 days"
            15 -> "Every 15 days"
            30 -> "Every month"
            else -> "Every $overloadPeriodDays days"
        }
}
