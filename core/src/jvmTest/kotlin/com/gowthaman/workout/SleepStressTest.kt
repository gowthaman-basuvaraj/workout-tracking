package com.gowthaman.workout

import com.gowthaman.workout.data.Catalog
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StressWeight
import com.gowthaman.workout.data.formatSleep
import com.gowthaman.workout.data.parseSleepDuration
import com.gowthaman.workout.logic.Stats
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDurationTest {

    @Test
    fun `the shorthands people actually type all parse`() {
        assertEquals(450, parseSleepDuration("7h30"))
        assertEquals(450, parseSleepDuration("7h30m"))
        assertEquals(450, parseSleepDuration("7:30"))
        assertEquals(450, parseSleepDuration("7.5"))
        assertEquals(450, parseSleepDuration("7,5"))
        assertEquals(450, parseSleepDuration("450m"))
        assertEquals(420, parseSleepDuration("7h"))
        assertEquals(420, parseSleepDuration("7"))
    }

    @Test
    fun `spacing and case do not matter`() {
        assertEquals(450, parseSleepDuration("  7H 30  "))
    }

    @Test
    fun `nonsense is refused rather than guessed`() {
        // Silently recording the wrong duration is worse than refusing.
        assertNull(parseSleepDuration("eight"))
        assertNull(parseSleepDuration(""))
        assertNull(parseSleepDuration("7h99"))
        assertNull(parseSleepDuration("0"))
        assertNull(parseSleepDuration("25"))
    }

    @Test
    fun `durations format back readably`() {
        assertEquals("7h 30m", formatSleep(450))
        assertEquals("7h", formatSleep(420))
        assertEquals("45m", formatSleep(45))
    }
}

class SleepStatsTest {

    private val today: LocalDate = LocalDate(2026, 11, 17)
    private val settings = Settings(dashboardPeriod = Period.MONTH)

    private fun night(date: LocalDate, minutes: Int, quality: SleepQuality = SleepQuality.OK) =
        SleepNight("n-$date", date, minutes, quality)

    @Test
    fun `the average is reported with how many nights it came from`() {
        val nights = listOf(
            night(today, 480),
            night(today.minus(1, DateTimeUnit.DAY), 420),
            night(today.minus(2, DateTimeUnit.DAY), 360),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, sleep = nights)

        assertTrue(data.hasSleep)
        assertEquals(3, data.nightsLogged)
        assertEquals(420, data.sleepAvgMin)
    }

    @Test
    fun `short nights are counted at the six hour line`() {
        val nights = listOf(
            night(today, 359),               // under
            night(today.minus(1, DateTimeUnit.DAY), 360),  // exactly six hours is not short
            night(today.minus(2, DateTimeUnit.DAY), 300),  // under
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, sleep = nights)
        assertEquals(2, data.shortNights)
    }

    @Test
    fun `the quality mix reports every bucket`() {
        val nights = listOf(
            night(today, 480, SleepQuality.GOOD),
            night(today.minus(1, DateTimeUnit.DAY), 420, SleepQuality.GOOD),
            night(today.minus(2, DateTimeUnit.DAY), 300, SleepQuality.POOR),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, sleep = nights)

        assertEquals(3, data.sleepQualityMix.size)
        assertEquals(2, data.sleepQualityMix.first { it.first == SleepQuality.GOOD }.second)
        assertEquals(0, data.sleepQualityMix.first { it.first == SleepQuality.OK }.second)
    }

    @Test
    fun `sleep bars average rather than total, so a missed night is not a dip`() {
        // Two full weeks of 8h, but one week has six nights logged instead of seven.
        val full = (0..6).map { night(today.minus(it.toInt(), DateTimeUnit.DAY), 480) }
        val data = Stats.build(emptyList(), emptyList(), settings, today, sleep = full)
        val latest = data.sleepBuckets.last()

        assertEquals(480.0, latest.volume, 0.1)
    }

    @Test
    fun `the window applies to sleep too`() {
        val nights = listOf(night(today.minus(2, DateTimeUnit.DAY), 480), night(today.minus(60, DateTimeUnit.DAY), 480))
        fun loggedIn(period: Period) = Stats.build(
            emptyList(), emptyList(), settings.copy(dashboardPeriod = period), today, sleep = nights
        ).nightsLogged

        assertEquals(1, loggedIn(Period.MONTH))
        assertEquals(2, loggedIn(Period.QUARTER))
    }

