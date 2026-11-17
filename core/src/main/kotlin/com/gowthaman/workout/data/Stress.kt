package com.gowthaman.workout.data

import java.time.LocalDate

/** Whether something wound you up or wound you down. */
enum class StressDirection(val label: String) {
    ENHANCER("Enhancer"),
    BUSTER("Buster");

    companion object {
        val DEFAULT = ENHANCER

        fun fromName(name: String?): StressDirection =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * How much it counted for.
 *
 * Without this a snapped shoelace and a bad diagnosis are both "one enhancer", which makes
 * every total useless. Three points, like every other scale here, and remembered per item
 * so it usually costs nothing to record.
 */
enum class StressWeight(val level: Int, val label: String) {
    MINOR(1, "Minor"),
    NOTABLE(2, "Notable"),
    MAJOR(3, "Major");

    companion object {
        val DEFAULT = NOTABLE

        fun fromLevel(level: Int): StressWeight =
            entries.firstOrNull { it.level == level } ?: DEFAULT

        fun parse(raw: String): StressWeight {
            val asNumber = raw.trim().toIntOrNull()
            if (asNumber != null) return fromLevel(asNumber)
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

/** One thing that happened, and which way it pushed. */
data class StressEvent(
    val id: String,
    val date: LocalDate,
    val label: String,
    val direction: StressDirection,
    val weight: StressWeight,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/**
 * An entry in the auto-populated stress catalog.
 *
 * [direction] and [weight] are the remembered defaults: a deadline is always an enhancer,
 * a walk is always a buster, so logging either is a single tap.
 */
data class StressDef(
    val name: String,
    val direction: StressDirection = StressDirection.DEFAULT,
    val weight: StressWeight = StressWeight.DEFAULT,
    val createdAt: Long = 0L,
    val lastUsed: LocalDate? = null,
    val useCount: Int = 0,
)
