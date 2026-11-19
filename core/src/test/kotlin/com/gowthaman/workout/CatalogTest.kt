package com.gowthaman.workout

import com.gowthaman.workout.data.Catalog
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CatalogTest {

    private val foods = listOf("Coffee", "Crisps", "Beer", "Biscuits", "Rice and curry")

    // ------------------------------------------------- the no-duplicates promise

    @Test
    fun `case and padding never create a second entry`() {
        listOf("coffee", "COFFEE", "  Coffee  ", "CoFFeE").forEach { typed ->
            assertEquals(
                "typing \"$typed\" must log the catalog's spelling",
                "Coffee",
                Catalog.canonical(foods, typed),
            )
        }
    }

    @Test
    fun `a genuinely new name is kept as typed, trimmed`() {
        assertEquals("Quinoa", Catalog.canonical(foods, "  Quinoa "))
    }

    @Test
    fun `contains is case insensitive too`() {
        assertTrue(Catalog.contains(foods, "cOffEE"))
        assertTrue(!Catalog.contains(foods, "quinoa"))
    }

    // ---------------------------------------------------------------- resolving

    @Test
    fun `an exact name resolves even when longer names contain it`() {
        val names = listOf("Run", "Running machine")
        assertEquals(Catalog.Match.One("Run"), Catalog.resolve(names, "run"))
    }

    @Test
    fun `a unique prefix resolves`() {
        assertEquals(Catalog.Match.One("Coffee"), Catalog.resolve(foods, "cof"))
    }

    @Test
    fun `a unique substring resolves`() {
        assertEquals(Catalog.Match.One("Rice and curry"), Catalog.resolve(foods, "curry"))
    }

    @Test
    fun `an ambiguous prefix reports every candidate instead of guessing`() {
        val match = Catalog.resolve(foods, "b")
        assertEquals(Catalog.Match.Many(listOf("Beer", "Biscuits")), match)
    }

    @Test
    fun `a typo resolves to nothing but suggests the near miss`() {
        val match = Catalog.resolve(foods, "cofee")
        assertTrue(match is Catalog.Match.None)
        assertEquals(listOf("Coffee"), (match as Catalog.Match.None).suggestions)
    }

    @Test
    fun `something genuinely unrelated suggests nothing`() {
        val match = Catalog.resolve(foods, "aubergine") as Catalog.Match.None
        assertTrue(match.suggestions.isEmpty())
    }

    @Test
    fun `an empty query matches nothing`() {
        assertEquals(Catalog.Match.None(emptyList()), Catalog.resolve(foods, "   "))
    }

    @Test
    fun `edit distance counts single edits`() {
        assertEquals(0, Catalog.editDistance("coffee", "coffee"))
        assertEquals(1, Catalog.editDistance("cofee", "coffee"))   // insertion
        assertEquals(1, Catalog.editDistance("coffe", "coffee"))   // deletion
        assertEquals(1, Catalog.editDistance("coffea", "coffee"))  // substitution
    }

    // -------------------------------------------------------------- rebuilding

    @Test
    fun `the food catalog remembers how an item was last logged`() {
        val day = LocalDate.of(2026, 11, 17)
        val entries = listOf(
            FoodEntry("a", day.minusDays(2), "Coffee", Purpose.DRINK, FoodScale.HEALTHY, qty = 1, loggedAt = 1),
            FoodEntry("b", day, "Coffee", Purpose.DRINK, FoodScale.INDULGENT, qty = 2, loggedAt = 2),
        )
        val def = Catalog.rebuildFood(emptyList(), entries).single()

        assertEquals("Coffee", def.name)
        assertEquals(FoodScale.INDULGENT, def.scale)   // from the newest entry
        assertEquals(3, def.useCount)                  // quantity-weighted
        assertEquals(day, def.lastUsed)
    }

    @Test
    fun `differently cased rows collapse into one catalog entry`() {
        val day = LocalDate.of(2026, 11, 17)
        val entries = listOf(
            FoodEntry("a", day, "Coffee", Purpose.DRINK, FoodScale.EVERYDAY, qty = 1, loggedAt = 1),
            FoodEntry("b", day, "coffee", Purpose.DRINK, FoodScale.EVERYDAY, qty = 1, loggedAt = 2),
        )
        val built = Catalog.rebuildFood(emptyList(), entries)

        assertEquals(1, built.size)
        assertEquals(2, built.single().useCount)
    }

    @Test
    fun `exercise rebuilding keeps strength and cardio apart`() {
        val day = LocalDate.of(2026, 11, 17)
        val sets = listOf(
            StrengthSet("a", day, "Bench Press", 1, 10, 40.0, Units.KG, false, loggedAt = 1),
        )
        val built = Catalog.rebuildExercises(emptyList(), sets, emptyList())
        assertEquals(1, built.size)
        assertEquals("Bench Press", built.single().name)
        assertEquals(1, built.single().useCount)
    }
}

