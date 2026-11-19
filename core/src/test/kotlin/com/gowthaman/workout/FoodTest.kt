package com.gowthaman.workout

import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Catalog
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
        val one = FoodEntry("a", LocalDate.now(), "Coffee", Purpose.DRINK, FoodScale.EVERYDAY, qty = 1)
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

class FoodTagTest {

    @Test
    fun `tags render in declaration order whatever order they were picked in`() {
        // Otherwise the same meal writes two different strings and every diff is noise.
        val a = FoodTag.render(setOf(FoodTag.MEAT, FoodTag.PLANTS))
        val b = FoodTag.render(setOf(FoodTag.PLANTS, FoodTag.MEAT))
        assertEquals("PLANTS|MEAT", a)
        assertEquals(a, b)
    }

    @Test
    fun `tags round-trip through the stored form`() {
        val tags = setOf(FoodTag.PLANTS, FoodTag.DAIRY)
        assertEquals(tags, FoodTag.parse(FoodTag.render(tags)))
    }

    @Test
    fun `an empty tag set renders and parses as nothing`() {
        assertEquals("", FoodTag.render(emptySet()))
        assertEquals(emptySet<FoodTag>(), FoodTag.parse(""))
    }

    @Test
    fun `hand-edited separators and casing still parse`() {
        assertEquals(
            setOf(FoodTag.PLANTS, FoodTag.EGG),
            FoodTag.parse("plants, Egg"),
        )
        assertEquals(setOf(FoodTag.MEAT), FoodTag.parse(" meat "))
    }

    @Test
    fun `an unknown tag is dropped rather than throwing`() {
        // A laptop edit must never be able to crash the app.
        assertEquals(setOf(FoodTag.DAIRY), FoodTag.parse("DAIRY|GLUTEN"))
        assertEquals(emptySet<FoodTag>(), FoodTag.parse("nonsense"))
    }
}

class FoodTagCsvTest {

    private val day = LocalDate.of(2026, 11, 19)

    private fun entry(tags: Set<FoodTag>) = FoodEntry(
        id = "x1",
        date = day,
        item = "Dosa",
        purpose = Purpose.DINNER,
        scale = FoodScale.EVERYDAY,
        tags = tags,
        qty = 1,
        loggedAt = 1_700_000_000_000L,
    )

    @Test
    fun `tags survive a write and read`() {
        val row = entry(setOf(FoodTag.PLANTS, FoodTag.DAIRY))
        val back = CsvFormat.parseFood(CsvFormat.renderFood(listOf(row)))
        assertEquals(1, back.size)
        assertEquals(setOf(FoodTag.PLANTS, FoodTag.DAIRY), back.first().tags)
    }

    @Test
    fun `a file written before tags existed reads as untagged`() {
        // The column is addressed by header name, so an old file is simply short of one.
        val old = """
            id,date,item,purpose,scale,qty,notes,logged_at
            a1,2026-11-19,Coffee,DRINK,2,2,,1700000000000
        """.trimIndent()
        val rows = CsvFormat.parseFood(old)
        assertEquals(1, rows.size)
        assertEquals(emptySet<FoodTag>(), rows.first().tags)
        assertEquals(2, rows.first().qty)
    }

    @Test
    fun `the tag field never needs quoting`() {
        val text = CsvFormat.renderFood(listOf(entry(setOf(FoodTag.PLANTS, FoodTag.MEAT))))
        assertTrue(text, !text.contains('"'))
        assertTrue(text, text.contains("PLANTS|MEAT"))
    }

    @Test
    fun `appending to a file that already agrees is left to a plain append`() {
        val text = CsvFormat.renderFood(listOf(entry(setOf(FoodTag.PLANTS))))
        assertNull(CsvFormat.appendAligned(text, CsvFormat.FOOD_HEADER, "whatever"))
    }

    @Test
    fun `appending to a file written before tags existed does not shift every value`() {
        // The CLI appends rather than rewrites. Without realigning, PLANTS would land in
        // the qty column and the quantity in the notes -- and nothing would complain,
        // because columns are read by name on the way back in.
        val old = "id,date,item,purpose,scale,qty,notes,logged_at\n" +
            "a1,2026-11-18,Coffee,DRINK,2,3,morning,1700000000000\n"
        val row = CsvFormat.foodRow(entry(setOf(FoodTag.PLANTS, FoodTag.DAIRY)).copy(qty = 2))

        val merged = CsvFormat.appendAligned(old, CsvFormat.FOOD_HEADER, row)
        assertTrue(merged != null)
        val rows = CsvFormat.parseFood(merged!!)

        assertEquals(2, rows.size)
        val existing = rows.first { it.id == "a1" }
        assertEquals(3, existing.qty)
        assertEquals("morning", existing.notes)
        assertEquals(emptySet<FoodTag>(), existing.tags)

        val appended = rows.first { it.id == "x1" }
        assertEquals(2, appended.qty)
        assertEquals(setOf(FoodTag.PLANTS, FoodTag.DAIRY), appended.tags)
        assertEquals(1_700_000_000_000L, appended.loggedAt)
    }

