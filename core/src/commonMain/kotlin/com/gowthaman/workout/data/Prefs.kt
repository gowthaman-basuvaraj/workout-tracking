package com.gowthaman.workout.data

import com.gowthaman.workout.logic.ReminderCodec
import com.gowthaman.workout.logic.ReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The device's own settings, read once and then kept in flows.
 *
 * Everything here is the schema -- which keys exist, what they default to, how a reminder
 * list is encoded -- and none of it is platform-specific. The storage underneath is a
 * [KeyValueStore]: SharedPreferences on Android, NSUserDefaults on iOS, a file on a
 * desktop.
 *
 * The keys are the ones an existing install already uses and must not be renamed; doing
 * so would silently reset everyone's settings to the defaults below.
 */
class Prefs(private val store: KeyValueStore) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    val current: Settings get() = _state.value

    /**
     * Whether the first-run nudge towards the guide has been answered.
     *
     * Deliberately not part of [Settings]: that type is published into the data folder as
     * settings.csv for desktop tools to read, and whether someone dismissed a card on
     * this phone is nobody else's business.
     */
    private val _guideSeen = MutableStateFlow(store.getBoolean(K_GUIDE_SEEN, false))
    val guideSeen: StateFlow<Boolean> = _guideSeen

    fun markGuideSeen() {
        store.write { it.putBoolean(K_GUIDE_SEEN, true) }
        _guideSeen.value = true
    }

    /**
     * Whether the app asks for a fingerprint. Phone-local, like the reminders and for the
     * same reason: it is about this device, not about the data in the folder.
     */
    private val _lockEnabled = MutableStateFlow(store.getBoolean(K_LOCK, false))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled

    fun setLockEnabled(enabled: Boolean) {
        store.write { it.putBoolean(K_LOCK, enabled) }
        _lockEnabled.value = enabled
    }

    /**
     * The reminders, also kept out of [Settings] and so out of settings.csv.
     *
     * That file exists so desktop tools agree with the app about units and the overload
     * window. What time a particular phone buzzes is not something the CLI has any use
     * for, and one folder can be synced to more than one device.
     */
    private val _reminders = MutableStateFlow(readReminders())
    val reminders: StateFlow<ReminderSettings> = _reminders

    private fun readReminders() = ReminderSettings(
        enabled = store.getBoolean(K_REMIND_ON, false),
        reminders = ReminderCodec.decode(store.getString(K_REMIND_LIST, "").orEmpty()),
    )

    fun updateReminders(block: (ReminderSettings) -> ReminderSettings) {
        val next = block(_reminders.value)
        store.write {
            it.putBoolean(K_REMIND_ON, next.enabled)
            it.putString(K_REMIND_LIST, ReminderCodec.encode(next.reminders))
        }
        _reminders.value = next
    }

    private fun read() = Settings(
        folderUri = store.getString(K_FOLDER, null),
        overloadPeriodDays = store.getInt(K_PERIOD, 7),
        weightUnit = store.getString(K_W_UNIT, Units.KG) ?: Units.KG,
        distanceUnit = store.getString(K_D_UNIT, Units.KM) ?: Units.KM,
        weightIncrement = store.getFloat(K_INCREMENT, 2.5f).toDouble(),
        repMin = store.getInt(K_REP_MIN, 8),
        repMax = store.getInt(K_REP_MAX, 12),
        bodyweightRepCeiling = store.getInt(K_BW_CEILING, 15),
        userBodyweightKg = store.getFloat(K_BODYWEIGHT, 0f).toDouble(),
        dashboardPeriod = Period.fromName(store.getString(K_PERIOD_VIEW, null)),
    )

    fun update(block: (Settings) -> Settings) {
        val next = block(_state.value)
        store.write {
            it.putString(K_FOLDER, next.folderUri)
            it.putInt(K_PERIOD, next.overloadPeriodDays)
            it.putString(K_W_UNIT, next.weightUnit)
            it.putString(K_D_UNIT, next.distanceUnit)
            it.putFloat(K_INCREMENT, next.weightIncrement.toFloat())
            it.putInt(K_REP_MIN, next.repMin)
            it.putInt(K_REP_MAX, next.repMax)
            it.putInt(K_BW_CEILING, next.bodyweightRepCeiling)
            it.putFloat(K_BODYWEIGHT, next.userBodyweightKg.toFloat())
            it.putString(K_PERIOD_VIEW, next.dashboardPeriod.name)
        }
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
        const val K_REMIND_ON = "reminder_enabled"
        const val K_REMIND_LIST = "reminders"
        const val K_LOCK = "biometric_lock"
    }
}