/** The format is the contract between the phone and the desktop, so it is pinned here. */
class CsvFormatTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 17)

    @Test
    fun `month file names match what the app writes`() {
        val month = YearMonth.of(2026, 3)
        assertEquals("strength-2026-03.csv", CsvFormat.strengthFile(month))
        assertEquals("cardio-2026-03.csv", CsvFormat.cardioFile(month))
        assertEquals("food-2026-03.csv", CsvFormat.foodFile(month))
    }

    @Test
    fun `a food row renders and parses back unchanged`() {
        val entry = FoodEntry(
            id = "abc123", date = day, item = "Flat white, large",
            purpose = Purpose.DRINK, scale = FoodScale.INDULGENT, qty = 2,
            notes = "with \"extra\" sugar", loggedAt = 1760000000000,
        )
        val text = CsvFormat.renderFood(listOf(entry))
        assertEquals(entry, CsvFormat.parseFood(text).single())
    }

    @Test
    fun `a single row carries no header, for appending`() {
        val entry = FoodEntry("abc", day, "Coffee", Purpose.DRINK, FoodScale.EVERYDAY, qty = 1)
        val row = CsvFormat.foodRow(entry)

        assertTrue(!row.contains("item"))
        // Appending it under a header must parse as one entry.
        val whole = com.gowthaman.workout.data.Csv.row(CsvFormat.FOOD_HEADER) + "\n" + row + "\n"
        assertEquals(entry.copy(loggedAt = 0), CsvFormat.parseFood(whole).single().copy(loggedAt = 0))
    }

    @Test
    fun `a strength row survives the round trip`() {
        val set = StrengthSet("x1", day, "Bench Press", 2, 10, 42.5, Units.KG, false, "felt good", 17L)
        assertEquals(set, CsvFormat.parseStrength(CsvFormat.renderStrength(listOf(set))).single())
    }

    @Test
    fun `sync conflict copies are recognised so they can be skipped`() {
        assertTrue(CsvFormat.isConflictCopy("food-2026-11.sync-conflict-20261117-abc.csv"))
        assertTrue(!CsvFormat.isConflictCopy("food-2026-11.csv"))
    }
}

/** settings.csv is the app's one-way publication to desktop tools. */
class SettingsFileTest {

    @Test
    fun `settings survive a round trip`() {
        val settings = com.gowthaman.workout.data.Settings(
            overloadPeriodDays = 15,
            weightUnit = Units.LB,
            distanceUnit = "mi",
            weightIncrement = 5.0,
            repMin = 6,
            repMax = 10,
            bodyweightRepCeiling = 20,
            userBodyweightKg = 78.5,
            dashboardPeriod = com.gowthaman.workout.data.Period.QUARTER,
        )
        val read = CsvFormat.parseSettings(CsvFormat.renderSettings(settings))
        assertEquals(settings, read)
    }

    @Test
    fun `the device-specific folder uri is never published`() {
        val text = CsvFormat.renderSettings(
            com.gowthaman.workout.data.Settings(folderUri = "content://tree/primary%3ADocuments")
        )
        assertTrue("the content uri must not leak into the folder", !text.contains("content://"))
        assertEquals(null, CsvFormat.parseSettings(text).folderUri)
    }

    @Test
    fun `the warning is the first row, so it is seen on opening the file`() {
        val lines = CsvFormat.renderSettings(com.gowthaman.workout.data.Settings()).lines()
        assertTrue(lines[0].startsWith("key,value"))
        assertTrue(lines[1].startsWith("_note,"))
        assertTrue(lines[1].contains("Read-only"))
    }

    @Test
    fun `missing and unreadable values fall back rather than failing`() {
        val text = "key,value\noverload_period_days,notanumber\nweight_unit,stones\nnonsense,1\n"
        val base = com.gowthaman.workout.data.Settings()
        val read = CsvFormat.parseSettings(text, base)

        assertEquals(base.overloadPeriodDays, read.overloadPeriodDays)
        assertEquals(base.weightUnit, read.weightUnit)
        assertEquals(base.repMin, read.repMin)
    }

    @Test
    fun `an empty file yields the fallback untouched`() {
        val base = com.gowthaman.workout.data.Settings(weightUnit = Units.LB)
        assertEquals(base, CsvFormat.parseSettings("", base))
    }
}
