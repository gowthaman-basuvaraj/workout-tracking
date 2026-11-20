package com.gowthaman.workout.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("workoutlog.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    /**
     * Whether the first-run nudge towards the guide has been answered.
     *
     * Deliberately not part of [Settings]: that type is published into the data folder as
     * settings.csv for desktop tools to read, and whether someone dismissed a card on
     * this phone is nobody else's business.
     */
    private val _guideSeen = MutableStateFlow(sp.getBoolean(K_GUIDE_SEEN, false))
    val guideSeen: StateFlow<Boolean> = _guideSeen

    fun markGuideSeen() {
        sp.edit().putBoolean(K_GUIDE_SEEN, true).apply()
        _guideSeen.value = true
    }

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
        const val K_GUIDE_SEEN = "guide_seen"
    }
}
