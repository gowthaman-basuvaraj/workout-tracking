package com.gowthaman.workout.logic

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.StressEvent
import java.time.LocalDate

/**
 * What order to offer the five kinds of thing in.
 *
 * A fixed order puts sleep and cardio — once a day at most — in the same place as food,
 * which is a dozen taps a day. Ranking them by how often they are actually reached for
 * costs nothing and saves a scan every time.
 *
 * Measured in **days touched**, not entries. Entries would say strength is the busiest
 * thing on the list, because a session is twelve sets logged in ten minutes behind one
 * trip to the form. Days answer the question the order is actually asking: on a given
 * day, how likely is it that this is what you came to log?
 *
 * Deliberately slow to move. The ranking comes from a 30-day window, so it shifts over
 * weeks rather than between taps, and a position learned by thumb stays learned. The one
 * thing that moves within a day is sleep, which drops to the end once it is logged —
 * there is only ever one night, so leaving it in reach is offering something that cannot
 * be done.
 */
object LogOrder {

    /** Used before there is data, and to break ties. Roughly how often people log things. */
    val DEFAULT: List<Kind> = listOf(Kind.FOOD, Kind.STRENGTH, Kind.STRESS, Kind.CARDIO, Kind.SLEEP)

    private const val WINDOW_DAYS = 30L

    fun rank(
        today: LocalDate,
        strength: List<StrengthSet> = emptyList(),
        cardio: List<CardioEntry> = emptyList(),
        food: List<FoodEntry> = emptyList(),
        sleep: List<SleepNight> = emptyList(),
        stress: List<StressEvent> = emptyList(),
    ): List<Kind> {
        val from = today.minusDays(WINDOW_DAYS - 1)
        fun <T> days(rows: List<T>, dateOf: (T) -> LocalDate): Int =
            rows.map(dateOf).filter { it in from..today }.distinct().size

        val touched = mapOf(
            Kind.STRENGTH to days(strength) { it.date },
            Kind.CARDIO to days(cardio) { it.date },
            Kind.FOOD to days(food) { it.date },
            Kind.SLEEP to days(sleep) { it.date },
            Kind.STRESS to days(stress) { it.date },
        )

        // Only sleep is genuinely once a day: logging it again replaces the night rather
        // than adding one. Everything else can take another entry, so nothing else sinks.
        val sleepDone = sleep.any { it.date == today }

        return DEFAULT.sortedWith(
            compareBy<Kind> { it == Kind.SLEEP && sleepDone }
                .thenByDescending { touched[it] ?: 0 }
                .thenBy { DEFAULT.indexOf(it) }
        )
    }
}
