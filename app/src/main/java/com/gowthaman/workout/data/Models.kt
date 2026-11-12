package com.gowthaman.workout.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Kind { STRENGTH, CARDIO }

/** Weight units. Stored per row so historical data stays exactly as it was entered. */
object Units {
    const val KG = "kg"
    const val LB = "lb"
    const val KM = "km"
    const val MI = "mi"

    private const val LB_PER_KG = 2.2046226218
    private const val MI_PER_KM = 0.621371192

    fun weight(value: Double, from: String, to: String): Double = when {
        from == to -> value
        from == KG && to == LB -> value * LB_PER_KG
        from == LB && to == KG -> value / LB_PER_KG
        else -> value
    }

    fun distance(value: Double, from: String, to: String): Double = when {
        from == to -> value
        from == KM && to == MI -> value * MI_PER_KM
        from == MI && to == KM -> value / MI_PER_KM
        else -> value
    }
}

/** One set of a strength exercise. */
data class StrengthSet(
    val id: String,
    val date: LocalDate,
    val exercise: String,
    val setNo: Int,
    val reps: Int,
    /** Added load. 0.0 means unweighted (pure bodyweight when [bodyweight] is true). */
    val weight: Double,
    val unit: String,
    val bodyweight: Boolean,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/** One cardio session. */
data class CardioEntry(
    val id: String,
    val date: LocalDate,
    val activity: String,
    val durationSec: Int,
    /** 0.0 when distance was not tracked. */
    val distance: Double,
    val distUnit: String,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/** An entry in the auto-populated exercise catalog. */
data class ExerciseDef(
    val name: String,
    val kind: Kind,
    val createdAt: Long = 0L,
    val lastUsed: LocalDate? = null,
    val useCount: Int = 0,
)

/** Catalog lookups are case/whitespace insensitive so "Bench Press" and "bench press" stay one exercise. */
fun catalogKey(name: String): String = name.trim().lowercase(Locale.ROOT)

fun StrengthSet.weightIn(unit: String): Double = Units.weight(weight, this.unit, unit)

/** Estimated 1RM (Epley). Null for unweighted sets, where reps are the only signal. */
fun StrengthSet.e1rm(unit: String): Double? =
    if (weight <= 0.0) null else weightIn(unit) * (1.0 + reps / 30.0)

/** Single comparable number for "was this set better than that one". */
fun StrengthSet.score(unit: String): Double = e1rm(unit) ?: reps.toDouble()

/**
 * Tonnage for this set, in [unit].
 *
 * Pure bodyweight sets have no load to multiply by, so they contribute nothing until the
 * user records their bodyweight in Settings — at which point the moved mass is real and
 * counts. Reporting reps as kilograms instead would quietly corrupt every volume total.
 */
fun StrengthSet.volume(unit: String, bodyweightKg: Double): Double {
    val added = weightIn(unit)
    val base = if (bodyweight && bodyweightKg > 0.0) Units.weight(bodyweightKg, Units.KG, unit) else 0.0
    val load = added + base
    return if (load <= 0.0) 0.0 else reps * load
}

fun StrengthSet.loadLabel(displayUnit: String): String {
    val w = weightIn(displayUnit)
    return when {
        bodyweight && w <= 0.0 -> "BW"
        bodyweight -> "BW +${w.fmt()} $displayUnit"
        w <= 0.0 -> "—"
        else -> "${w.fmt()} $displayUnit"
    }
}

/** All sets of one exercise on one day. */
data class ExerciseSession(
    val exercise: String,
    val date: LocalDate,
    val sets: List<StrengthSet>,
) {
    fun topSet(unit: String): StrengthSet? = sets.maxByOrNull { it.score(unit) }
    val totalReps: Int get() = sets.sumOf { it.reps }
    fun volume(unit: String, bodyweightKg: Double): Double = sets.sumOf { it.volume(unit, bodyweightKg) }

    /** "3 x 8 @ 40 kg" when uniform, otherwise "8, 7, 6 @ 40 kg" or a per-set list. */
    fun summary(unit: String): String {
        if (sets.isEmpty()) return ""
        val loads = sets.map { it.loadLabel(unit) }.distinct()
        val reps = sets.map { it.reps }
        return if (loads.size == 1) {
            val load = loads.single()
            val repPart = if (reps.distinct().size == 1) "${sets.size} x ${reps.first()}"
            else reps.joinToString(", ")
            if (load == "—") repPart else "$repPart @ $load"
        } else {
            sets.joinToString(", ") { "${it.reps} @ ${it.loadLabel(unit)}" }
        }
    }
}

fun List<StrengthSet>.toSessions(): List<ExerciseSession> =
    groupBy { it.date }
        .map { (date, sets) ->
            ExerciseSession(sets.first().exercise, date, sets.sortedBy { it.setNo })
        }
        .sortedBy { it.date }

/** Compact number text: 40.0 -> "40", 42.5 -> "42.5", 1440.0 at 0 decimals -> "1440". */
fun Double.fmt(decimals: Int = 1): String {
    if (!isFinite()) return "0"
    val rounded = String.format(Locale.US, "%.${decimals}f", this)
    // Only trim inside a fraction: "1440" must not become "144".
    if (!rounded.contains('.')) return rounded
    return rounded.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

fun Double.nearlyEquals(other: Double, eps: Double = 1e-6): Boolean = abs(this - other) < eps

fun formatDuration(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

/** "1h 05m" / "42m" — for totals rather than per-session times. */
fun formatDurationLong(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = ((s % 3600) / 60.0).roundToInt()
    return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m) else "${m}m"
}
