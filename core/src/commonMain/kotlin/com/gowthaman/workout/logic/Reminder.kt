package com.gowthaman.workout.logic

import com.gowthaman.workout.data.Csv
import com.gowthaman.workout.data.pad2
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * One nudge: what it is called, when it goes off, and on which days.
 *
 * Several of these rather than one, because the times you might want reminding at are not
 * one time: breakfast, lunch and dinner are three different questions, and a weekday
 * commute is not a Sunday morning.
 *
 * [id] is small, stable and never reused within a device. It addresses both the pending
 * alarm and the notification, so an edit replaces rather than duplicates, and a deleted
 * reminder's alarm cannot post under someone else's name.
 */
data class Reminder(
    val id: Int,
    val label: String,
    /** 0-23, local time. */
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
) {
    /** A reminder with no days chosen is a reminder that never fires. */
    val isActive: Boolean get() = days.isNotEmpty()

    /** "20:00", for the settings row. */
    val timeLabel: String get() = "${hour.pad2()}:${minute.pad2()}"

    /** What the notification is titled. A blank label still has to say something. */
    val title: String get() = label.trim().ifBlank { "Anything to write down?" }
}

/**
 * The whole reminder setting: one switch over a list.
 *
 * The switch exists separately from the list so turning everything off for a week does not
 * mean deleting four reminders and typing them back in afterwards.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
) {
    /** The ones that will actually fire. Everything else is scheduled as "cancel". */
    val active: List<Reminder>
        get() = if (enabled) reminders.filter { it.isActive } else emptyList()

    val hasAny: Boolean get() = reminders.isNotEmpty()

    /** Small, stable, and never reused: ids address alarms that may already be pending. */
    fun nextId(): Int = (reminders.maxOfOrNull { it.id } ?: 0) + 1
}

object Reminders {

    /**
     * The next moment [reminder] is due, strictly after [now]. Null when it never is.
     *
     * Strictly after matters: this is called again from inside the alarm it just fired, so
     * an "at or after" comparison would hand back the same instant and the alarm would
     * re-arm for a time that has already passed.
     *
     * Eight days are tried rather than seven. Seven would be enough for the day itself,
     * but today is only a candidate if its time has not yet passed -- so a reminder set
     * for one day a week, asked on that day an hour late, needs the eighth to come back
     * round to it.
     */
    fun nextTrigger(reminder: Reminder, now: LocalDateTime): LocalDateTime? {
        if (!reminder.isActive) return null
        val today = now.date
        for (offset in 0..7) {
            val day = today.plus(offset, DateTimeUnit.DAY)
            if (day.dayOfWeek !in reminder.days) continue
            val at = LocalDateTime(day, LocalTime(reminder.hour, reminder.minute))
            if (at > now) return at
        }
        return null
    }

    /** Suggestions offered when adding, so the common case is one tap and no typing. */
    val SUGGESTIONS: List<Pair<String, Pair<Int, Int>>> = listOf(
        "Breakfast" to (8 to 0),
        "Lunch" to (13 to 0),
        "Dinner" to (20 to 0),
        "Bedtime" to (22 to 30),
    )
}

/**
 * Reminders as text, for the one preference that holds them.
 *
 * CSV because the app already has a reader and writer for it that handles a label
 * containing a comma, a quote or anything else someone types. A home-made separator would
 * be one more thing to get wrong, on a value that is free text.
 */
object ReminderCodec {

    private const val DAY_SEPARATOR = '|'

    fun encode(reminders: List<Reminder>): String = reminders.joinToString("\n") { r ->
        Csv.row(
            r.id.toString(),
            Csv.clean(r.label),
            r.hour.toString(),
            r.minute.toString(),
            DayOfWeek.entries.filter { it in r.days }.joinToString(DAY_SEPARATOR.toString()) { it.name },
        )
    }

    fun decode(text: String): List<Reminder> = Csv.lines(text).mapNotNull { line ->
        val f = Csv.parse(line)
        if (f.size < 5) return@mapNotNull null
        val id = f[0].trim().toIntOrNull() ?: return@mapNotNull null
        Reminder(
            id = id,
            label = f[1],
            hour = f[2].trim().toIntOrNull()?.coerceIn(0, 23) ?: return@mapNotNull null,
            minute = f[3].trim().toIntOrNull()?.coerceIn(0, 59) ?: return@mapNotNull null,
            days = f[4].split(DAY_SEPARATOR)
                .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name.trim() } }
                .toSet(),
        )
    }
}
