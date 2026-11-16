package com.gowthaman.workout.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("workoutlog.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    val current: Settings get() = _state.value

    private fun read() = Settings(
        folderUri = sp.getString(K_FOLDER, null),
        overloadPeriodDays = sp.getInt(K_PERIOD, 7),
        weightUnit = sp.getString(K_W_UNIT, Units.KG) ?: Units.KG,
        distanceUnit = sp.getString(K_D_UNIT, Units.KM) ?: Units.KM,
        weightIncrement = sp.getFloat(K_INCREMENT, 2.5f).toDouble(),
        repMin = sp.getInt(K_REP_MIN, 8),
        repMax = sp.getInt(K_REP_MAX, 12),
        bodyweightRepCeiling = sp.getInt(K_BW_CEILING, 15),
        userBodyweightKg = sp.getFloat(K_BODYWEIGHT, 0f).toDouble(),
        dashboardPeriod = Period.fromName(sp.getString(K_PERIOD_VIEW, null)),
    )

    fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        sp.edit()
            .putString(K_FOLDER, next.folderUri)
            .putInt(K_PERIOD, next.overloadPeriodDays)
            .putString(K_W_UNIT, next.weightUnit)
            .putString(K_D_UNIT, next.distanceUnit)
            .putFloat(K_INCREMENT, next.weightIncrement.toFloat())
            .putInt(K_REP_MIN, next.repMin)
            .putInt(K_REP_MAX, next.repMax)
            .putInt(K_BW_CEILING, next.bodyweightRepCeiling)
            .putFloat(K_BODYWEIGHT, next.userBodyweightKg.toFloat())
            .putString(K_PERIOD_VIEW, next.dashboardPeriod.name)
            .apply()
        _state.value = next
    }

    private companion object {
        const val K_FOLDER = "folder_uri"
        const val K_PERIOD = "overload_period_days"
        const val K_W_UNIT = "weight_unit"
        const val K_D_UNIT = "distance_unit"
        const val K_INCREMENT = "weight_increment"
        const val K_REP_MIN = "rep_min"
        const val K_REP_MAX = "rep_max"
        const val K_BW_CEILING = "bw_rep_ceiling"
        const val K_BODYWEIGHT = "user_bodyweight_kg"
        const val K_PERIOD_VIEW = "dashboard_period"
    }
}
