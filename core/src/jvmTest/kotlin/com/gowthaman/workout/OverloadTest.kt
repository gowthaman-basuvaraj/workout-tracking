package com.gowthaman.workout

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.toSessions
import com.gowthaman.workout.logic.Overload
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverloadTest {

    private val today: LocalDate = LocalDate(2026, 11, 12)
    private val settings = Settings(
        overloadPeriodDays = 7,
        weightUnit = Units.KG,
        weightIncrement = 2.5,
        repMin = 8,
        repMax = 12,
        bodyweightRepCeiling = 15,
    )

    private fun sets(
        date: LocalDate,
        reps: Int,
        weight: Double,
        count: Int = 3,
        bodyweight: Boolean = false,
    ) = (1..count).map { i ->
        StrengthSet(
            id = "$date-$i",
            date = date,
            exercise = "Bench Press",
            setNo = i,
            reps = reps,
            weight = weight,
            unit = Units.KG,
            bodyweight = bodyweight,
        )
    }

    @Test
    fun `no history gives no target`() {
        val advice = Overload.forStrength(emptyList(), settings, today)
        assertNull(advice.target)
        assertFalse(advice.hasHistory)
    }

    @Test
    fun `inside the window it asks you to repeat the last session`() {
        // Progressed 3 days ago, window is 7 days — not due yet.
        val history = sets(today.minus(3, DateTimeUnit.DAY), reps = 9, weight = 40.0).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertFalse(advice.isOverload)
        assertEquals(9, advice.target!!.reps)
        assertEquals(40.0, advice.target!!.weight, 0.001)
        assertEquals(3, advice.target!!.sets)
        assertEquals(4, advice.daysUntilOverload)
        assertTrue(advice.reason.contains("4 days"))
    }

    @Test
    fun `window elapsed below the rep ceiling adds one rep`() {
        val history = sets(today.minus(7, DateTimeUnit.DAY), reps = 9, weight = 40.0).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertTrue(advice.isOverload)
        assertEquals(10, advice.target!!.reps)
        assertEquals(40.0, advice.target!!.weight, 0.001)
        assertEquals("3 x 10 @ 40 kg", advice.headline)
    }

    @Test
    fun `at the top of the rep range the weight goes up and reps reset`() {
        val history = sets(today.minus(9, DateTimeUnit.DAY), reps = 12, weight = 40.0).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertTrue(advice.isOverload)
        assertEquals(8, advice.target!!.reps)
        assertEquals(42.5, advice.target!!.weight, 0.001)
    }

    @Test
    fun `the clock runs from the last improvement, not the last session`() {
        // Improved 20 days ago, then repeated the same numbers twice. Still overdue.
        val history = (
            sets(today.minus(20, DateTimeUnit.DAY), reps = 10, weight = 40.0) +
                sets(today.minus(13, DateTimeUnit.DAY), reps = 10, weight = 40.0) +
                sets(today.minus(6, DateTimeUnit.DAY), reps = 10, weight = 40.0)
            ).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertTrue("repeating the same load must not reset the clock", advice.isOverload)
        assertEquals(20, advice.daysSinceProgress)
        assertEquals(6, advice.daysSinceLast)
        assertEquals(11, advice.target!!.reps)
    }

    @Test
    fun `a genuine improvement resets the clock`() {
        val history = (
            sets(today.minus(20, DateTimeUnit.DAY), reps = 10, weight = 40.0) +
                sets(today.minus(2, DateTimeUnit.DAY), reps = 11, weight = 40.0)
            ).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertFalse(advice.isOverload)
        assertEquals(2, advice.daysSinceProgress)
        assertEquals(11, advice.target!!.reps)
    }

    @Test
    fun `bodyweight sets gain reps until the ceiling`() {
        val history = sets(
            today.minus(8, DateTimeUnit.DAY), reps = 10, weight = 0.0, bodyweight = true
        ).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertTrue(advice.isOverload)
        assertEquals(11, advice.target!!.reps)
        assertEquals(0.0, advice.target!!.weight, 0.001)
        assertTrue(advice.target!!.bodyweight)
        assertEquals("3 x 11 @ BW", advice.headline)
    }

    @Test
    fun `past the bodyweight ceiling it adds load instead of reps`() {
        val history = sets(
            today.minus(8, DateTimeUnit.DAY), reps = 16, weight = 0.0, bodyweight = true
        ).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertTrue(advice.isOverload)
        assertEquals(8, advice.target!!.reps)
        assertEquals(2.5, advice.target!!.weight, 0.001)
        assertTrue(advice.target!!.bodyweight)
        assertEquals("3 x 8 @ BW +2.5 kg", advice.headline)
    }

    @Test
    fun `a longer overload period delays the step up`() {
        val history = sets(today.minus(10, DateTimeUnit.DAY), reps = 9, weight = 40.0).toSessions()

        val weekly = Overload.forStrength(history, settings, today)
        val monthly = Overload.forStrength(history, settings.copy(overloadPeriodDays = 30), today)

        assertTrue(weekly.isOverload)
        assertFalse(monthly.isOverload)
        assertEquals(20, monthly.daysUntilOverload)
        assertEquals(9, monthly.target!!.reps)
    }

    @Test
    fun `sessions after the planning date are ignored`() {
        // Planning Monday while Wednesday is already logged must not look into the future.
        val history = (
            sets(today.minus(10, DateTimeUnit.DAY), reps = 9, weight = 40.0) +
                sets(today.plus(2, DateTimeUnit.DAY), reps = 20, weight = 80.0)
            ).toSessions()

        val advice = Overload.forStrength(history, settings, today)

        assertEquals(10, advice.target!!.reps)
        assertEquals(40.0, advice.target!!.weight, 0.001)
    }

    @Test
    fun `the top set drives the suggestion when sets differ`() {
        val mixed = listOf(
            StrengthSet("a", today.minus(8, DateTimeUnit.DAY), "Row", 1, 8, 50.0, Units.KG, false),
            StrengthSet("b", today.minus(8, DateTimeUnit.DAY), "Row", 2, 10, 60.0, Units.KG, false),
            StrengthSet("c", today.minus(8, DateTimeUnit.DAY), "Row", 3, 6, 55.0, Units.KG, false),
        ).toSessions()

        val advice = Overload.forStrength(mixed, settings, today)

        // Best set is 10 @ 60 (highest e1RM), so that is what gets the extra rep.
        assertEquals(11, advice.target!!.reps)
        assertEquals(60.0, advice.target!!.weight, 0.001)
    }

    @Test
    fun `pounds history is suggested back in pounds`() {
        val lb = settings.copy(weightUnit = Units.LB, weightIncrement = 5.0)
        val history = listOf(
            StrengthSet("a", today.minus(8, DateTimeUnit.DAY), "Bench", 1, 12, 100.0, Units.LB, false),
        ).toSessions()

        val advice = Overload.forStrength(history, lb, today)

        assertEquals(8, advice.target!!.reps)
        assertEquals(105.0, advice.target!!.weight, 0.001)
        assertEquals(Units.LB, advice.target!!.unit)
    }

    // ------------------------------------------------------------------ cardio

    private fun cardio(date: LocalDate, sec: Int, km: Double) = CardioEntry(
        id = "$date", date = date, activity = "Run",
        durationSec = sec, distance = km, distUnit = Units.KM,
    )

    @Test
    fun `cardio inside the window repeats the last session`() {
        val advice = Overload.forCardio(
            listOf(cardio(today.minus(2, DateTimeUnit.DAY), 1800, 5.0)), settings, today
        )

        assertFalse(advice.isOverload)
        assertEquals(5.0, advice.target!!.distance, 0.001)
        assertEquals(1800, advice.target!!.durationSec)
    }

    @Test
    fun `cardio with distance gets a distance bump`() {
        val advice = Overload.forCardio(
            listOf(cardio(today.minus(8, DateTimeUnit.DAY), 1800, 5.0)), settings, today
        )

        assertTrue(advice.isOverload)
        // 5% of 5 km rounds to 0.3 km.
        assertEquals(5.3, advice.target!!.distance, 0.001)
    }

    @Test
    fun `cardio without distance gets a duration bump`() {
        val advice = Overload.forCardio(
            listOf(cardio(today.minus(8, DateTimeUnit.DAY), 1800, 0.0)), settings, today
        )

        assertTrue(advice.isOverload)
        // 5% of 30 min is 90s, rounded to half-minutes.
        assertEquals(1890, advice.target!!.durationSec)
        assertEquals(0.0, advice.target!!.distance, 0.001)
    }

    @Test
    fun `a short cardio session still gets at least a one minute bump`() {
        val advice = Overload.forCardio(
            listOf(cardio(today.minus(8, DateTimeUnit.DAY), 300, 0.0)), settings, today
        )

        assertEquals(360, advice.target!!.durationSec)
    }
}
