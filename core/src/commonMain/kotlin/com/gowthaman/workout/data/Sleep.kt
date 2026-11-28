package com.gowthaman.workout.data

import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/**
 * How a night went, on the same coarse three-point scale as everything else.
 *
 * Deliberately not a percentage or a 1-10 score. Anxiety about sleep scores is a real
 * thing, and this log exists to notice patterns, not to grade you overnight.
 */
enum class SleepQuality(val level: Int, val label: String) {
    POOR(1, "Poor"),
    OK(2, "OK"),
    GOOD(3, "Good");

    companion object {
        val DEFAULT = OK

        fun fromLevel(level: Int): SleepQuality =
            entries.firstOrNull { it.level == level } ?: DEFAULT

        /** Accepts the stored number, and the name as a courtesy to hand-edited files. */
        fun parse(raw: String): SleepQuality {
            val asNumber = raw.trim().toIntOrNull()
            if (asNumber != null) return fromLevel(asNumber)
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * One night's sleep.
 *
 * [date] is the **wake** date: a night running from Monday 23:30 to Tuesday 07:00 belongs
 * to Tuesday. That is the convention sleep trackers use, and it is what makes "I slept
 * badly, so today was rough" line up on the same row of the day view.
 *
 * At most one per date — logging again replaces it, because a night is an attribute of a
 * day rather than an event that can happen twice.
 */
data class SleepNight(
    val id: String,
    val date: LocalDate,
    val durationMin: Int,
    val quality: SleepQuality,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/** "7h 30m", or "45m" for a short night. */
fun formatSleep(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val hours = m / 60
    val mins = m % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

/**
 * Reads the shorthand people actually type: 7h30, 7:30, 7.5, 450m, 7h.
 *
 * Returns null rather than guessing, so a mistyped duration is refused instead of silently
 * recording something wrong.
 */
fun parseSleepDuration(raw: String): Int? {
    val text = raw.trim().lowercase().replace(" ", "")
    if (text.isEmpty()) return null

    // 450m / 450min
    Regex("^(\\d+)(m|min|mins)$").find(text)?.let {
        return it.groupValues[1].toIntOrNull()?.takeIf { v -> v > 0 }
    }
    // 7h30, 7h30m, 7h
    Regex("^(\\d+)h(\\d{1,2})?m?$").find(text)?.let {
        val hours = it.groupValues[1].toIntOrNull() ?: return null
        val mins = it.groupValues[2].takeIf { s -> s.isNotEmpty() }?.toIntOrNull() ?: 0
        if (mins >= 60) return null
        return (hours * 60 + mins).takeIf { v -> v > 0 }
    }
    // 7:30
    Regex("^(\\d+):(\\d{1,2})$").find(text)?.let {
        val hours = it.groupValues[1].toIntOrNull() ?: return null
        val mins = it.groupValues[2].toIntOrNull() ?: return null
        if (mins >= 60) return null
        return (hours * 60 + mins).takeIf { v -> v > 0 }
    }
    // 7.5 or 7,5 decimal hours
    text.replace(',', '.').toDoubleOrNull()?.let { hours ->
        if (hours <= 0 || hours > 24) return null
        return (hours * 60).roundToInt()
    }
    return null
}
