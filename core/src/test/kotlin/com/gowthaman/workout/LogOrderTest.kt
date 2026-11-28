package com.gowthaman.workout

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.logic.LogOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogOrderTest {

    private val today = LocalDate.of(2026, 11, 28)

    private fun food(day: LocalDate, n: Int = 1) = (1..n).map {
        FoodEntry("f$day-$it", day, "Coffee", Purpose.DRINK, FoodScale.EVERYDAY, qty = 1)
    }

    private fun sets(day: LocalDate, n: Int = 1) = (1..n).map {
        StrengthSet("s$day-$it", day, "Bench press", it, 8, 40.0, Units.KG, false)
    }

    private fun night(day: LocalDate) =
        SleepNight("n$day", day, 450, SleepQuality.OK)

    private fun run(day: LocalDate) =
        CardioEntry("c$day", day, "Run", 1800, 5.0, Units.KM)

    @Test
    fun `with no data at all it falls back to the default order`() {
        assertEquals(LogOrder.DEFAULT, LogOrder.rank(today))
    }

    @Test
    fun `the thing touched on the most days comes first`() {
        val order = LogOrder.rank(
            today,
            food = (0..20).flatMap { food(today.minusDays(it.toLong())) },
            cardio = (0..2).map { run(today.minusDays(it.toLong())) },
        )
        assertEquals(Kind.FOOD, order.first())
        assertTrue(order.indexOf(Kind.FOOD) < order.indexOf(Kind.CARDIO))
    }

    @Test
    fun `a busy session does not outrank a thing logged on more days`() {
        // The reason this counts days and not entries: twelve sets in one session is one
        // trip to the form, and should not outrank food touched on ten separate days.
        val order = LogOrder.rank(
            today,
            strength = sets(today, n = 12) + sets(today.minusDays(1), n = 12),
            food = (0..9).flatMap { food(today.minusDays(it.toLong())) },
        )
        assertEquals(Kind.FOOD, order.first())
    }

    @Test
    fun `sleep sinks to the end once tonight is logged`() {
        val slept = (0..25).map { night(today.minusDays(it.toLong())) }
        val before = LogOrder.rank(today, sleep = slept.drop(1))   // nothing for today yet
        val after = LogOrder.rank(today, sleep = slept)            // today's night logged

        assertTrue("sleep should be reachable before it is logged", before.indexOf(Kind.SLEEP) < 4)
        assertEquals("sleep should sink once logged", Kind.SLEEP, after.last())
    }

    @Test
    fun `food logged today does not sink, because there is always more`() {
        val order = LogOrder.rank(today, food = (0..20).flatMap { food(today.minusDays(it.toLong())) })
        assertEquals(Kind.FOOD, order.first())
    }

    @Test
    fun `anything outside the window is ignored`() {
        // A month of cardio last quarter says nothing about what gets logged today.
        val order = LogOrder.rank(
            today,
            cardio = (40..70).map { run(today.minusDays(it.toLong())) },
            food = (0..4).flatMap { food(today.minusDays(it.toLong())) },
        )
        assertTrue(order.indexOf(Kind.FOOD) < order.indexOf(Kind.CARDIO))
    }

    @Test
    fun `ties keep the default order, so the list does not shuffle at random`() {
        // Two kinds touched on exactly the same days must not swap places between taps.
        val days = (0..5).map { today.minusDays(it.toLong()) }
        val order = LogOrder.rank(
            today,
            food = days.flatMap { food(it) },
            strength = days.flatMap { sets(it) },
        )
        assertEquals(listOf(Kind.FOOD, Kind.STRENGTH), order.take(2))
    }

    @Test
    fun `every kind is always present exactly once`() {
        val order = LogOrder.rank(today, food = food(today), sleep = listOf(night(today)))
        assertEquals(Kind.entries.size, order.size)
        assertEquals(Kind.entries.toSet(), order.toSet())
    }
}
