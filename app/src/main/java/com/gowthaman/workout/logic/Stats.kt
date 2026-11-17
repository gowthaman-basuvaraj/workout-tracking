package com.gowthaman.workout.logic

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.Period
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How wide one bar of the volume chart is. */
enum class BucketUnit { DAY, WEEK, MONTH }

data class Bucket(
    val start: LocalDate,
    val unit: BucketUnit,
    val volume: Double,
    val sets: Int,
    val days: Int,
)

data class PersonalRecord(
    val exercise: String,
    val date: LocalDate,
    val label: String,
    val e1rm: Double?,
)

/**
 * Everything the dashboard shows.
 *
 * Figures named `...ThisPeriod` cover the selected rolling window; `total...` and the
 * streak are all-time, and `overloadDue` follows the overload setting rather than the
 * reporting window.
 */
data class Dashboard(
    val hasData: Boolean = false,
    val period: Period = Period.DEFAULT,
    val trainingDaysThisPeriod: Int = 0,
    val currentStreakDays: Int = 0,
    val setsThisPeriod: Int = 0,
    val volumeThisPeriod: Double = 0.0,
    val volumePrevPeriod: Double = 0.0,
    val buckets: List<Bucket> = emptyList(),
    val topExercises: List<Pair<String, Int>> = emptyList(),
    val recentPrs: List<PersonalRecord> = emptyList(),
    val cardioSessions: Int = 0,
    val cardioSec: Int = 0,
    val cardioDistance: Double = 0.0,
    val overloadDue: List<DueItem> = emptyList(),
    val totalTrainingDays: Int = 0,
    val totalSets: Int = 0,
    val exerciseCount: Int = 0,
    val firstDay: LocalDate? = null,
    // --- food, all scoped to the same window ---
    val foodCount: Int = 0,
    val foodByPurpose: List<Pair<Purpose, Int>> = emptyList(),
    val foodScaleMix: List<Pair<FoodScale, Int>> = emptyList(),
    val topFoods: List<Pair<String, Int>> = emptyList(),
    val cleanDayStreak: Int = 0,
    val lateNightSharePct: Int? = null,
) {
    val hasFood: Boolean get() = foodCount > 0

    /** Share of logged items that were indulgent, as a percentage of the total. */
    val indulgentPct: Int?
        get() {
            if (foodCount <= 0) return null
            val indulgent = foodScaleMix.firstOrNull { it.first == FoodScale.INDULGENT }?.second ?: 0
            return (indulgent * 100.0 / foodCount).roundToInt()
        }

    /** Tonnage change against the preceding window of the same length. */
    val volumeTrendPct: Int?
        get() = if (volumePrevPeriod <= 0.0) null
        else (((volumeThisPeriod - volumePrevPeriod) / volumePrevPeriod) * 100).toInt()
}

object Stats {

    private const val LATE_HOUR = 21
    private const val MIN_SAMPLE_FOR_SHARE = 5

    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /**
     * Bar width and bar count for each period. Each shape lands between 6 and 12 bars,
     * which is what stays readable on a phone, and deliberately spans more history than
     * the headline figures so the chart shows a trend rather than restating them.
     */
    fun chartShape(period: Period): Pair<BucketUnit, Int> = when (period) {
        Period.WEEK -> BucketUnit.DAY to 7
        Period.MONTH -> BucketUnit.WEEK to 8
        Period.QUARTER -> BucketUnit.MONTH to 6
        Period.YEAR -> BucketUnit.MONTH to 12
    }

