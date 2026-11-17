package com.gowthaman.workout

import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.parseCsv
import com.gowthaman.workout.data.summary
import com.gowthaman.workout.logic.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FoodModelTest {

    @Test
    fun `scale parses from the stored number`() {
        assertEquals(FoodScale.HEALTHY, FoodScale.parse("1"))
        assertEquals(FoodScale.EVERYDAY, FoodScale.parse("2"))
        assertEquals(FoodScale.INDULGENT, FoodScale.parse("3"))
    }

    @Test
    fun `scale also parses a hand-edited name`() {
        assertEquals(FoodScale.INDULGENT, FoodScale.parse("indulgent"))
        assertEquals(FoodScale.HEALTHY, FoodScale.parse("  Healthy "))
    }

    @Test
    fun `unknown scale and purpose values fall back rather than throwing`() {
        // A laptop edit should never be able to crash the app.
        assertEquals(FoodScale.DEFAULT, FoodScale.parse("banana"))
        assertEquals(FoodScale.DEFAULT, FoodScale.parse("9"))
        assertEquals(Purpose.DEFAULT, Purpose.fromName("brunch"))
        assertEquals(Purpose.DEFAULT, Purpose.fromName(null))
    }

    @Test
    fun `purpose parsing ignores case`() {
        assertEquals(Purpose.DRINK, Purpose.fromName("drink"))
        assertEquals(Purpose.BREAKFAST, Purpose.fromName("BREAKFAST"))
    }

    @Test
    fun `quantity only shows when there is more than one`() {
        val one = FoodEntry("a", LocalDate.now(), "Coffee", Purpose.DRINK, FoodScale.EVERYDAY, 1)
        val two = one.copy(qty = 2)
        assertEquals("Coffee", one.summary())
        assertEquals("2 x Coffee", two.summary())
    }

    @Test
    fun `a food row survives a csv round trip`() {
        val text = """
            id,date,item,purpose,scale,qty,notes,logged_at
            abc,2026-11-17,"Flat white, large",DRINK,3,2,"with ""extra"" sugar",1760000000000
        """.trimIndent()

        val row = parseCsv(text).single()

        assertEquals("Flat white, large", row.str("item"))
        assertEquals(Purpose.DRINK, Purpose.fromName(row.str("purpose")))
        assertEquals(FoodScale.INDULGENT, FoodScale.parse(row.str("scale")))
        assertEquals(2, row.int("qty"))
        assertEquals("with \"extra\" sugar", row.str("notes"))
    }
}

class FoodStatsTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 17)
    private val settings = Settings(dashboardPeriod = Period.MONTH)

    private fun food(
        date: LocalDate,
        item: String,
        purpose: Purpose = Purpose.SNACK,
        scale: FoodScale = FoodScale.EVERYDAY,
        qty: Int = 1,
        hour: Int = 12,
        id: String = "$item-$date-$hour",
    ) = FoodEntry(
        id = id,
        date = date,
        item = item,
        purpose = purpose,
        scale = scale,
        qty = qty,
        loggedAt = date.atTime(LocalTime.of(hour, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test
    fun `food alone is enough to populate the dashboard`() {
        val data = Stats.build(
            emptyList(), emptyList(), settings, today,
            listOf(food(today, "Coffee")),
        )
        assertTrue(data.hasData)
        assertTrue(data.hasFood)
    }

    @Test
    fun `counts use quantity, not row count`() {
        val entries = listOf(
            food(today, "Coffee", qty = 2),
            food(today.minusDays(1), "Coffee", qty = 1),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(3, data.foodCount)
        assertEquals(listOf("Coffee" to 3), data.topFoods)
    }

    @Test
    fun `the food window follows the dashboard period`() {
        val entries = listOf(
            food(today.minusDays(2), "Coffee"),
            food(today.minusDays(60), "Coffee"),
        )
        fun countIn(period: Period) = Stats.build(
            emptyList(), emptyList(), settings.copy(dashboardPeriod = period), today, entries
        ).foodCount

        assertEquals(1, countIn(Period.WEEK))
        assertEquals(1, countIn(Period.MONTH))
        assertEquals(2, countIn(Period.QUARTER))
    }

    @Test
    fun `purpose totals are counted and ordered by size`() {
        val entries = listOf(
            food(today, "Coffee", purpose = Purpose.DRINK, qty = 3),
            food(today, "Crisps", purpose = Purpose.SNACK),
            food(today, "Soup", purpose = Purpose.LUNCH),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(Purpose.DRINK to 3, data.foodByPurpose.first())
        assertEquals(3, data.foodByPurpose.size)
        // Purposes with nothing logged are left out rather than shown as zero.
        assertTrue(data.foodByPurpose.none { it.first == Purpose.BREAKFAST })
    }

    @Test
    fun `the scale mix reports every bucket and an indulgent share`() {
        val entries = listOf(
            food(today, "Salad", scale = FoodScale.HEALTHY),
            food(today, "Toast", scale = FoodScale.EVERYDAY),
            food(today, "Cake", scale = FoodScale.INDULGENT),
            food(today, "Beer", scale = FoodScale.INDULGENT),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(3, data.foodScaleMix.size)
        assertEquals(2, data.foodScaleMix.first { it.first == FoodScale.INDULGENT }.second)
        assertEquals(50, data.indulgentPct)
    }

    @Test
    fun `a clean run counts consecutive logged days with nothing indulgent`() {
        val entries = listOf(
            food(today, "Salad", scale = FoodScale.HEALTHY),
            food(today.minusDays(1), "Toast", scale = FoodScale.EVERYDAY),
            food(today.minusDays(2), "Cake", scale = FoodScale.INDULGENT),
            food(today.minusDays(3), "Salad", scale = FoodScale.HEALTHY),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(2, data.cleanDayStreak)
    }

    @Test
    fun `an unlogged day breaks the clean run rather than extending it`() {
        // Nothing logged yesterday: unknown is not the same as clean.
        val entries = listOf(
            food(today, "Salad", scale = FoodScale.HEALTHY),
            food(today.minusDays(2), "Salad", scale = FoodScale.HEALTHY),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(1, data.cleanDayStreak)
    }

    @Test
    fun `late night share needs a usable sample before it reports`() {
        val few = List(4) { food(today, "Crisps", hour = 22, id = "x$it") }
        assertNull(Stats.build(emptyList(), emptyList(), settings, today, few).lateNightSharePct)

        val enough = List(10) { i ->
            food(today, "Crisps", hour = if (i < 3) 22 else 12, id = "y$i")
        }
        assertEquals(
            30,
            Stats.build(emptyList(), emptyList(), settings, today, enough).lateNightSharePct,
        )
    }

    @Test
    fun `late night share is weighted by quantity`() {
        // One row of three late coffees outweighs two single daytime items.
        val entries = listOf(
            food(today, "Coffee", qty = 3, hour = 22, id = "late"),
            food(today, "Fruit", qty = 1, hour = 10, id = "a"),
            food(today, "Salad", qty = 1, hour = 13, id = "b"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(5, data.foodCount)
        assertEquals(60, data.lateNightSharePct)
    }

    @Test
    fun `an empty food log reports nothing rather than zeroes`() {
        val data = Stats.build(emptyList(), emptyList(), settings, today, emptyList())
        assertEquals(false, data.hasFood)
        assertNull(data.indulgentPct)
        assertEquals(0, data.cleanDayStreak)
    }
}