    @Test
    fun `a column this version does not know about is kept rather than dropped`() {
        // An older CLI logging into a folder a newer app writes must not quietly delete
        // whatever that newer app added.
        val newer = "id,date,item,purpose,scale,tags,qty,notes,logged_at,mood\n" +
            "a1,2026-11-18,Coffee,DRINK,2,DAIRY,1,,1700000000000,calm\n"
        val merged = CsvFormat.appendAligned(
            newer, CsvFormat.FOOD_HEADER, CsvFormat.foodRow(entry(setOf(FoodTag.EGG))),
        )

        assertTrue(merged!!, merged.lines().first().endsWith(",mood"))
        assertTrue(merged, merged.contains(",calm"))
        assertEquals(setOf(FoodTag.DAIRY), CsvFormat.parseFood(merged).first { it.id == "a1" }.tags)
    }

    @Test
    fun `the catalog remembers the tags from the most recent entry`() {
        val entries = listOf(
            entry(setOf(FoodTag.PLANTS)).copy(id = "a", date = day.minusDays(2), loggedAt = 1L),
            entry(setOf(FoodTag.PLANTS, FoodTag.DAIRY)).copy(id = "b", loggedAt = 2L),
        )
        val rebuilt = Catalog.rebuildFood(emptyList(), entries)
        assertEquals(1, rebuilt.size)
        assertEquals(setOf(FoodTag.PLANTS, FoodTag.DAIRY), rebuilt.first().tags)
    }

    @Test
    fun `catalog tags survive a write and read`() {
        val def = FoodDef(
            name = "Dosa",
            purpose = Purpose.DINNER,
            scale = FoodScale.EVERYDAY,
            tags = setOf(FoodTag.PLANTS, FoodTag.DAIRY),
            useCount = 3,
        )
        val back = CsvFormat.parseFoodCatalog(CsvFormat.renderFoodCatalog(listOf(def)))
        assertEquals(setOf(FoodTag.PLANTS, FoodTag.DAIRY), back.first().tags)
        assertEquals(3, back.first().useCount)
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
        tags: Set<FoodTag> = emptySet(),
        qty: Int = 1,
        hour: Int = 12,
        id: String = "$item-$date-$hour",
    ) = FoodEntry(
        id = id,
        date = date,
        item = item,
        purpose = purpose,
        scale = scale,
        tags = tags,
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
    fun `tag counts are quantity-weighted and may overlap`() {
        val entries = listOf(
            food(today, "Dosa", tags = setOf(FoodTag.PLANTS, FoodTag.DAIRY)),
            food(today, "Coffee", qty = 3, tags = setOf(FoodTag.DAIRY), id = "c"),
            food(today, "Crisps", id = "d"),
        )
        val data = Stats.build(emptyList(), emptyList(), settings, today, entries)

        assertEquals(5, data.foodCount)
        // One dosa plus three coffees carry a tag; the crisps do not.
        assertEquals(4, data.foodTaggedCount)
        assertEquals(
            listOf(FoodTag.DAIRY to 4, FoodTag.PLANTS to 1),
            data.foodTagMix,
        )
    }

    @Test
    fun `tags nobody used are left out entirely`() {
        val data = Stats.build(
            emptyList(), emptyList(), settings, today,
            listOf(food(today, "Salad", tags = setOf(FoodTag.PLANTS))),
        )
        assertEquals(listOf(FoodTag.PLANTS to 1), data.foodTagMix)
    }

    @Test
    fun `an untagged log reports no coverage rather than zeroes`() {
        val data = Stats.build(
            emptyList(), emptyList(), settings, today, listOf(food(today, "Crisps")),
        )
        assertEquals(0, data.foodTaggedCount)
        assertTrue(data.foodTagMix.isEmpty())
    }

    @Test
    fun `an empty food log reports nothing rather than zeroes`() {
        val data = Stats.build(emptyList(), emptyList(), settings, today, emptyList())
        assertEquals(false, data.hasFood)
        assertNull(data.indulgentPct)
        assertEquals(0, data.cleanDayStreak)
    }
}
