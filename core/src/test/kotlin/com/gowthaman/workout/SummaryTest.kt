package com.gowthaman.workout

import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SummaryTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 17)
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
            SleepNight("b", today.minusDays(1), 360, SleepQuality.POOR),
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
    fun `percentages add to a hundred`() {
        val food = listOf(
            FoodEntry("a", today, "Salad", Purpose.LUNCH, FoodScale.HEALTHY, 1),
            FoodEntry("b", today, "Toast", Purpose.BREAKFAST, FoodScale.EVERYDAY, 1),
            FoodEntry("c", today, "Cake", Purpose.SNACK, FoodScale.INDULGENT, 1),
        )
        val line = summaryOf(food = food).lines().first { it.contains("%") }
        val total = Regex("""(\d+)%""").findAll(line).sumOf { it.groupValues[1].toInt() }

        assertEquals(100, total)
    }

    @Test
    fun `nothing in the text grades or advises`() {
        val text = summaryOf(
            strength = listOf(StrengthSet("s", today, "Squat", 1, 10, 100.0, Units.KG, false)),
            food = listOf(FoodEntry("f", today, "Cake", Purpose.SNACK, FoodScale.INDULGENT, 4)),
            sleep = listOf(SleepNight("n", today, 240, SleepQuality.POOR)),
        )
        listOf("should", "try to", "too much", "poorly", "well done", "goal", "target")
            .forEach { assertTrue("summary must not editorialise: found \"$it\"", !text.contains(it)) }
    }
}
