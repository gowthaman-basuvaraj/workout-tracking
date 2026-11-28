package com.gowthaman.workout

import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StressWeight
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.logic.Stats
import com.gowthaman.workout.logic.Summary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTest {

    private val today: LocalDate = LocalDate(2026, 11, 17)
    private val settings = Settings()

    private fun summaryOf(
        strength: List<StrengthSet> = emptyList(),
        food: List<FoodEntry> = emptyList(),
        sleep: List<SleepNight> = emptyList(),
        stress: List<StressEvent> = emptyList(),
    ): String {
        val data = Stats.build(strength, emptyList(), settings, today, food, sleep, stress)
        return Summary.text(data, settings)
    }

    @Test
    fun `an empty log says so rather than printing empty headings`() {
        assertEquals("Uncomplicated - nothing logged yet.", summaryOf())
    }

    @Test
    fun `sections are absent when there is nothing in them`() {
        val text = summaryOf(sleep = listOf(SleepNight("a", today, 450, SleepQuality.OK)))

        assertTrue(text.contains("Sleep"))
        assertTrue("no training logged, so no Training heading", !text.contains("Training"))
        assertTrue(!text.contains("Food"))
        assertTrue(!text.contains("Stress"))
    }

    @Test
    fun `the window is named at the top`() {
        val text = summaryOf(sleep = listOf(SleepNight("a", today, 450, SleepQuality.OK)))
        assertTrue(text.startsWith("Uncomplicated - last 30 days"))
    }

    @Test
    fun `sleep coverage always travels with the average`() {
        val nights = listOf(
            SleepNight("a", today, 480, SleepQuality.GOOD),
            SleepNight("b", today.minus(1, DateTimeUnit.DAY), 360, SleepQuality.POOR),
        )
        val text = summaryOf(sleep = nights)

        assertTrue(text.contains("average over 2 nights logged"))
        assertTrue(text.contains("nights under 6h"))
    }

    @Test
    fun `stress leads with busters and never prints a ratio`() {
        val events = listOf(
            StressEvent("a", today, "Deadline", StressDirection.ENHANCER, StressWeight.MAJOR),
            StressEvent("b", today, "Walk", StressDirection.BUSTER, StressWeight.MINOR),
        )
        val text = summaryOf(stress = events)
        val stressBlock = text.substringAfter("Stress")

        assertTrue(stressBlock.indexOf("busters") < stressBlock.indexOf("came up"))
        // A ratio would claim precision the logging bias does not support.
        assertTrue(!text.contains(":1"))
        assertTrue(!text.contains("ratio"))
    }

    @Test
    fun `food tags are shared as counts with the coverage they came from`() {
        val food = listOf(
            FoodEntry("a", today, "Dosa", Purpose.DINNER, FoodScale.EVERYDAY,
                tags = setOf(FoodTag.PLANTS, FoodTag.DAIRY), qty = 1),
            FoodEntry("b", today, "Coffee", Purpose.DRINK, FoodScale.EVERYDAY,
                tags = setOf(FoodTag.DAIRY), qty = 2),
            FoodEntry("c", today, "Crisps", Purpose.SNACK, FoodScale.INDULGENT, qty = 1),
        )
        val line = summaryOf(food = food).lines().first { it.contains("tagged") }

        // Counts, never a percentage: tags overlap, so shares would not add up and the
        // untagged crisps belong to none of them.
        assertEquals("  tagged 3 of 4 - dairy 3, plants 1", line)
        assertTrue(!line.contains("%"))
    }

    @Test
    fun `an untagged food log says nothing about tags at all`() {
        val text = summaryOf(
            food = listOf(FoodEntry("a", today, "Crisps", Purpose.SNACK, FoodScale.INDULGENT, qty = 1)),
        )
        assertTrue(text, !text.contains("tagged"))
    }

    @Test
    fun `percentages add to a hundred`() {
        val food = listOf(
            FoodEntry("a", today, "Salad", Purpose.LUNCH, FoodScale.HEALTHY, qty = 1),
            FoodEntry("b", today, "Toast", Purpose.BREAKFAST, FoodScale.EVERYDAY, qty = 1),
            FoodEntry("c", today, "Cake", Purpose.SNACK, FoodScale.INDULGENT, qty = 1),
        )
        val line = summaryOf(food = food).lines().first { it.contains("%") }
        val total = Regex("""(\d+)%""").findAll(line).sumOf { it.groupValues[1].toInt() }

        assertEquals(100, total)
    }

    @Test
    fun `nothing in the text grades or advises`() {
        val text = summaryOf(
            strength = listOf(StrengthSet("s", today, "Squat", 1, 10, 100.0, Units.KG, false)),
            food = listOf(FoodEntry("f", today, "Cake", Purpose.SNACK, FoodScale.INDULGENT, qty = 4)),
            sleep = listOf(SleepNight("n", today, 240, SleepQuality.POOR)),
        )
        listOf("should", "try to", "too much", "poorly", "well done", "goal", "target")
            .forEach { assertTrue("summary must not editorialise: found \"$it\"", !text.contains(it)) }
    }
}