    fun build(
        strength: List<StrengthSet>,
        cardio: List<CardioEntry>,
        settings: Settings,
        today: LocalDate,
        food: List<FoodEntry> = emptyList(),
    ): Dashboard {
        val period = settings.dashboardPeriod
        if (strength.isEmpty() && cardio.isEmpty() && food.isEmpty()) {
            return Dashboard(period = period)
        }

        val unit = settings.weightUnit
        val bw = settings.userBodyweightKg

        // Rolling windows, inclusive of today, so the previous one is always the same length.
        val windowStart = today.minusDays(period.days - 1L)
        val prevStart = windowStart.minusDays(period.days.toLong())

        val trainedDays = (strength.map { it.date } + cardio.map { it.date }).toSortedSet()

        val periodSets = strength.filter { it.date >= windowStart && it.date <= today }
        val prevSets = strength.filter { it.date >= prevStart && it.date < windowStart }
        val periodCardio = cardio.filter { it.date >= windowStart && it.date <= today }
        val periodFood = food.filter { it.date >= windowStart && it.date <= today }

        val byExercise = strength.groupBy { catalogKey(it.exercise) }

        // Scoped to the window: "most logged" should answer "lately", not "ever".
        val topExercises = periodSets
            .groupBy { catalogKey(it.exercise) }.values
            .map { rows -> rows.first().exercise to rows.size }
            .sortedByDescending { it.second }
            .take(6)

        return Dashboard(
            hasData = true,
            period = period,
            trainingDaysThisPeriod = trainedDays.count { it >= windowStart && it <= today },
            currentStreakDays = streak(trainedDays, today),
            setsThisPeriod = periodSets.size,
            volumeThisPeriod = periodSets.sumOf { it.volume(unit, bw) },
            volumePrevPeriod = prevSets.sumOf { it.volume(unit, bw) },
            buckets = buildBuckets(strength, unit, bw, today, period),
            topExercises = topExercises,
            recentPrs = recentPrs(byExercise, unit, today, period.days.toLong()),
            cardioSessions = periodCardio.size,
            cardioSec = periodCardio.sumOf { it.durationSec },
            cardioDistance = periodCardio.sumOf {
                Units.distance(it.distance, it.distUnit, settings.distanceUnit)
            },
            overloadDue = due(byExercise, settings, today),
            totalTrainingDays = trainedDays.size,
            totalSets = strength.size,
            exerciseCount = (byExercise.keys + cardio.map { catalogKey(it.activity) }).size,
            firstDay = trainedDays.firstOrNull(),
            foodCount = periodFood.sumOf { it.qty },
            foodByPurpose = Purpose.entries
                .map { purpose -> purpose to periodFood.filter { it.purpose == purpose }.sumOf { it.qty } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second },
            foodScaleMix = FoodScale.entries
                .map { scale -> scale to periodFood.filter { it.scale == scale }.sumOf { it.qty } },
            topFoods = periodFood
                .groupBy { catalogKey(it.item) }.values
                .map { rows -> rows.first().item to rows.sumOf { it.qty } }
                .sortedByDescending { it.second }
                .take(8),
            cleanDayStreak = cleanStreak(food, today),
            lateNightSharePct = lateNightShare(periodFood),
        )
    }

    /** Volume chart bars, oldest first, aligned to whole days / Mondays / 1st-of-month. */
    private fun buildBuckets(
        strength: List<StrengthSet>,
        unit: String,
        bodyweightKg: Double,
        today: LocalDate,
        period: Period,
    ): List<Bucket> {
        val (bucketUnit, count) = chartShape(period)
        return (0 until count).map { index ->
            val back = (count - 1 - index).toLong()
            val start = when (bucketUnit) {
                BucketUnit.DAY -> today.minusDays(back)
                BucketUnit.WEEK -> weekStart(today).minusWeeks(back)
                BucketUnit.MONTH -> today.withDayOfMonth(1).minusMonths(back)
            }
            val end = when (bucketUnit) {
                BucketUnit.DAY -> start.plusDays(1)
                BucketUnit.WEEK -> start.plusWeeks(1)
                BucketUnit.MONTH -> start.plusMonths(1)
            }
            val rows = strength.filter { it.date >= start && it.date < end }
            Bucket(
                start = start,
                unit = bucketUnit,
                volume = rows.sumOf { it.volume(unit, bodyweightKg) },
                sets = rows.size,
                days = rows.map { it.date }.distinct().size,
            )
        }
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

    /**
     * Consecutive logged days, ending today or yesterday, with nothing indulgent on them.
     *
     * A day with no food logged breaks the run rather than extending it: an unlogged day
     * is unknown, not clean, and crediting it would make the number a lie.
     */
    private fun cleanStreak(food: List<FoodEntry>, today: LocalDate): Int {
        if (food.isEmpty()) return 0
        val byDay = food.groupBy { it.date }
        var cursor = when {
            byDay.containsKey(today) -> today
            byDay.containsKey(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (true) {
            val day = byDay[cursor] ?: break
            if (day.any { it.scale == FoodScale.INDULGENT }) break
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * Share of items logged after 21:00, from the timestamp already recorded on save.
     * Null below a handful of entries, where the percentage would be noise.
     */
    private fun lateNightShare(food: List<FoodEntry>): Int? {
        val timed = food.filter { it.loggedAt > 0L }
        // Quantity-weighted, like every other food figure: three coffees at 22:00 are
        // three late items, not one.
        val total = timed.sumOf { it.qty }
        if (total < MIN_SAMPLE_FOR_SHARE) return null
        val zone = ZoneId.systemDefault()
        val late = timed
            .filter { Instant.ofEpochMilli(it.loggedAt).atZone(zone).hour >= LATE_HOUR }
            .sumOf { it.qty }
        return (late * 100.0 / total).roundToInt()
    }

    /** Exercises whose all-time best set landed inside the reporting window. */
    private fun recentPrs(
        byExercise: Map<String, List<StrengthSet>>,
        unit: String,
        today: LocalDate,
        windowDays: Long,
    ): List<PersonalRecord> = byExercise.values.mapNotNull { rows ->
        val best = rows.maxByOrNull { it.score(unit) } ?: return@mapNotNull null
        if (ChronoUnit.DAYS.between(best.date, today) > windowDays) return@mapNotNull null
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
