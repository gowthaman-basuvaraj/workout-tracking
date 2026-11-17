package com.gowthaman.workout.logic

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.score
import com.gowthaman.workout.data.weightIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

/** What the app proposes you do today for one strength exercise. */
data class StrengthTarget(
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val unit: String,
    val bodyweight: Boolean,
)

data class StrengthAdvice(
    /** Previous sessions, most recent first — this is the "what did I do last time" panel. */
    val recent: List<ExerciseSession>,
    val bestSet: StrengthSet?,
    val target: StrengthTarget?,
    /** True when the target is a step up, false when it is a deliberate repeat. */
    val isOverload: Boolean,
    val daysSinceLast: Int?,
    val daysSinceProgress: Int?,
    val daysUntilOverload: Int,
    val headline: String,
    val reason: String,
) {
    val hasHistory: Boolean get() = recent.isNotEmpty()
}

data class CardioTarget(
    val durationSec: Int,
    val distance: Double,
    val distUnit: String,
)

data class CardioAdvice(
    val recent: List<CardioEntry>,
    val target: CardioTarget?,
    val isOverload: Boolean,
    val daysSinceLast: Int?,
    val daysUntilOverload: Int,
    val headline: String,
    val reason: String,
) {
    val hasHistory: Boolean get() = recent.isNotEmpty()
}

/** One exercise whose overload window has elapsed. Drives the dashboard's "due" list. */
data class DueItem(
    val exercise: String,
    val daysSinceProgress: Int,
    val lastDone: LocalDate,
    val suggestion: String,
)

/**
 * Double progression, gated by a period the user chooses in Settings.
 *
 * Within the window the app asks you to repeat what you did, because adding load every
 * session is how people stall. Once the window has elapsed since your last actual
 * improvement, it proposes the smallest next step: one more rep until the top of your rep
 * range, then one weight increment with reps reset to the bottom of the range.
 */
object Overload {

    private const val RECENT_SESSIONS = 5

    fun forStrength(
        history: List<ExerciseSession>,
        settings: Settings,
        today: LocalDate,
    ): StrengthAdvice {
        val unit = settings.weightUnit
        val past = history.filter { it.date < today }.sortedBy { it.date }

        if (past.isEmpty()) {
            return StrengthAdvice(
                recent = emptyList(),
                bestSet = null,
                target = null,
                isOverload = false,
                daysSinceLast = null,
                daysSinceProgress = null,
                daysUntilOverload = 0,
                headline = "No history yet",
                reason = "Log a set you can finish comfortably — that becomes the baseline " +
                    "everything else is measured against.",
            )
        }

        val last = past.last()
        val top = last.topSet(unit)
        val bestSet = past.flatMap { it.sets }.maxByOrNull { it.score(unit) }
        val progressDate = lastProgressDate(past, unit) ?: last.date
        val daysSinceLast = ChronoUnit.DAYS.between(last.date, today).toInt()
        val daysSinceProgress = ChronoUnit.DAYS.between(progressDate, today).toInt()
        val due = daysSinceProgress >= settings.overloadPeriodDays
        val daysUntil = (settings.overloadPeriodDays - daysSinceProgress).coerceAtLeast(0)

        if (top == null) {
            return StrengthAdvice(
                recent = past.asReversed().take(RECENT_SESSIONS),
                bestSet = bestSet,
                target = null,
                isOverload = false,
                daysSinceLast = daysSinceLast,
                daysSinceProgress = daysSinceProgress,
                daysUntilOverload = daysUntil,
                headline = "No target",
                reason = "The last session has no usable sets.",
            )
        }

        val lastWeight = top.weightIn(unit)
        val sets = last.sets.size.coerceAtLeast(1)

        val (target, reason) = if (!due) {
            StrengthTarget(sets, top.reps, lastWeight, unit, top.bodyweight) to
                buildString {
                    append("Repeat last session to bank the progress. ")
                    append(
                        if (daysUntil == 0) "Overload is due next session."
                        else "Next overload in $daysUntil day${plural(daysUntil)} " +
                            "(${settings.overloadLabel.lowercase()})."
                    )
                }
        } else {
            step(top, lastWeight, sets, settings, unit, daysSinceProgress)
        }

        return StrengthAdvice(
            recent = past.asReversed().take(RECENT_SESSIONS),
            bestSet = bestSet,
            target = target,
            isOverload = due,
            daysSinceLast = daysSinceLast,
            daysSinceProgress = daysSinceProgress,
            daysUntilOverload = daysUntil,
            headline = target.label(),
            reason = reason,
        )
    }

    /** The smallest sensible next step from [top]. */
    private fun step(
        top: StrengthSet,
        lastWeight: Double,
        sets: Int,
        settings: Settings,
        unit: String,
        daysSinceProgress: Int,
    ): Pair<StrengthTarget, String> {
        // The increment is configured in the display unit, so it needs no conversion.
        val incrementInUnit = settings.weightIncrement.takeIf { it > 0.0 } ?: 2.5
        val sinceText = "$daysSinceProgress day${plural(daysSinceProgress)} since your last step up"
        val pureBodyweight = top.bodyweight && lastWeight <= 0.0

        return when {
            pureBodyweight && top.reps < settings.bodyweightRepCeiling -> {
                StrengthTarget(sets, top.reps + 1, 0.0, unit, true) to
                    "+1 rep per set — $sinceText."
            }

            pureBodyweight -> {
                StrengthTarget(sets, settings.repMin, incrementInUnit, unit, true) to
                    "You are past ${settings.bodyweightRepCeiling} bodyweight reps, so add " +
                    "${incrementInUnit.fmt()} $unit and drop back to ${settings.repMin} reps — $sinceText."
            }

            top.reps < settings.repMax -> {
                StrengthTarget(sets, top.reps + 1, lastWeight, unit, top.bodyweight) to
                    "+1 rep at the same load — $sinceText. Weight goes up once you reach " +
                    "${settings.repMax} reps."
            }

            else -> {
                StrengthTarget(
                    sets = sets,
                    reps = settings.repMin,
                    weight = lastWeight + incrementInUnit,
                    unit = unit,
                    bodyweight = top.bodyweight,
                ) to "You hit the top of your ${settings.repMin}-${settings.repMax} rep range, so " +
                    "+${incrementInUnit.fmt()} $unit and reset to ${settings.repMin} reps — $sinceText."
            }
        }
    }

