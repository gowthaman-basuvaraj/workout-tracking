package com.gowthaman.workout.logic

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * When, if ever, the app should put a notification up.
 *
 * Off by default and empty by default: a log that starts nagging the day you install it
 * has misunderstood itself. Nothing here fires until someone goes to Settings and says so.
 */
data class ReminderSchedule(
    val enabled: Boolean = false,
    /** 0-23, local time. */
    val hour: Int = 20,
    val minute: Int = 0,
    /** Days it may fire on. Empty is the same as off, and is the starting state. */
    val days: Set<DayOfWeek> = emptySet(),
) {
    val isActive: Boolean get() = enabled && days.isNotEmpty()

    /** "20:00", for the settings row. */
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

object Reminders {

    /**
     * The next moment this schedule is due, strictly after [now]. Null when it never is.
     *
     * Strictly after matters: this is called again from inside the alarm it just fired, so
     * an "at or after" comparison would hand back the same instant and the alarm would
     * re-arm for a time that has already passed.
     *
     * Eight days are tried rather than seven. Seven would be enough for the day itself,
     * but today is only a candidate if its time has not yet passed -- so a schedule with
     * one day a week, asked on that day an hour late, needs the eighth to come back round
     * to it.
     */
    fun nextTrigger(schedule: ReminderSchedule, now: LocalDateTime): LocalDateTime? {
        if (!schedule.isActive) return null
        val today = now.toLocalDate()
        for (offset in 0..7) {
            val day = today.plusDays(offset.toLong())
            if (day.dayOfWeek !in schedule.days) continue
            val at = day.atTime(schedule.hour, schedule.minute)
            if (at.isAfter(now)) return at
        }
        return null
    }
}