    @Test
    fun `a sleep row survives the csv round trip`() {
        val n = SleepNight("abc", today, 450, SleepQuality.POOR, "woken at 3", 17L)
        assertEquals(n, CsvFormat.parseSleep(CsvFormat.renderSleep(listOf(n))).single())
    }
}

class StressStatsTest {

    private val today: LocalDate = LocalDate(2026, 11, 17)
    private val settings = Settings(dashboardPeriod = Period.MONTH)

    private fun event(
        date: LocalDate,
        label: String,
        direction: StressDirection,
        weight: StressWeight = StressWeight.NOTABLE,
        id: String = "$label-$date-${weight.name}",
    ) = StressEvent(id, date, label, direction, weight)

    @Test
    fun `busters and enhancers are tallied separately`() {
        val events = listOf(
            event(today, "Deadline", StressDirection.ENHANCER, StressWeight.MAJOR),
            event(today.minus(1, DateTimeUnit.DAY), "Deadline", StressDirection.ENHANCER, StressWeight.MINOR),
            event(today, "Walk", StressDirection.BUSTER, id = "w1"),
            event(today.minus(3, DateTimeUnit.DAY), "Walk", StressDirection.BUSTER, id = "w2"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, stress = events)

        assertTrue(data.hasStress)
        assertEquals(2, data.enhancerCount)
        assertEquals(2, data.busterCount)
        assertEquals("Deadline", data.enhancers.single().name)
        assertEquals("Walk", data.busters.single().name)
    }

    @Test
    fun `major events are counted within a tally`() {
        val events = listOf(
            event(today, "Deadline", StressDirection.ENHANCER, StressWeight.MAJOR, "a"),
            event(today, "Deadline", StressDirection.ENHANCER, StressWeight.MINOR, "b"),
            event(today, "Deadline", StressDirection.ENHANCER, StressWeight.MAJOR, "c"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, stress = events)

        assertEquals(3, data.enhancers.single().count)
        assertEquals(2, data.enhancers.single().major)
    }

    @Test
    fun `days with a buster counts days, not events`() {
        // Three walks, but only on two days: the number is "did I do something", not "how often".
        val events = listOf(
            event(today, "Walk", StressDirection.BUSTER, id = "a"),
            event(today, "Walk", StressDirection.BUSTER, id = "b"),
            event(today.minus(1, DateTimeUnit.DAY), "Walk", StressDirection.BUSTER, id = "c"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, stress = events)

        assertEquals(3, data.busterCount)
        assertEquals(2, data.daysWithBuster)
    }

    @Test
    fun `differently cased labels tally as one`() {
        val events = listOf(
            event(today, "Walk", StressDirection.BUSTER, id = "a"),
            event(today, "walk", StressDirection.BUSTER, id = "b"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, stress = events)
        assertEquals(1, data.busters.size)
        assertEquals(2, data.busters.single().count)
    }

    @Test
    fun `the catalog remembers direction and weight from the newest entry`() {
        val events = listOf(
            StressEvent("a", today.minus(2, DateTimeUnit.DAY), "Gym", StressDirection.ENHANCER, StressWeight.MAJOR, loggedAt = 1),
            StressEvent("b", today, "Gym", StressDirection.BUSTER, StressWeight.MINOR, loggedAt = 2),
        )
        val def = Catalog.rebuildStress(emptyList(), events).single()

        assertEquals(StressDirection.BUSTER, def.direction)
        assertEquals(StressWeight.MINOR, def.weight)
        assertEquals(2, def.useCount)
    }

    @Test
    fun `a stress row survives the csv round trip`() {
        val e = StressEvent("x", today, "Review meeting", StressDirection.ENHANCER, StressWeight.MAJOR, "ran long", 9L)
        assertEquals(e, CsvFormat.parseStress(CsvFormat.renderStress(listOf(e))).single())
    }

    @Test
    fun `hand-edited direction and weight fall back rather than throwing`() {
        val text = "id,date,label,direction,weight,notes,logged_at\n" +
            "a,2026-11-17,Thing,sideways,99,,0\n"
        val parsed = CsvFormat.parseStress(text).single()

        assertEquals(StressDirection.DEFAULT, parsed.direction)
        assertEquals(StressWeight.DEFAULT, parsed.weight)
    }
}