    /**
     * The last day this exercise actually improved — the day a set beat every set before it.
     * The overload clock runs from here rather than from the last session, so skipped weeks
     * do not quietly reset your progression.
     */
    fun lastProgressDate(sessions: List<ExerciseSession>, unit: String): LocalDate? {
        var best = Double.NEGATIVE_INFINITY
        var day: LocalDate? = null
        for (session in sessions.sortedBy { it.date }) {
            val top = session.sets.maxOfOrNull { it.score(unit) } ?: continue
            if (top > best + 1e-9) {
                best = top
                day = session.date
            }
        }
        return day
    }

    // ----------------------------------------------------------------- cardio

    fun forCardio(
        history: List<CardioEntry>,
        settings: Settings,
        today: LocalDate,
    ): CardioAdvice {
        val unit = settings.distanceUnit
        val past = history.filter { it.date < today }.sortedBy { it.date }

        if (past.isEmpty()) {
            return CardioAdvice(
                recent = emptyList(),
                target = null,
                isOverload = false,
                daysSinceLast = null,
                daysUntilOverload = 0,
                headline = "No history yet",
                reason = "Log an easy session first — that becomes your baseline.",
            )
        }

        val last = past.last()
        val progressDate = lastCardioProgressDate(past, unit) ?: last.date
        val daysSinceLast = ChronoUnit.DAYS.between(last.date, today).toInt()
        val daysSinceProgress = ChronoUnit.DAYS.between(progressDate, today).toInt()
        val due = daysSinceProgress >= settings.overloadPeriodDays
        val daysUntil = (settings.overloadPeriodDays - daysSinceProgress).coerceAtLeast(0)

        val lastDistance = Units.distance(last.distance, last.distUnit, unit)

        if (!due) {
            return CardioAdvice(
                recent = past.asReversed().take(RECENT_SESSIONS),
                target = CardioTarget(last.durationSec, lastDistance, unit),
                isOverload = false,
                daysSinceLast = daysSinceLast,
                daysUntilOverload = daysUntil,
                headline = cardioLabel(last.durationSec, lastDistance, unit),
                reason = if (daysUntil == 0) "Match last session. Overload is due next time."
                else "Match last session. Next overload in $daysUntil day${plural(daysUntil)}.",
            )
        }

        // Push whichever metric this activity is actually tracked by.
        val sinceText = "$daysSinceProgress day${plural(daysSinceProgress)} since your last step up"
        return if (lastDistance > 0.0) {
            val stepped = lastDistance + distanceStep(lastDistance)
            CardioAdvice(
                recent = past.asReversed().take(RECENT_SESSIONS),
                target = CardioTarget(last.durationSec, stepped, unit),
                isOverload = true,
                daysSinceLast = daysSinceLast,
                daysUntilOverload = 0,
                headline = cardioLabel(last.durationSec, stepped, unit),
                reason = "+${distanceStep(lastDistance).fmt(2)} $unit on last time's distance — $sinceText.",
            )
        } else {
            val stepped = last.durationSec + durationStep(last.durationSec)
            CardioAdvice(
                recent = past.asReversed().take(RECENT_SESSIONS),
                target = CardioTarget(stepped, 0.0, unit),
                isOverload = true,
                daysSinceLast = daysSinceLast,
                daysUntilOverload = 0,
                headline = cardioLabel(stepped, 0.0, unit),
                reason = "+${durationStep(last.durationSec) / 60} min on last time — $sinceText.",
            )
        }
    }

    private fun lastCardioProgressDate(history: List<CardioEntry>, unit: String): LocalDate? {
        var best = Double.NEGATIVE_INFINITY
        var day: LocalDate? = null
        for (entry in history) {
            val metric = Units.distance(entry.distance, entry.distUnit, unit)
                .takeIf { it > 0.0 } ?: (entry.durationSec / 60.0)
            if (metric > best + 1e-9) {
                best = metric
                day = entry.date
            }
        }
        return day
    }

    /** ~5% of current distance, never smaller than 0.1 and rounded to something loggable. */
    private fun distanceStep(distance: Double): Double =
        max(0.1, (distance * 0.05 * 10).roundToInt() / 10.0)

    /** ~5% of current duration, rounded to half-minutes, never smaller than a minute. */
    private fun durationStep(durationSec: Int): Int =
        max(60, ((durationSec * 0.05) / 30.0).roundToInt() * 30)

    private fun cardioLabel(durationSec: Int, distance: Double, unit: String): String = when {
        durationSec > 0 && distance > 0 -> "${formatDuration(durationSec)} · ${distance.fmt(2)} $unit"
        distance > 0 -> "${distance.fmt(2)} $unit"
        durationSec > 0 -> formatDuration(durationSec)
        else -> "—"
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"
}

fun StrengthTarget.label(): String {
    val load = when {
        bodyweight && weight <= 0.0 -> "BW"
        bodyweight -> "BW +${weight.fmt()} $unit"
        weight <= 0.0 -> null
        else -> "${weight.fmt()} $unit"
    }
    val base = "$sets x $reps"
    return if (load == null) base else "$base @ $load"
}
