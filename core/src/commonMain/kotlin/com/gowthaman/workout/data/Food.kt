package com.gowthaman.workout.data

import kotlinx.datetime.LocalDate

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

/**
 * What was in it, as a handful of coarse tags rather than ingredients.
 *
 * Optional, and several at once: a dosa with curd is PLANTS and DAIRY. Four tags is
 * deliberately too few to describe a plate -- they are here so the dashboard can count how
 * often you reach for each, not so the log can describe a meal. Anything finer would be
 * nutrition tracking coming back in through the side door.
 */
enum class FoodTag(val label: String) {
    PLANTS("Plants"),
    EGG("Egg"),
    DAIRY("Dairy"),
    MEAT("Meat");

    companion object {
        /** Joined with a pipe, so the field never needs CSV quoting. */
        const val SEPARATOR = '|'

        fun fromName(name: String?): FoodTag? =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }

        /** Tolerant of hand-edited files: commas and spaces separate just as well. */
        fun parse(raw: String): Set<FoodTag> =
            raw.split(SEPARATOR, ',', ' ', ';').mapNotNull { fromName(it) }.toSet()

        /** Always declaration order, so one set always writes the same string. */
        fun render(tags: Set<FoodTag>): String =
            entries.filter { it in tags }.joinToString(SEPARATOR.toString()) { it.name }

        /** For the UI, where the labels read better than the stored names. */
        fun labels(tags: Set<FoodTag>): String =
            entries.filter { it in tags }.joinToString(" / ") { it.label }
    }
}

/** One thing eaten or drunk, on one day. */
data class FoodEntry(
    val id: String,
    val date: LocalDate,
    val item: String,
    val purpose: Purpose,
    val scale: FoodScale,
    /** Optional and possibly empty: not every item is worth describing. */
    val tags: Set<FoodTag> = emptySet(),
    /** Occurrences, not portions — "two coffees", never "180 grams". */
    val qty: Int = 1,
    val notes: String = "",
    val loggedAt: Long = 0L,
)

/**
 * An entry in the auto-populated food catalog.
 *
 * [purpose], [scale] and [tags] are the remembered defaults for this item, which is what
 * makes logging the same coffee for the hundredth time a single tap.
 */
data class FoodDef(
    val name: String,
    val purpose: Purpose = Purpose.DEFAULT,
    val scale: FoodScale = FoodScale.DEFAULT,
    val tags: Set<FoodTag> = emptySet(),
    val createdAt: Long = 0L,
    val lastUsed: LocalDate? = null,
    val useCount: Int = 0,
)

fun FoodEntry.summary(): String = buildString {
    if (qty > 1) append("$qty x ")
    append(item)
}
