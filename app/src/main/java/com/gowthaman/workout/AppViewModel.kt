package com.gowthaman.workout

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseDef
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Period
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
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val volume: Double = 0.0,
) {
    val isEmpty: Boolean get() = strength.isEmpty() && cardio.isEmpty()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repo = Repo(app, prefs)

    val settings: StateFlow<Settings> = prefs.state
    val status: StateFlow<Repo.Status> = repo.status
    val error: StateFlow<String?> = repo.error
    val strength: StateFlow<List<StrengthSet>> = repo.strength
    val cardio: StateFlow<List<CardioEntry>> = repo.cardio
    val catalog: StateFlow<List<ExerciseDef>> = repo.catalog

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val day: StateFlow<DayView> =
        combine(repo.strength, repo.cardio, _selectedDate, prefs.state) { sets, cardio, date, cfg ->
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
                totalSets = forDay.size,
                totalReps = forDay.sumOf { it.reps },
                volume = forDay.sumOf { it.volume(cfg.weightUnit, cfg.userBodyweightKg) },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DayView(LocalDate.now()))

    val dashboard: StateFlow<Dashboard> =
        combine(repo.strength, repo.cardio, prefs.state) { sets, cardio, cfg ->
            Stats.build(sets, cardio, cfg, LocalDate.now())
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
