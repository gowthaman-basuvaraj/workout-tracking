package com.gowthaman.workout.logic

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.e1rm
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.score
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.data.volume
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeekBucket(val start: LocalDate, val volume: Double, val sets: Int, val days: Int)

data class PersonalRecord(
    val exercise: String,
    val date: LocalDate,
    val label: String,
    val e1rm: Double?,
)

data class Dashboard(
    val hasData: Boolean = false,
    val trainingDaysThisWeek: Int = 0,
    val trainingDaysLast30: Int = 0,
    val currentStreakDays: Int = 0,
    val setsThisWeek: Int = 0,
    val volumeThisWeek: Double = 0.0,
    val volumePrevWeek: Double = 0.0,
    val weeks: List<WeekBucket> = emptyList(),
    val topExercises: List<Pair<String, Int>> = emptyList(),
    val recentPrs: List<PersonalRecord> = emptyList(),
    val cardioSessionsThisWeek: Int = 0,
    val cardioSecThisWeek: Int = 0,
    val cardioDistanceThisWeek: Double = 0.0,
    val overloadDue: List<DueItem> = emptyList(),
    val totalTrainingDays: Int = 0,
    val totalSets: Int = 0,
    val exerciseCount: Int = 0,
    val firstDay: LocalDate? = null,
) {
    /** Percentage change in weekly tonnage, null when there is nothing to compare against. */
    val volumeTrendPct: Int?
        get() = if (volumePrevWeek <= 0.0) null
        else (((volumeThisWeek - volumePrevWeek) / volumePrevWeek) * 100).toInt()
}

object Stats {

    private const val WEEKS_SHOWN = 8
    private const val PR_WINDOW_DAYS = 30L

    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    fun build(
        strength: List<StrengthSet>,
        cardio: List<CardioEntry>,
        settings: Settings,
        today: LocalDate,
    ): Dashboard {
        if (strength.isEmpty() && cardio.isEmpty()) return Dashboard()

        val unit = settings.weightUnit
        val bw = settings.userBodyweightKg
        val thisWeek = weekStart(today)
        val prevWeek = thisWeek.minusWeeks(1)

        val trainedDays = (strength.map { it.date } + cardio.map { it.date }).toSortedSet()

        val thisWeekSets = strength.filter { it.date >= thisWeek }
        val prevWeekSets = strength.filter { it.date >= prevWeek && it.date < thisWeek }
        val weekCardio = cardio.filter { it.date >= thisWeek }

        val weeks = (0 until WEEKS_SHOWN).map { back ->
            val start = thisWeek.minusWeeks((WEEKS_SHOWN - 1 - back).toLong())
            val end = start.plusWeeks(1)
            val rows = strength.filter { it.date >= start && it.date < end }
            WeekBucket(
                start = start,
                volume = rows.sumOf { it.volume(unit, bw) },
                sets = rows.size,
                days = rows.map { it.date }.distinct().size,
            )
        }

        val byExercise = strength.groupBy { catalogKey(it.exercise) }

        val topExercises = byExercise.values
            .map { rows -> rows.first().exercise to rows.size }
            .sortedByDescending { it.second }
            .take(6)

        return Dashboard(
            hasData = true,
            trainingDaysThisWeek = trainedDays.count { it >= thisWeek },
            trainingDaysLast30 = trainedDays.count { it >= today.minusDays(29) && it <= today },
            currentStreakDays = streak(trainedDays, today),
            setsThisWeek = thisWeekSets.size,
            volumeThisWeek = thisWeekSets.sumOf { it.volume(unit, bw) },
            volumePrevWeek = prevWeekSets.sumOf { it.volume(unit, bw) },
            weeks = weeks,
            topExercises = topExercises,
            recentPrs = recentPrs(byExercise, unit, today),
            cardioSessionsThisWeek = weekCardio.size,
            cardioSecThisWeek = weekCardio.sumOf { it.durationSec },
            cardioDistanceThisWeek = weekCardio.sumOf {
                Units.distance(it.distance, it.distUnit, settings.distanceUnit)
            },
            overloadDue = due(byExercise, settings, today),
            totalTrainingDays = trainedDays.size,
            totalSets = strength.size,
            exerciseCount = (byExercise.keys + cardio.map { catalogKey(it.activity) }).size,
            firstDay = trainedDays.firstOrNull(),
        )
    }

    /** Consecutive trained days ending today, or ending yesterday if today is still untouched. */
    private fun streak(trained: Set<LocalDate>, today: LocalDate): Int {
        var cursor = when {
            trained.contains(today) -> today
            trained.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (trained.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Exercises whose all-time best set landed inside the PR window. */
    private fun recentPrs(
        byExercise: Map<String, List<StrengthSet>>,
        unit: String,
        today: LocalDate,
    ): List<PersonalRecord> = byExercise.values.mapNotNull { rows ->
        val best = rows.maxByOrNull { it.score(unit) } ?: return@mapNotNull null
        if (ChronoUnit.DAYS.between(best.date, today) > PR_WINDOW_DAYS) return@mapNotNull null
        // A single-session exercise is a baseline, not a record worth celebrating.
        if (rows.map { it.date }.distinct().size < 2) return@mapNotNull null
        PersonalRecord(
            exercise = best.exercise,
            date = best.date,
            label = "${best.reps} x ${best.loadLabel(unit)}",
            e1rm = best.e1rm(unit),
        )
    }.sortedByDescending { it.date }.take(5)

    /** Exercises where the overload window has elapsed since the last real improvement. */
    private fun due(
        byExercise: Map<String, List<StrengthSet>>,
        settings: Settings,
        today: LocalDate,
    ): List<DueItem> = byExercise.values.mapNotNull { rows ->
        val sessions: List<ExerciseSession> = rows.toSessions().filter { it.date <= today }
        if (sessions.isEmpty()) return@mapNotNull null

        val progressDay = Overload.lastProgressDate(sessions, settings.weightUnit)
            ?: return@mapNotNull null
        val daysSince = ChronoUnit.DAYS.between(progressDay, today).toInt()
        if (daysSince < settings.overloadPeriodDays) return@mapNotNull null

        // Ask the engine what the next session should look like: advising "as of tomorrow"
        // makes today's sets count as history rather than as the session being planned.
        val target = Overload.forStrength(sessions, settings, today.plusDays(1)).target
            ?: return@mapNotNull null

        DueItem(
            exercise = rows.first().exercise,
            daysSinceProgress = daysSince,
            lastDone = sessions.last().date,
            suggestion = target.label(),
        )
    }.sortedByDescending { it.daysSinceProgress }.take(6)
}
