package com.gowthaman.workout.cli

import com.gowthaman.workout.data.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Thrown for anything the user can fix; main turns it into a message, not a stack trace. */
class UserError(message: String, val hint: String? = null) : Exception(message)

/**
 * A very small flag parser.
 *
 * Hand-rolled on purpose: a dependency-free jar is the whole point of shipping this as
 * `java -jar uncomplicated.jar`, and the grammar here is a dozen flags.
 */
class Args(raw: List<String>) {

    private val flags = LinkedHashSet<String>()
    private val values = LinkedHashMap<String, String>()
    val positional = ArrayList<String>()

    init {
        var i = 0
        while (i < raw.size) {
            val token = raw[i]
            when {
                token.startsWith("--") -> {
                    val name = token.removePrefix("--")
                    if (name.contains('=')) {
                        values[name.substringBefore('=')] = name.substringAfter('=')
                    } else if (name in VALUE_FLAGS) {
                        val next = raw.getOrNull(i + 1)
                            ?: throw UserError("--$name needs a value.")
                        values[name] = next
                        i++
                    } else {
                        flags += name
                    }
                }
                token.startsWith("-") && token.length > 1 && !token[1].isDigit() -> {
                    val short = token.removePrefix("-")
                    val boolean = SHORT_BOOLEAN[short]
                    if (boolean != null) {
                        flags += boolean
                    } else {
                        val name = SHORT[short]
                            ?: throw UserError("Unknown option $token.", "Try: unc help")
                        val next = raw.getOrNull(i + 1) ?: throw UserError("$token needs a value.")
                        values[name] = next
                        i++
                    }
                }
                else -> positional += token
            }
            i++
        }
    }

    fun has(name: String): Boolean = name in flags

    /**
     * Removes and returns the leading positional, once it is known to be a command.
     *
     * Parsing the whole line before dispatching is what lets global options appear
     * anywhere: `unc --dir X ls food` and `unc ls food --dir X` both work.
     */
    fun takeCommand(): String? = positional.removeFirstOrNull()
    fun value(name: String): String? = values[name]

    fun int(name: String, default: Int): Int {
        val raw = values[name] ?: return default
        return raw.toIntOrNull() ?: throw UserError("--$name expects a whole number, got \"$raw\".")
    }

    fun decimal(name: String): Double? {
        val raw = values[name] ?: return null
        return raw.replace(',', '.').toDoubleOrNull()
            ?: throw UserError("--$name expects a number, got \"$raw\".")
    }

    /** Unknown flags are an error, not a silent no-op: a typo must never log the wrong thing. */
    fun rejectUnknown(known: Set<String>) {
        val unknown = (flags + values.keys) - known - UNIVERSAL
        if (unknown.isNotEmpty()) {
            throw UserError(
                "Unknown option${if (unknown.size == 1) "" else "s"}: " +
                    unknown.joinToString(", ") { "--$it" },
                "Try: unc help",
            )
        }
    }

    /** `--date 2026-11-02`, or the shorthands people actually type. */
    fun date(today: LocalDate = today()): LocalDate {
        if (has("yesterday")) return today.minus(1, DateTimeUnit.DAY)
        val raw = values["date"] ?: return today
        return runCatching { LocalDate.parse(raw) }.getOrNull()
            ?: throw UserError("--date expects YYYY-MM-DD, got \"$raw\".")
    }

    companion object {
        /** Flags that consume the next token as their value. */
        private val VALUE_FLAGS = setOf("n", "qty", "note", "date", "dir", "sets", "time", "dist", "unit")
        private val SHORT = mapOf("n" to "n", "d" to "date", "m" to "note")
        private val SHORT_BOOLEAN = mapOf("h" to "help")
        /** Accepted everywhere, so each command need not list them. */
        val UNIVERSAL = setOf("dir", "date", "yesterday", "dry-run", "help")
    }
}
