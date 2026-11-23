package com.gowthaman.workout

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseDef
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StressDef
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StressWeight
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.logic.Reminder
import com.gowthaman.workout.logic.ReminderSettings
import com.gowthaman.workout.notify.ReminderAlarm
import com.gowthaman.workout.data.Prefs
import com.gowthaman.workout.data.Repo
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.data.volume
import com.gowthaman.workout.logic.Dashboard
import com.gowthaman.workout.logic.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Everything shown for one calendar day. */
data class DayView(
    val date: LocalDate,
    /** One entry per exercise trained that day, each holding its sets in order. */
    val strength: List<ExerciseSession> = emptyList(),
    val cardio: List<CardioEntry> = emptyList(),
    val food: List<FoodEntry> = emptyList(),
    val sleep: SleepNight? = null,
    val stress: List<StressEvent> = emptyList(),
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val volume: Double = 0.0,
) {
    val isEmpty: Boolean
        get() = strength.isEmpty() && cardio.isEmpty() && food.isEmpty() &&
            sleep == null && stress.isEmpty()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repo = Repo(app, prefs)

    val settings: StateFlow<Settings> = prefs.state

    val guideSeen: StateFlow<Boolean> = prefs.guideSeen

    fun markGuideSeen() = prefs.markGuideSeen()

    val reminders: StateFlow<ReminderSettings> = prefs.reminders

    /**
     * Every change re-arms every alarm, including the change that switches them off, which
     * cancels instead. Writing the preference without doing this would leave the phone
     * buzzing on yesterday's schedule.
     */
    fun updateReminders(block: (ReminderSettings) -> ReminderSettings) {
        prefs.updateReminders(block)
        ReminderAlarm.applyAll(getApplication(), prefs.reminders.value)
    }

    fun addReminder(label: String, hour: Int, minute: Int) = updateReminders { settings ->
        settings.copy(
            reminders = settings.reminders + Reminder(
                id = settings.nextId(),
                label = label,
                hour = hour,
                minute = minute,
                // Everything on by default: you have already switched reminders on and
                // asked for this one by name, so asking which days as well is a step too
                // many. Unticking is one tap.
                days = java.time.DayOfWeek.entries.toSet(),
            )
        )
    }

    fun editReminder(id: Int, block: (Reminder) -> Reminder) = updateReminders { settings ->
        settings.copy(reminders = settings.reminders.map { if (it.id == id) block(it) else it })
    }

    /** Cancels first: the alarm outlives the reminder otherwise, pending and orphaned. */
    fun removeReminder(id: Int) {
        ReminderAlarm.cancel(getApplication(), id)
        updateReminders { it.copy(reminders = it.reminders.filterNot { r -> r.id == id }) }
    }
    val status: StateFlow<Repo.Status> = repo.status
    val error: StateFlow<String?> = repo.error
    val strength: StateFlow<List<StrengthSet>> = repo.strength
    val cardio: StateFlow<List<CardioEntry>> = repo.cardio
    val catalog: StateFlow<List<ExerciseDef>> = repo.catalog
    val food: StateFlow<List<FoodEntry>> = repo.food
    val foodCatalog: StateFlow<List<FoodDef>> = repo.foodCatalog
    val sleep: StateFlow<List<SleepNight>> = repo.sleep
    val stress: StateFlow<List<StressEvent>> = repo.stress
    val stressCatalog: StateFlow<List<StressDef>> = repo.stressCatalog

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val day: StateFlow<DayView> =
        combine(
            repo.strength, repo.cardio, repo.food, repo.sleep, repo.stress,
            _selectedDate, prefs.state,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val sets = values[0] as List<StrengthSet>
            @Suppress("UNCHECKED_CAST")
            val cardio = values[1] as List<CardioEntry>
            @Suppress("UNCHECKED_CAST")
            val food = values[2] as List<FoodEntry>
            @Suppress("UNCHECKED_CAST")
            val sleep = values[3] as List<SleepNight>
            @Suppress("UNCHECKED_CAST")
            val stress = values[4] as List<StressEvent>
            val date = values[5] as LocalDate
            val cfg = values[6] as Settings
            val forDay = sets.filter { it.date == date }
            DayView(
                date = date,
                strength = forDay
                    .groupBy { catalogKey(it.exercise) }
                    .map { (_, rows) ->
                        ExerciseSession(rows.first().exercise, date, rows.sortedBy { it.setNo })
                    }
                    // Most recently logged exercise first, so the newest work is at the top.
                    .sortedByDescending { session -> session.sets.maxOf { it.loggedAt } },
                cardio = cardio.filter { it.date == date }.sortedBy { it.loggedAt },
                food = food.filter { it.date == date }.sortedBy { it.loggedAt },
                sleep = sleep.firstOrNull { it.date == date },
                stress = stress.filter { it.date == date }.sortedBy { it.loggedAt },
                totalSets = forDay.size,
                totalReps = forDay.sumOf { it.reps },
                volume = forDay.sumOf { it.volume(cfg.weightUnit, cfg.userBodyweightKg) },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DayView(LocalDate.now()))

    val dashboard: StateFlow<Dashboard> =
        combine(
            repo.strength, repo.cardio, repo.food, repo.sleep, repo.stress, prefs.state,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            Stats.build(
                strength = values[0] as List<StrengthSet>,
                cardio = values[1] as List<CardioEntry>,
                settings = values[5] as Settings,
                today = LocalDate.now(),
                food = values[2] as List<FoodEntry>,
                sleep = values[3] as List<SleepNight>,
                stress = values[4] as List<StressEvent>,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Dashboard())

    // ------------------------------------------------------------- navigation

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftDay(days: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(days)
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    // ------------------------------------------------------------------ data

    fun useFolder(uri: Uri) = repo.useFolder(uri)

    fun forgetFolder() = repo.forgetFolder()

    /** Re-reads the folder, so edits made on a laptop or by a sync client show up. */
    fun refresh() = repo.reload()

    fun dismissError() = repo.clearError()

    fun addSet(
        date: LocalDate,
        exercise: String,
        reps: Int,
        weight: Double,
        bodyweight: Boolean,
        notes: String = "",
    ) = repo.addStrengthSet(date, exercise, reps, weight, bodyweight, notes)

    fun updateSet(set: StrengthSet) = repo.updateStrengthSet(set)

    fun deleteSet(id: String) = repo.deleteStrengthSet(id)

    fun deleteExerciseOnDay(date: LocalDate, exercise: String) =
        repo.deleteStrengthGroup(date, exercise)

    fun addCardio(
        date: LocalDate,
        activity: String,
        durationSec: Int,
        distance: Double,
        notes: String = "",
    ) = repo.addCardio(date, activity, durationSec, distance, notes)

    fun updateCardio(entry: CardioEntry) = repo.updateCardio(entry)

    fun deleteCardio(id: String) = repo.deleteCardio(id)

    fun addExerciseName(name: String, kind: Kind) = repo.addCatalogEntry(name, kind)

    fun addFood(
        date: LocalDate,
        item: String,
        purpose: Purpose,
        scale: FoodScale,
        tags: Set<FoodTag> = emptySet(),
        qty: Int = 1,
        notes: String = "",
    ) = repo.addFood(date, item, purpose, scale, tags, qty, notes)

    fun updateFood(entry: FoodEntry) = repo.updateFood(entry)

    fun deleteFood(id: String) = repo.deleteFood(id)

    fun addFoodName(name: String, purpose: Purpose, scale: FoodScale) =
        repo.addFoodName(name, purpose, scale)

    fun addSleep(date: LocalDate, durationMin: Int, quality: SleepQuality, notes: String = "") =
        repo.addSleep(date, durationMin, quality, notes)

    fun deleteSleep(id: String) = repo.deleteSleep(id)

    fun addStress(
        date: LocalDate,
        label: String,
        direction: StressDirection,
        weight: StressWeight,
        notes: String = "",
    ) = repo.addStress(date, label, direction, weight, notes)

    fun deleteStress(id: String) = repo.deleteStress(id)

    fun addStressName(name: String, direction: StressDirection, weight: StressWeight) =
        repo.addStressName(name, direction, weight)

    fun renameStress(from: String, to: String) = repo.renameStress(from, to)

    fun deleteStressName(name: String, alsoDeleteHistory: Boolean) =
        repo.deleteStressName(name, alsoDeleteHistory)

    fun renameFood(from: String, to: String) = repo.renameFood(from, to)

    fun deleteFoodName(name: String, alsoDeleteHistory: Boolean) =
        repo.deleteFoodName(name, alsoDeleteHistory)

    fun renameExercise(from: String, to: String) = repo.renameExercise(from, to)

    fun deleteExercise(name: String, alsoDeleteHistory: Boolean) =
        repo.deleteExercise(name, alsoDeleteHistory)

    // -------------------------------------------------------------- settings

    fun setOverloadPeriod(days: Int) = prefs.update { it.copy(overloadPeriodDays = days) }

    /** Which rolling window the dashboard reports on. Persisted, so it survives restarts. */
    fun setDashboardPeriod(period: Period) = prefs.update { it.copy(dashboardPeriod = period) }

    fun setWeightUnit(unit: String) = prefs.update { it.copy(weightUnit = unit) }

    fun setDistanceUnit(unit: String) = prefs.update { it.copy(distanceUnit = unit) }

    fun setWeightIncrement(value: Double) =
        prefs.update { it.copy(weightIncrement = value.coerceIn(0.25, 50.0)) }

    fun setRepRange(min: Int, max: Int) = prefs.update {
        val lo = min.coerceIn(1, 100)
        it.copy(repMin = lo, repMax = max.coerceIn(lo, 100))
    }

    fun setBodyweightCeiling(reps: Int) =
        prefs.update { it.copy(bodyweightRepCeiling = reps.coerceIn(1, 200)) }

    fun setUserBodyweight(kg: Double) =
        prefs.update { it.copy(userBodyweightKg = kg.coerceIn(0.0, 400.0)) }
}

// ----------------------------------------------------------------- queries

/** Every session of one exercise, oldest first. */
fun sessionsOf(sets: List<StrengthSet>, exercise: String): List<ExerciseSession> {
    val key = catalogKey(exercise)
    return sets.filter { catalogKey(it.exercise) == key }.toSessions()
}

/** Every cardio session of one activity, oldest first. */
fun cardioOf(entries: List<CardioEntry>, activity: String): List<CardioEntry> {
    val key = catalogKey(activity)
    return entries.filter { catalogKey(it.activity) == key }.sortedBy { it.date }
}

/** Catalog names of one kind, most recently used first — the order the picker wants. */
fun pickerNames(catalog: List<ExerciseDef>, kind: Kind): List<ExerciseDef> =
    catalog.filter { it.kind == kind }
        .sortedWith(
            compareByDescending<ExerciseDef> { it.lastUsed }
                .thenByDescending { it.useCount }
                .thenBy { catalogKey(it.name) }
        )
