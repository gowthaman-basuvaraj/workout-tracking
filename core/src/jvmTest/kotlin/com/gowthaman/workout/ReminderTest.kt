package com.gowthaman.workout

import com.gowthaman.workout.logic.Reminder
import com.gowthaman.workout.logic.ReminderCodec
import com.gowthaman.workout.logic.ReminderSettings
import com.gowthaman.workout.logic.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime

class ReminderTest {

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /** Monday 23 Nov 2026. */
    private val monMorning = LocalDateTime(2026, 11, 23, 9, 0)

    private fun reminder(
        hour: Int = 20,
        minute: Int = 0,
        days: Set<DayOfWeek> = weekdays,
        label: String = "Dinner",
        id: Int = 1,
    ) = Reminder(id = id, label = label, hour = hour, minute = minute, days = days)

    // ------------------------------------------------------------ scheduling

    @Test
    fun `nothing is scheduled when no day is chosen`() {
        // Adding a reminder and unticking every day is a state the UI allows, and it has
        // to mean never rather than always.
        assertNull(Reminders.nextTrigger(reminder(days = emptySet()), monMorning))
    }

    @Test
    fun `later the same day is the next trigger`() {
        assertEquals(
            LocalDateTime(2026, 11, 23, 20, 0),
            Reminders.nextTrigger(reminder(), monMorning),
        )
    }

    @Test
    fun `a time already past today rolls to the next chosen day`() {
        assertEquals(
            LocalDateTime(2026, 11, 24, 20, 0),
            Reminders.nextTrigger(reminder(), LocalDateTime(2026, 11, 23, 21, 30)),
        )
    }

    @Test
    fun `the exact trigger instant does not return itself`() {
        // Called from inside the alarm it just fired. Returning the same instant would
        // re-arm for a time that has already passed, and the reminder would stop.
        assertEquals(
            LocalDateTime(2026, 11, 24, 20, 0),
            Reminders.nextTrigger(reminder(), LocalDateTime(2026, 11, 23, 20, 0)),
        )
    }

    @Test
    fun `a weekend-only reminder skips the week`() {
        assertEquals(
            LocalDateTime(2026, 11, 28, 10, 30),
            Reminders.nextTrigger(
                reminder(hour = 10, minute = 30, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
                monMorning,
            ),
        )
    }

    @Test
    fun `one day a week, asked an hour late, comes back round next week`() {
        // The case that needs an eighth day of lookahead.
        assertEquals(
            LocalDateTime(2026, 11, 30, 8, 0),
            Reminders.nextTrigger(reminder(hour = 8, days = setOf(DayOfWeek.MONDAY)), monMorning),
        )
    }

    @Test
    fun `meal reminders on the same day keep their own times`() {
        val breakfast = reminder(hour = 8, minute = 0, label = "Breakfast", id = 1)
        val lunch = reminder(hour = 13, minute = 0, label = "Lunch", id = 2)
        val dinner = reminder(hour = 20, minute = 0, label = "Dinner", id = 3)
        val noon = LocalDateTime(2026, 11, 23, 12, 0)

        // Breakfast has gone for today; the other two have not.
        assertEquals(LocalDateTime(2026, 11, 24, 8, 0), Reminders.nextTrigger(breakfast, noon))
        assertEquals(LocalDateTime(2026, 11, 23, 13, 0), Reminders.nextTrigger(lunch, noon))
        assertEquals(LocalDateTime(2026, 11, 23, 20, 0), Reminders.nextTrigger(dinner, noon))
    }

    // -------------------------------------------------------------- settings

    @Test
    fun `the master switch silences every reminder without deleting any`() {
        val settings = ReminderSettings(
            enabled = false,
            reminders = listOf(reminder(id = 1), reminder(id = 2, hour = 8)),
        )
        assertTrue(settings.active.isEmpty())
        assertEquals(2, settings.reminders.size)
    }

    @Test
    fun `only reminders with days chosen are active`() {
        val settings = ReminderSettings(
            enabled = true,
            reminders = listOf(reminder(id = 1), reminder(id = 2, days = emptySet())),
        )
        assertEquals(listOf(1), settings.active.map { it.id })
    }

    @Test
    fun `ids are never reused, so a pending alarm cannot land on a new reminder`() {
        val two = ReminderSettings(reminders = listOf(reminder(id = 1), reminder(id = 2)))
        assertEquals(3, two.nextId())
        // Delete the last one and add another: the new one must not inherit id 2, whose
        // alarm may still be pending.
        val afterDelete = two.copy(reminders = two.reminders.filterNot { it.id == 2 })
        assertEquals(2, afterDelete.nextId())
        val refilled = afterDelete.copy(reminders = afterDelete.reminders + reminder(id = 2))
        assertEquals(3, refilled.nextId())
    }

    @Test
    fun `a blank label still gives the notification something to say`() {
        assertEquals("Anything to write down?", reminder(label = "   ").title)
        assertEquals("Breakfast", reminder(label = "Breakfast").title)
    }

    // ------------------------------------------------------------- the codec

    @Test
    fun `reminders survive a write and read`() {
        val list = listOf(
            reminder(id = 1, label = "Breakfast", hour = 8, minute = 5, days = weekdays),
            reminder(id = 7, label = "Dinner", hour = 20, minute = 0, days = setOf(DayOfWeek.SUNDAY)),
        )
        assertEquals(list, ReminderCodec.decode(ReminderCodec.encode(list)))
    }

    @Test
    fun `a label with a comma or a quote in it survives`() {
        // The reason this is CSV rather than a home-made separator: the label is free text.
        val list = listOf(reminder(label = """Tea, then "the walk"""", days = weekdays))
        assertEquals(list, ReminderCodec.decode(ReminderCodec.encode(list)))
    }

    @Test
    fun `an empty list round-trips as nothing`() {
        assertEquals(emptyList<Reminder>(), ReminderCodec.decode(ReminderCodec.encode(emptyList())))
        assertEquals(emptyList<Reminder>(), ReminderCodec.decode(""))
    }

    @Test
    fun `a corrupt line is dropped rather than throwing`() {
        // Preferences can be edited, restored from a backup, or written by an older build.
        val good = ReminderCodec.encode(listOf(reminder(id = 4, label = "Lunch")))
        val decoded = ReminderCodec.decode("not,a,reminder\n$good\n\nalso-rubbish")
        assertEquals(listOf(4), decoded.map { it.id })
    }

    @Test
    fun `a reminder with no days round-trips as having none`() {
        val list = listOf(reminder(days = emptySet()))
        assertEquals(list, ReminderCodec.decode(ReminderCodec.encode(list)))
    }

    @Test
    fun `the time label is zero padded`() {
        assertEquals("08:05", reminder(hour = 8, minute = 5).timeLabel)
        assertEquals("20:00", reminder(hour = 20, minute = 0).timeLabel)
    }
}
