package com.gowthaman.workout

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Csv
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.parseCsv
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.data.volume
import com.gowthaman.workout.logic.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvTest {

    @Test
    fun `commas quotes and whitespace survive a round trip`() {
        val values = listOf(
            "plain",
            "with, comma",
            "with \"quotes\"",
            "both, and \"quoted\"",
            "",
            "trailing ",
        )
        val line = Csv.row(values)
        assertEquals(values, Csv.parse(line))
    }

    @Test
    fun `columns are read by name, not by position`() {
        val text = """
            date,exercise,reps,weight
            2026-11-12,Bench,8,40
        """.trimIndent()

        val row = parseCsv(text).single()

        assertEquals("Bench", row.str("exercise"))
        assertEquals(8, row.int("reps"))
        assertEquals(40.0, row.double("weight"), 0.001)
        // A column that is not there falls back instead of shifting everything along.
        assertEquals("kg", row.str("unit", "kg"))
    }

    @Test
    fun `decimal commas are accepted`() {
        val text = "weight\n\"42,5\""
        assertEquals(42.5, parseCsv(text).single().double("weight"), 0.001)
    }

    @Test
    fun `blank lines and carriage returns are tolerated`() {
        val text = "date,reps\r\n2026-11-12,8\r\n\r\n"
        assertEquals(1, parseCsv(text).size)
    }

    @Test
    fun `notes never carry a newline into a record`() {
        assertEquals("felt heavy today", Csv.clean("felt heavy\ntoday"))
    }
}

class FormatTest {

    @Test
    fun `numbers lose trailing zeros`() {
        assertEquals("40", 40.0.fmt())
        assertEquals("42.5", 42.5.fmt())
        assertEquals("0", 0.0.fmt())
        assertEquals("2.25", 2.25.fmt(2))
    }

    @Test
    fun `whole numbers keep their trailing zeros`() {
        // A tonnage of 1440 kg must not be rendered as 144.
        assertEquals("1440", 1440.0.fmt(0))
        assertEquals("100", 100.0.fmt(0))
        assertEquals("1440", 1440.0.fmt(1))
        assertEquals("10", 10.0.fmt(2))
    }

    @Test
    fun `durations read as clock time`() {
        assertEquals("0:45", formatDuration(45))
        assertEquals("30:00", formatDuration(1800))
        assertEquals("1:05:30", formatDuration(3930))
    }

    @Test
    fun `load labels distinguish bodyweight from added load`() {
        fun set(weight: Double, bodyweight: Boolean) =
            StrengthSet("x", LocalDate.now(), "Dip", 1, 8, weight, Units.KG, bodyweight)

        assertEquals("BW", set(0.0, true).loadLabel(Units.KG))
        assertEquals("BW +10 kg", set(10.0, true).loadLabel(Units.KG))
        assertEquals("40 kg", set(40.0, false).loadLabel(Units.KG))
    }

    @Test
    fun `catalog keys ignore case and padding`() {
        assertEquals(catalogKey("Bench Press"), catalogKey("  bench press "))
    }

    @Test
    fun `pounds are shown in kilograms when the unit changes`() {
        val set = StrengthSet("x", LocalDate.now(), "Bench", 1, 8, 220.0, Units.LB, false)
        assertEquals("99.8 kg", set.loadLabel(Units.KG).let { it })
    }
}

class SessionTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 12)

    private fun set(reps: Int, weight: Double, setNo: Int, bodyweight: Boolean = false) =
        StrengthSet("s$setNo", day, "Squat", setNo, reps, weight, Units.KG, bodyweight)

    @Test
    fun `uniform sets collapse to the short form`() {
        val session = listOf(set(8, 100.0, 1), set(8, 100.0, 2), set(8, 100.0, 3)).toSessions().single()
        assertEquals("3 x 8 @ 100 kg", session.summary(Units.KG))
    }

    @Test
    fun `varying reps at one load list the reps`() {
        val session = listOf(set(8, 100.0, 1), set(7, 100.0, 2), set(5, 100.0, 3)).toSessions().single()
        assertEquals("8, 7, 5 @ 100 kg", session.summary(Units.KG))
    }

    @Test
    fun `varying loads list every set`() {
        val session = listOf(set(8, 100.0, 1), set(6, 110.0, 2)).toSessions().single()
        assertEquals("8 @ 100 kg, 6 @ 110 kg", session.summary(Units.KG))
    }

    @Test
    fun `bodyweight sets add no tonnage until a bodyweight is recorded`() {
        val pullUp = set(10, 0.0, 1, bodyweight = true)
        assertEquals(0.0, pullUp.volume(Units.KG, bodyweightKg = 0.0), 0.001)
        assertEquals(750.0, pullUp.volume(Units.KG, bodyweightKg = 75.0), 0.001)
    }

    @Test
    fun `added load on a bodyweight set counts on its own`() {
        val weightedDip = set(5, 20.0, 1, bodyweight = true)
        assertEquals(100.0, weightedDip.volume(Units.KG, bodyweightKg = 0.0), 0.001)
        assertEquals(475.0, weightedDip.volume(Units.KG, bodyweightKg = 75.0), 0.001)
    }
}

class StatsTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 12) // a Thursday
    private val settings = Settings(overloadPeriodDays = 7, weightUnit = Units.KG)

    private fun set(date: LocalDate, exercise: String, reps: Int, weight: Double, id: String) =
        StrengthSet(id, date, exercise, 1, reps, weight, Units.KG, false)

    @Test
    fun `weeks start on monday`() {
        assertEquals(LocalDate.of(2026, 11, 9), Stats.weekStart(today))
        assertEquals(LocalDate.of(2026, 11, 9), Stats.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(LocalDate.of(2026, 11, 9), Stats.weekStart(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `an empty log produces an empty dashboard`() {
        val data = Stats.build(emptyList(), emptyList(), settings, today)
        assertEquals(false, data.hasData)
    }

    @Test
    fun `streak counts consecutive days back from today`() {
        val sets = listOf(
            set(today, "Squat", 5, 100.0, "a"),
            set(today.minusDays(1), "Squat", 5, 100.0, "b"),
            set(today.minusDays(2), "Squat", 5, 100.0, "c"),
            // gap on day 3
            set(today.minusDays(4), "Squat", 5, 100.0, "d"),
        )
        assertEquals(3, Stats.build(sets, emptyList(), settings, today).currentStreakDays)
    }

    @Test
    fun `a rest day today keeps yesterday's streak alive`() {
        val sets = listOf(
            set(today.minusDays(1), "Squat", 5, 100.0, "a"),
            set(today.minusDays(2), "Squat", 5, 100.0, "b"),
        )
        assertEquals(2, Stats.build(sets, emptyList(), settings, today).currentStreakDays)
    }

    @Test
    fun `weekly volume is bucketed into the last eight weeks`() {
        val sets = listOf(
            set(today, "Squat", 10, 100.0, "a"),              // this week: 1000
            set(today.minusDays(7), "Squat", 10, 50.0, "b"),  // last week: 500
        )
        val data = Stats.build(sets, emptyList(), settings, today)

        assertEquals(8, data.weeks.size)
        assertEquals(1000.0, data.volumeThisWeek, 0.001)
        assertEquals(500.0, data.volumePrevWeek, 0.001)
        assertEquals(100, data.volumeTrendPct)
        assertEquals(1000.0, data.weeks.last().volume, 0.001)
    }

    @Test
    fun `an exercise with no step up for longer than the window shows as due`() {
        val sets = listOf(
            set(today.minusDays(21), "Squat", 10, 100.0, "a"),
            set(today.minusDays(14), "Squat", 10, 100.0, "b"),
            set(today.minusDays(2), "Bench", 10, 60.0, "c"),
        )
        val data = Stats.build(sets, emptyList(), settings, today)

        assertEquals(1, data.overloadDue.size)
        assertEquals("Squat", data.overloadDue.single().exercise)
        assertEquals(21, data.overloadDue.single().daysSinceProgress)
        assertEquals("1 x 11 @ 100 kg", data.overloadDue.single().suggestion)
    }

    @Test
    fun `a single session is a baseline rather than a record`() {
        val sets = listOf(set(today.minusDays(1), "Squat", 10, 100.0, "a"))
        assertTrue(Stats.build(sets, emptyList(), settings, today).recentPrs.isEmpty())
    }

    @Test
    fun `beating an old best counts as a recent record`() {
        val sets = listOf(
            set(today.minusDays(40), "Squat", 10, 100.0, "a"),
            set(today.minusDays(3), "Squat", 10, 110.0, "b"),
        )
        val prs = Stats.build(sets, emptyList(), settings, today).recentPrs

        assertEquals(1, prs.size)
        assertEquals("Squat", prs.single().exercise)
        assertEquals(today.minusDays(3), prs.single().date)
    }

    @Test
    fun `cardio totals convert into the chosen unit`() {
        val miles = settings.copy(distanceUnit = Units.MI)
        val cardio = listOf(
            CardioEntry("a", today, "Run", 1800, 10.0, Units.KM),
        )
        val data = Stats.build(emptyList(), cardio, miles, today)

        assertEquals(1, data.cardioSessionsThisWeek)
        assertEquals(1800, data.cardioSecThisWeek)
        assertEquals(6.21, data.cardioDistanceThisWeek, 0.01)
    }
}
