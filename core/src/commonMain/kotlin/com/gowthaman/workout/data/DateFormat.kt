package com.gowthaman.workout.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private fun LocalDate.weekday(): String = WEEKDAY_NAMES[dayOfWeek.isoDayNumber - 1]
private fun LocalDate.monthName(): String = MONTH_NAMES[monthNumber - 1]

/** "Today" / "Yesterday" / "Tomorrow" when close, an explicit date otherwise. */
fun LocalDate.relativeLabel(today: LocalDate = today()): String = when (this) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
    else -> shortLabel()
}

/** "Thu 28 Nov 2026" */
fun LocalDate.fullLabel(): String = "${weekday()} $dayOfMonth ${monthName()} $year"

/** "Thu 28 Nov" */
fun LocalDate.shortLabel(): String = "${weekday()} $dayOfMonth ${monthName()}"

/** "28 Nov" */
fun LocalDate.tinyLabel(): String = "$dayOfMonth ${monthName()}"

/** "Thu" — chart axis for day-wide buckets. */
fun LocalDate.weekdayLabel(): String = weekday()

/** "Nov" — chart axis for month-wide buckets. */
fun LocalDate.monthLabel(): String = monthName()

/** "today", "3 days ago", "5 weeks ago" — for history lists. */
fun LocalDate.agoLabel(today: LocalDate = today()): String {
    val days = this.daysUntil(today).toLong()
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
