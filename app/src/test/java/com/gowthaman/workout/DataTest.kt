package com.gowthaman.workout

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Csv
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.parseCsv
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.data.volume
import com.gowthaman.workout.logic.BucketUnit
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
    fun `the default period is a rolling month`() {
        assertEquals(Period.MONTH, Settings().dashboardPeriod)
        assertEquals(30, Period.MONTH.days)
    }

    @Test
    fun `period figures cover a rolling window and compare to the one before it`() {
        val sets = listOf(
            set(today.minusDays(3), "Squat", 10, 100.0, "a"),   // in window:  1000
            set(today.minusDays(29), "Squat", 10, 50.0, "b"),   // in window:   500
            set(today.minusDays(31), "Squat", 10, 20.0, "c"),   // previous:    200
        )
        val data = Stats.build(sets, emptyList(), settings, today)

        assertEquals(1500.0, data.volumeThisPeriod, 0.001)
        assertEquals(200.0, data.volumePrevPeriod, 0.001)
        assertEquals(2, data.setsThisPeriod)
        assertEquals(2, data.trainingDaysThisPeriod)
    }

    @Test
    fun `the window is inclusive of exactly period days`() {
        val onEdge = listOf(set(today.minusDays(29), "Squat", 10, 100.0, "a"))
        val justOutside = listOf(set(today.minusDays(30), "Squat", 10, 100.0, "a"))

        assertEquals(1, Stats.build(onEdge, emptyList(), settings, today).setsThisPeriod)
        assertEquals(0, Stats.build(justOutside, emptyList(), settings, today).setsThisPeriod)
    }

    @Test
    fun `changing the period changes what is counted`() {
        val sets = listOf(
            set(today.minusDays(2), "Squat", 10, 100.0, "a"),
            set(today.minusDays(60), "Squat", 10, 100.0, "b"),
            set(today.minusDays(200), "Squat", 10, 100.0, "c"),
        )
        fun setsIn(period: Period) = Stats
            .build(sets, emptyList(), settings.copy(dashboardPeriod = period), today)
            .setsThisPeriod

        assertEquals(1, setsIn(Period.WEEK))
        assertEquals(1, setsIn(Period.MONTH))
        assertEquals(2, setsIn(Period.QUARTER))
        assertEquals(3, setsIn(Period.YEAR))
    }

    @Test
    fun `each period gets a chart shape that stays readable`() {
        assertEquals(BucketUnit.DAY to 7, Stats.chartShape(Period.WEEK))
        assertEquals(BucketUnit.WEEK to 8, Stats.chartShape(Period.MONTH))
        assertEquals(BucketUnit.MONTH to 6, Stats.chartShape(Period.QUARTER))
        assertEquals(BucketUnit.MONTH to 12, Stats.chartShape(Period.YEAR))
    }

    @Test
    fun `month buckets are whole weeks, oldest first`() {
        val sets = listOf(
            set(today, "Squat", 10, 100.0, "a"),              // this week: 1000
            set(today.minusDays(7), "Squat", 10, 50.0, "b"),  // last week:  500
        )
        val data = Stats.build(sets, emptyList(), settings, today)

        assertEquals(8, data.buckets.size)
        assertEquals(BucketUnit.WEEK, data.buckets.first().unit)
        assertEquals(1000.0, data.buckets.last().volume, 0.001)
        assertEquals(500.0, data.buckets[data.buckets.lastIndex - 1].volume, 0.001)
        assertTrue(data.buckets.zipWithNext().all { (a, b) -> a.start < b.start })
        assertTrue(data.buckets.all { it.start == Stats.weekStart(it.start) })
    }

    @Test
    fun `week buckets are seven days ending today`() {
        val sets = listOf(set(today, "Squat", 10, 100.0, "a"))
        val data = Stats.build(
            sets, emptyList(), settings.copy(dashboardPeriod = Period.WEEK), today
        )

        assertEquals(7, data.buckets.size)
        assertEquals(BucketUnit.DAY, data.buckets.first().unit)
        assertEquals(today, data.buckets.last().start)
        assertEquals(today.minusDays(6), data.buckets.first().start)
        assertEquals(1000.0, data.buckets.last().volume, 0.001)
    }

    @Test
    fun `year buckets are whole calendar months`() {
        val sets = listOf(set(today, "Squat", 10, 100.0, "a"))
        val data = Stats.build(
            sets, emptyList(), settings.copy(dashboardPeriod = Period.YEAR), today
        )

        assertEquals(12, data.buckets.size)
        assertEquals(BucketUnit.MONTH, data.buckets.first().unit)
        assertTrue(data.buckets.all { it.start.dayOfMonth == 1 })
        assertEquals(today.withDayOfMonth(1), data.buckets.last().start)
    }

    @Test
    fun `most logged is scoped to the selected window`() {
        val sets = listOf(
            set(today.minusDays(2), "Squat", 10, 100.0, "a"),
            set(today.minusDays(200), "Bench", 10, 100.0, "b"),
            set(today.minusDays(201), "Bench", 10, 100.0, "c"),
        )
        val month = Stats.build(sets, emptyList(), settings, today)
        val year = Stats.build(
            sets, emptyList(), settings.copy(dashboardPeriod = Period.YEAR), today
        )

        assertEquals(listOf("Squat"), month.topExercises.map { it.first })
        assertEquals(listOf("Bench", "Squat"), year.topExercises.map { it.first })
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
    fun `the records window follows the selected period`() {
        val sets = listOf(
            set(today.minusDays(300), "Squat", 10, 100.0, "a"),
            set(today.minusDays(50), "Squat", 10, 110.0, "b"),  // best, but 50 days back
        )
        fun prs(period: Period) = Stats
            .build(sets, emptyList(), settings.copy(dashboardPeriod = period), today)
            .recentPrs

        assertTrue("50 days is outside a 30-day window", prs(Period.MONTH).isEmpty())
        assertEquals(1, prs(Period.QUARTER).size)
    }

    @Test
    fun `cardio totals convert into the chosen unit`() {
        val miles = settings.copy(distanceUnit = Units.MI)
        val cardio = listOf(
            CardioEntry("a", today, "Run", 1800, 10.0, Units.KM),
        )
        val data = Stats.build(emptyList(), cardio, miles, today)

        assertEquals(1, data.cardioSessions)
        assertEquals(1800, data.cardioSec)
        assertEquals(6.21, data.cardioDistance, 0.01)
    }
}
