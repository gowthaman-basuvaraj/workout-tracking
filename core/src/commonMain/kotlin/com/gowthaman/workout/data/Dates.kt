package com.gowthaman.workout.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random

/**
 * The handful of things the JVM gave us for free and a multiplatform build does not.
 *
 * Small on purpose. Everything here replaces something that used to come from `java.*`,
 * and each one is a few lines rather than a dependency, because the alternatives are all
 * much larger than the problem.
 */

/** Today, in the device's own zone. Replaces `LocalDate.now()`. */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Now, in epoch milliseconds. Replaces `System.currentTimeMillis()`. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * A year and a month, which is how the log is filed.
 *
 * kotlinx-datetime has no equivalent, and the only things ever asked of it are "which
 * file does this date belong in" and "is this month before that one".
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {

    override fun compareTo(other: YearMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    /** The first day, used when a whole month has to become a date. */
    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

    fun plusMonths(n: Int): YearMonth {
        val zero = year * 12 + (month - 1) + n
        return YearMonth(zero.floorDiv(12), zero.mod(12) + 1)
    }

    fun minusMonths(n: Int): YearMonth = plusMonths(-n)

    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.monthNumber)
    }
}

/**
 * A short random id for a row.
 *
 * Replaces `UUID.randomUUID()`. Ids only have to be unique within one person's folder and
 * are never compared across devices, so twelve hex characters — 48 bits — is far more
 * than the job needs.
 */
fun newRowId(): String {
    val hex = "0123456789abcdef"
    return buildString(12) { repeat(12) { append(hex[Random.nextInt(16)]) } }
}

/** `"%02d"`, which commonMain has no `String.format` for. */
fun Int.pad2(): String = if (this < 10) "0$this" else toString()

/** `"%04d"`, same reason. */
internal fun Int.pad4(): String = toString().padStart(4, '0')

/**
 * Month and day names, in English.
 *
 * These used to come from `DateTimeFormatter` with the device locale, so a French phone
 * showed "nov.". Multiplatform has no locale-aware date formatting without pulling in
 * something far larger than this app, and every other string in the UI is English
 * anyway — a localised month name next to "Nothing logged today" was never consistent.
 */
internal val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

internal val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * "Mon" — for the day-of-week chips.
 *
 * Replaces `DayOfWeek.getDisplayName(TextStyle.SHORT, Locale)`, which is java.time and so
 * exists on exactly one of the platforms this now builds for. SHORT rather than NARROW
 * for the same reason as before: NARROW gives "M T W T F S S", and two of those are "T".
 */
fun DayOfWeek.shortLabel(): String = WEEKDAY_NAMES[isoDayNumber - 1]

/**
 * `"%.Nf"` — round to a fixed number of decimals and keep the trailing zeros.
 *
 * `toString()` will not do: it gives 42.5 as "42.5" but 1.0/3 as "0.3333333333333333",
 * and the whole point of this is a fixed width before [fmt] trims it back.
 */
internal fun Double.roundedTo(decimals: Int): String {
    if (decimals <= 0) return kotlin.math.round(this).toLong().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(kotlin.math.abs(this) * factor).toLong()
    val whole = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0')
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return "$sign$whole.$frac"
}
