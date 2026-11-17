package com.gowthaman.workout.data

import kotlin.math.min

/**
 * Everything about turning a typed name into a catalog entry.
 *
 * Shared because this is precisely where a second implementation would cause damage: if
 * the phone and the desktop disagreed about whether "coffee" is "Coffee", the folder would
 * quietly accumulate two of everything.
 */
object Catalog {

    /** How a typed name resolved against a catalog. */
    sealed interface Match {
        /** Exactly one entry. [name] is the catalog's spelling, not what was typed. */
        data class One(val name: String) : Match

        /** Several entries matched a partial name; the caller must narrow it down. */
        data class Many(val names: List<String>) : Match

        /** Nothing matched. [suggestions] are the closest spellings, possibly empty. */
        data class None(val suggestions: List<String>) : Match
    }

    /**
     * The catalog's own spelling for [typed], or [typed] unchanged when it is new.
     *
     * This is what stops "coffee", "Coffee" and " COFFEE " becoming three separate items:
     * they share a [catalogKey], so the first spelling logged wins from then on.
     */
    fun canonical(names: List<String>, typed: String): String {
        val key = catalogKey(typed)
        return names.firstOrNull { catalogKey(it) == key } ?: typed.trim()
    }

    fun contains(names: List<String>, typed: String): Boolean {
        val key = catalogKey(typed)
        return names.any { catalogKey(it) == key }
    }

    /**
     * Resolves a possibly-partial name.
     *
     * An exact match always wins outright, so a short name is never ambiguous against the
     * longer ones containing it — "Run" resolves to Run even when "Running" exists.
     */
    fun resolve(names: List<String>, query: String): Match {
        val q = catalogKey(query)
        if (q.isEmpty()) return Match.None(emptyList())

        names.firstOrNull { catalogKey(it) == q }?.let { return Match.One(it) }

        val prefix = names.filter { catalogKey(it).startsWith(q) }
        if (prefix.size == 1) return Match.One(prefix.single())
        if (prefix.size > 1) return Match.Many(prefix.sortedBy { catalogKey(it) })

        val substring = names.filter { catalogKey(it).contains(q) }
        if (substring.size == 1) return Match.One(substring.single())
        if (substring.size > 1) return Match.Many(substring.sortedBy { catalogKey(it) })

        return Match.None(suggest(names, query))
    }

    /** Closest spellings by edit distance — the "did you mean" list after a typo. */
    fun suggest(names: List<String>, query: String, limit: Int = 3): List<String> {
        val q = catalogKey(query)
        if (q.isEmpty()) return emptyList()
        val tolerance = when {
            q.length <= 4 -> 1
            q.length <= 8 -> 2
            else -> 3
        }
        return names
            .map { it to editDistance(q, catalogKey(it)) }
            .filter { it.second <= tolerance }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Levenshtein, two rows at a time; the catalogs here are tens of entries, not millions. */
    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    // ------------------------------------------------------------- rebuilding

    /**
     * Rebuilds the food catalog from logged entries, merged with names that exist only in
     * foods.csv. Usage counts and the remembered purpose/rating always come from the real
     * data, so the catalog can never drift away from the log.
     */
    fun rebuildFood(stored: List<FoodDef>, entries: List<FoodEntry>): List<FoodDef> {
        val byKey = LinkedHashMap<String, FoodDef>()
        stored.forEach { def -> byKey[catalogKey(def.name)] = def.copy(useCount = 0, lastUsed = null) }

        entries.groupBy { catalogKey(it.item) }.forEach { (key, rows) ->
            val newest = rows.maxByOrNull { it.loggedAt } ?: rows.last()
            val existing = byKey[key]
            byKey[key] = FoodDef(
                name = existing?.name ?: newest.item,
                purpose = newest.purpose,
                scale = newest.scale,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: rows.minOf { it.loggedAt },
                lastUsed = rows.maxOf { it.date },
                useCount = rows.sumOf { it.qty },
            )
        }
        return byKey.values.sortedBy { catalogKey(it.name) }
    }

    /** The same, for stress: direction and weight come from the most recent entry. */
    fun rebuildStress(stored: List<StressDef>, events: List<StressEvent>): List<StressDef> {
        val byKey = LinkedHashMap<String, StressDef>()
        stored.forEach { def -> byKey[catalogKey(def.name)] = def.copy(useCount = 0, lastUsed = null) }

        events.groupBy { catalogKey(it.label) }.forEach { (key, rows) ->
            val newest = rows.maxByOrNull { it.loggedAt } ?: rows.last()
            val existing = byKey[key]
            byKey[key] = StressDef(
                name = existing?.name ?: newest.label,
                direction = newest.direction,
                weight = newest.weight,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: rows.minOf { it.loggedAt },
                lastUsed = rows.maxOf { it.date },
                useCount = rows.size,
            )
        }
        return byKey.values.sortedBy { catalogKey(it.name) }
    }

    /** The same, for exercises: strength and cardio share one catalog keyed by name. */
    fun rebuildExercises(
        stored: List<ExerciseDef>,
        strength: List<StrengthSet>,
        cardio: List<CardioEntry>,
    ): List<ExerciseDef> {
        val byKey = LinkedHashMap<String, ExerciseDef>()
        stored.forEach { def -> byKey[catalogKey(def.name)] = def.copy(useCount = 0, lastUsed = null) }

        strength.groupBy { catalogKey(it.exercise) }.forEach { (key, rows) ->
            val newest = rows.maxByOrNull { it.loggedAt } ?: rows.first()
            val existing = byKey[key]
            byKey[key] = ExerciseDef(
                name = existing?.name ?: newest.exercise,
                kind = Kind.STRENGTH,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: rows.minOf { it.loggedAt },
                lastUsed = rows.maxOf { it.date },
                useCount = rows.size,
            )
        }

        cardio.groupBy { catalogKey(it.activity) }.forEach { (key, rows) ->
            val newest = rows.maxByOrNull { it.loggedAt } ?: rows.first()
            val existing = byKey[key]
            byKey[key] = ExerciseDef(
                name = existing?.name ?: newest.activity,
                kind = Kind.CARDIO,
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: rows.minOf { it.loggedAt },
                lastUsed = rows.maxOf { it.date },
                useCount = rows.size,
            )
        }

        return byKey.values.sortedBy { catalogKey(it.name) }
    }
}
