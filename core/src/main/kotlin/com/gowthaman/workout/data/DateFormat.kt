package com.gowthaman.workout.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val DAY_TINY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val MONTH_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

/** "Today" / "Yesterday" / "Tomorrow" when close, an explicit date otherwise. */
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> format(DAY_SHORT)
}

fun LocalDate.fullLabel(): String = format(DAY_FULL)

fun LocalDate.shortLabel(): String = format(DAY_SHORT)

fun LocalDate.tinyLabel(): String = format(DAY_TINY)

/** "Thu" — chart axis for day-wide buckets. */
fun LocalDate.weekdayLabel(): String = format(WEEKDAY)

/** "Nov" — chart axis for month-wide buckets. */
fun LocalDate.monthLabel(): String = format(MONTH_ONLY)

/** "today", "3 days ago", "5 weeks ago" — for history lists. */
fun LocalDate.agoLabel(today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(this, today)
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 0L -> "in ${-days} day${if (days == -1L) "" else "s"}"
        days < 7L -> "$days days ago"
        days < 14L -> "1 week ago"
        days < 60L -> "${days / 7} weeks ago"
        days < 365L -> "${days / 30} months ago"
        else -> "${days / 365} year${if (days / 365 == 1L) "" else "s"} ago"
    }
}
