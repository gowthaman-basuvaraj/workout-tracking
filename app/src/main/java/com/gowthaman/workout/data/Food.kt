package com.gowthaman.workout.data

import java.time.LocalDate

/**
 * Why something was eaten, rather than what it contained.
 *
 * A fixed set on purpose: the whole point of this log is counting ("30 snacks this month"),
 * and free text would fragment into snack / Snack / snacks and ruin every total.
 */
enum class Purpose(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    DRINK("Drink");

    /** English is not regular here: "lunchs" is the kind of thing people notice. */
    val plural: String
        get() = when (this) {
            BREAKFAST -> "breakfasts"
            LUNCH -> "lunches"
            DINNER -> "dinners"
            SNACK -> "snacks"
            DRINK -> "drinks"
        }

    fun count(n: Int): String = if (n == 1) label.lowercase() else plural

    companion object {
        val DEFAULT = SNACK

        fun fromName(name: String?): Purpose =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * How indulgent something was, on a deliberately coarse three-point scale.
 *
 * Three points keeps the decision to a glance — this gets answered once per coffee, a
 * hundred times a month — and keeps the dashboard's mix readable. [level] is what lands in
 * the CSV so the scale stays orderable on a laptop.
 */
enum class FoodScale(val level: Int, val label: String) {
    HEALTHY(1, "Healthy"),
    EVERYDAY(2, "Everyday"),
    INDULGENT(3, "Indulgent");

    companion object {
        val DEFAULT = EVERYDAY

        fun fromLevel(level: Int): FoodScale =
            entries.firstOrNull { it.level == level } ?: DEFAULT

        /** Accepts the stored number, and the name as a courtesy to hand-edited files. */
        fun parse(raw: String): FoodScale {
            val asNumber = raw.trim().toIntOrNull()
            if (asNumber != null) return fromLevel(asNumber)
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

/** One thing eaten or drunk, on one day. */
data class FoodEntry(
    val id: String,
    val date: LocalDate,
    val item: String,
    val purpose: Purpose,
    val scale: FoodScale,
    /** Occurrences, not portions — "two coffees", never "180 grams". */
    val qty: Int = 1,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/**
 * An entry in the auto-populated food catalog.
 *
 * [purpose] and [scale] are the remembered defaults for this item, which is what makes
 * logging the same coffee for the hundredth time a single tap.
 */
data class FoodDef(
    val name: String,
    val purpose: Purpose = Purpose.DEFAULT,
    val scale: FoodScale = FoodScale.DEFAULT,
    val createdAt: Long = 0L,
    val lastUsed: LocalDate? = null,
    val useCount: Int = 0,
)

fun FoodEntry.summary(): String = buildString {
    if (qty > 1) append("$qty x ")
    append(item)
}
