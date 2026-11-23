package com.gowthaman.workout

import com.gowthaman.workout.logic.ReminderSchedule
import com.gowthaman.workout.logic.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ReminderTest {

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /** Monday 23 Nov 2026. */
    private val monMorning = LocalDateTime.of(2026, 11, 23, 9, 0)

    @Test
    fun `nothing is scheduled while it is switched off`() {
        assertNull(Reminders.nextTrigger(ReminderSchedule(enabled = false, days = weekdays), monMorning))
    }

    @Test
    fun `nothing is scheduled when no day is chosen`() {
        // Switched on with every day unticked is a state the UI allows, and it has to mean
        // the same as off rather than every day.
        assertNull(Reminders.nextTrigger(ReminderSchedule(enabled = true, days = emptySet()), monMorning))
    }

    @Test
    fun `later the same day is the next trigger`() {
        val next = Reminders.nextTrigger(
            ReminderSchedule(enabled = true, hour = 20, minute = 0, days = weekdays), monMorning,
        )
        assertEquals(LocalDateTime.of(2026, 11, 23, 20, 0), next)
    }

    @Test
    fun `a time already past today rolls to the next chosen day`() {
        val monEvening = LocalDateTime.of(2026, 11, 23, 21, 30)
        val next = Reminders.nextTrigger(
            ReminderSchedule(enabled = true, hour = 20, minute = 0, days = weekdays), monEvening,
        )
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 0), next)
    }

    @Test
    fun `the exact trigger instant does not return itself`() {
        // This is called from inside the alarm it just fired. Returning the same instant
        // would re-arm for a time that has already passed, and the reminder would stop.
        val onTheDot = LocalDateTime.of(2026, 11, 23, 20, 0)
        val next = Reminders.nextTrigger(
            ReminderSchedule(enabled = true, hour = 20, minute = 0, days = weekdays), onTheDot,
        )
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 0), next)
    }

    @Test
    fun `a weekend-only schedule skips the week`() {
        val next = Reminders.nextTrigger(
            ReminderSchedule(
                enabled = true, hour = 10, minute = 30,
                days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
            monMorning,
        )
        assertEquals(LocalDateTime.of(2026, 11, 28, 10, 30), next)
    }

    @Test
    fun `one day a week, asked an hour late, comes back round next week`() {
        // The case that needs an eighth day of lookahead: today is the only chosen day and
        // its time has gone.
        val next = Reminders.nextTrigger(
            ReminderSchedule(enabled = true, hour = 8, minute = 0, days = setOf(DayOfWeek.MONDAY)),
            monMorning,
        )
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 0), next)
    }

    @Test
    fun `midnight is a usable time`() {
        val next = Reminders.nextTrigger(
            ReminderSchedule(enabled = true, hour = 0, minute = 0, days = weekdays), monMorning,
        )
        assertEquals(LocalDateTime.of(2026, 11, 24, 0, 0), next)
    }

    @Test
    fun `the time label is zero padded`() {
        assertEquals("08:05", ReminderSchedule(hour = 8, minute = 5).timeLabel)
        assertEquals("20:00", ReminderSchedule(hour = 20, minute = 0).timeLabel)
    }
}
