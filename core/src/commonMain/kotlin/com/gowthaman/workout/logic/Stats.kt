package com.gowthaman.workout.logic

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StressWeight
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.e1rm
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.score
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.data.volume
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** How wide one bar of the volume chart is. */
enum class BucketUnit { DAY, WEEK, MONTH }

data class Bucket(
    val start: LocalDate,
    val unit: BucketUnit,
    val volume: Double,
    val sets: Int,
    val days: Int,
)

/** One stress label, with how many of its events were major. */
data class StressTally(val name: String, val count: Int, val major: Int)

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
    /**
     * Counts, not a mix: tags overlap (a dosa with curd is two of them) and are optional,
     * so these neither sum to [foodCount] nor to each other. [foodTaggedCount] is what
     * they are a share of, and travels with them everywhere they are shown.
     */
    val foodTagMix: List<Pair<FoodTag, Int>> = emptyList(),
    val foodTaggedCount: Int = 0,
    val topFoods: List<Pair<String, Int>> = emptyList(),
    val cleanDayStreak: Int = 0,
    val lateNightSharePct: Int? = null,
    // --- sleep, scoped to the same window ---
    val nightsLogged: Int = 0,
    val sleepAvgMin: Int = 0,
    val shortNights: Int = 0,
    val sleepQualityMix: List<Pair<SleepQuality, Int>> = emptyList(),
    val sleepBuckets: List<Bucket> = emptyList(),
    // --- stress, scoped to the same window ---
    val enhancers: List<StressTally> = emptyList(),
    val busters: List<StressTally> = emptyList(),
    val enhancerCount: Int = 0,
    val busterCount: Int = 0,
    /** Days on which you reached for at least one buster. The number you control. */
    val daysWithBuster: Int = 0,
) {
    val hasSleep: Boolean get() = nightsLogged > 0
    val hasStress: Boolean get() = enhancerCount > 0 || busterCount > 0

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
    /** Below this a night counts as short; the figure people actually act on. */
    const val SHORT_NIGHT_MIN = 6 * 60
    private const val MIN_SAMPLE_FOR_SHARE = 5

    fun weekStart(date: LocalDate): LocalDate =
        date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

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
        sleep: List<SleepNight> = emptyList(),
        stress: List<StressEvent> = emptyList(),
    ): Dashboard {
        val period = settings.dashboardPeriod
        if (strength.isEmpty() && cardio.isEmpty() && food.isEmpty() &&
            sleep.isEmpty() && stress.isEmpty()
        ) {
            return Dashboard(period = period)
        }

        val unit = settings.weightUnit
        val bw = settings.userBodyweightKg

        // Rolling windows, inclusive of today, so the previous one is always the same length.
        val windowStart = today.minus((period.days - 1).toInt(), DateTimeUnit.DAY)
        val prevStart = windowStart.minus(period.days.toInt(), DateTimeUnit.DAY)

        val trainedDays = (strength.map { it.date } + cardio.map { it.date }).distinct().sorted()

        val periodSets = strength.filter { it.date >= windowStart && it.date <= today }
        val prevSets = strength.filter { it.date >= prevStart && it.date < windowStart }
        val periodCardio = cardio.filter { it.date >= windowStart && it.date <= today }
        val periodFood = food.filter { it.date >= windowStart && it.date <= today }
        val periodSleep = sleep.filter { it.date >= windowStart && it.date <= today }
        val periodStress = stress.filter { it.date >= windowStart && it.date <= today }

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
            currentStreakDays = streak(trainedDays.toSet(), today),
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
            foodTagMix = FoodTag.entries
                .map { tag -> tag to periodFood.filter { tag in it.tags }.sumOf { it.qty } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second },
            foodTaggedCount = periodFood.filter { it.tags.isNotEmpty() }.sumOf { it.qty },
            topFoods = periodFood
                .groupBy { catalogKey(it.item) }.values
                .map { rows -> rows.first().item to rows.sumOf { it.qty } }
                .sortedByDescending { it.second }
                .take(8),
            cleanDayStreak = cleanStreak(food, today),
            lateNightSharePct = lateNightShare(periodFood),
            nightsLogged = periodSleep.size,
            sleepAvgMin = if (periodSleep.isEmpty()) 0
            else periodSleep.sumOf { it.durationMin } / periodSleep.size,
            shortNights = periodSleep.count { it.durationMin < SHORT_NIGHT_MIN },
            sleepQualityMix = SleepQuality.entries
                .map { q -> q to periodSleep.count { it.quality == q } },
            sleepBuckets = buildSleepBuckets(sleep, today, period),
            enhancers = tally(periodStress, StressDirection.ENHANCER),
            busters = tally(periodStress, StressDirection.BUSTER),
            enhancerCount = periodStress.count { it.direction == StressDirection.ENHANCER },
            busterCount = periodStress.count { it.direction == StressDirection.BUSTER },
            daysWithBuster = periodStress
                .filter { it.direction == StressDirection.BUSTER }
                .map { it.date }.distinct().size,
        )
    }

    /**
     * Stress labels in one direction, commonest first, carrying how many were major.
     *
     * Counts only. No ratio of enhancers to busters: you log stressors when you are least
     * able to log anything, so that number would read as precision it does not have.
     */
    private fun tally(events: List<StressEvent>, direction: StressDirection): List<StressTally> =
        events.filter { it.direction == direction }
            .groupBy { catalogKey(it.label) }.values
            .map { rows ->
                StressTally(
                    name = rows.first().label,
                    count = rows.size,
                    major = rows.count { it.weight == StressWeight.MAJOR },
                )
            }
            .sortedByDescending { it.count }
            .take(8)

    /** Average nightly sleep per bar, so the chart matches the volume chart's shape. */
    private fun buildSleepBuckets(
        sleep: List<SleepNight>,
        today: LocalDate,
        period: Period,
    ): List<Bucket> {
        if (sleep.isEmpty()) return emptyList()
        return bucketRanges(today, period).map { (start, end, unit) ->
            val nights = sleep.filter { it.date >= start && it.date < end }
            Bucket(
                start = start,
                unit = unit,
                // Average rather than total: a week with six nights logged should not look
                // like less sleep than a week with seven.
                volume = if (nights.isEmpty()) 0.0
                else nights.sumOf { it.durationMin }.toDouble() / nights.size,
                sets = nights.size,
                days = nights.size,
            )
        }
    }

    /** Bar boundaries for the selected period, oldest first. */
    private fun bucketRanges(
        today: LocalDate,
        period: Period,
    ): List<Triple<LocalDate, LocalDate, BucketUnit>> {
        val (bucketUnit, count) = chartShape(period)
        return (0 until count).map { index ->
            val back = count - 1 - index
            val start = when (bucketUnit) {
                BucketUnit.DAY -> today.minus(back, DateTimeUnit.DAY)
                BucketUnit.WEEK -> weekStart(today).minus(back * 7, DateTimeUnit.DAY)
                BucketUnit.MONTH ->
                    LocalDate(today.year, today.monthNumber, 1).minus(back, DateTimeUnit.MONTH)
            }
            val end = when (bucketUnit) {
                BucketUnit.DAY -> start.plus(1, DateTimeUnit.DAY)
                BucketUnit.WEEK -> start.plus(7, DateTimeUnit.DAY)
                BucketUnit.MONTH -> start.plus(1, DateTimeUnit.MONTH)
            }
            Triple(start, end, bucketUnit)
        }
    }

    /** Volume chart bars, oldest first, aligned to whole days / Mondays / 1st-of-month. */
    private fun buildBuckets(
        strength: List<StrengthSet>,
        unit: String,
        bodyweightKg: Double,
        today: LocalDate,
        period: Period,
    ): List<Bucket> = bucketRanges(today, period).map { (start, end, bucketUnit) ->
        val rows = strength.filter { it.date >= start && it.date < end }
        Bucket(
            start = start,
            unit = bucketUnit,
            volume = rows.sumOf { it.volume(unit, bodyweightKg) },
            sets = rows.size,
            days = rows.map { it.date }.distinct().size,
        )
    }

    /** Consecutive trained days ending today, or ending yesterday if today is still untouched. */
    private fun streak(trained: Set<LocalDate>, today: LocalDate): Int {
        var cursor = when {
            trained.contains(today) -> today
            trained.contains(today.minus(1, DateTimeUnit.DAY)) -> today.minus(1, DateTimeUnit.DAY)
            else -> return 0
        }
        var count = 0
        while (trained.contains(cursor)) {
            count++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
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
            byDay.containsKey(today.minus(1, DateTimeUnit.DAY)) -> today.minus(1, DateTimeUnit.DAY)
            else -> return 0
        }
        var count = 0
        while (true) {
            val day = byDay[cursor] ?: break
            if (day.any { it.scale == FoodScale.INDULGENT }) break
            count++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
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
        val zone = TimeZone.currentSystemDefault()
        val late = timed
            .filter {
                Instant.fromEpochMilliseconds(it.loggedAt).toLocalDateTime(zone).hour >= LATE_HOUR
            }
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
        if (best.date.daysUntil(today) > windowDays) return@mapNotNull null
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
        val daysSince = progressDay.daysUntil(today)
        if (daysSince < settings.overloadPeriodDays) return@mapNotNull null

        // Ask the engine what the next session should look like: advising "as of tomorrow"
        // makes today's sets count as history rather than as the session being planned.
        val target = Overload.forStrength(sessions, settings, today.plus(1, DateTimeUnit.DAY)).target
            ?: return@mapNotNull null

        DueItem(
            exercise = rows.first().exercise,
            daysSinceProgress = daysSince,
            lastDone = sessions.last().date,
            suggestion = target.label(),
        )
    }.sortedByDescending { it.daysSinceProgress }.take(6)
}
