package com.gowthaman.workout.cli

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Catalog
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.agoLabel
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.shortLabel
import com.gowthaman.workout.data.summary
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

private const val CONFIG = ".jotter"
private const val LAST = ".jotter-last"

fun main(rawArgs: Array<String>) {
    // Console code pages vary; a food name with an accent in it should still print.
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    System.setErr(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"))
    try {
        run(rawArgs.toList())
    } catch (e: UserError) {
        System.err.println("jotter: ${e.message}")
        e.hint?.let { System.err.println("  $it") }
        exitProcess(1)
    }
}

private fun run(raw: List<String>) {
    if (raw.isEmpty()) return interactive()

    // Parsed before dispatch, so --dir and friends may appear before the command.
    val args = Args(raw)
    val first = args.positional.firstOrNull()

    if (args.has("help") || first == "help") {
        if (first == "help") args.takeCommand()
        return help(args.positional.firstOrNull())
    }
    if (args.has("version") || first == "version") return println("jotter 1.0")

    when (first) {
        "init" -> { args.takeCommand(); return init(args.positional.toList()) }
        "ls", "list" -> { args.takeCommand(); return list(args) }
        "today", "day" -> { args.takeCommand(); return showDay(args) }
        "undo" -> { args.takeCommand(); return undo(args) }
        "food" -> { args.takeCommand(); return logFood(args, explicit = true) }
        "set", "lift" -> { args.takeCommand(); return logSet(args) }
        "cardio" -> { args.takeCommand(); return logCardio(args) }
    }

    if (first == null) {
        throw UserError("Nothing to log.", "Try: jotter help")
    }
    // Not a command, so it is a name: `jotter coffee`.
    logByName(args)
}

// --------------------------------------------------------------------- folder

private fun resolveFolder(args: Args): Folder {
    val explicit = args.value("dir") ?: System.getenv("JOTTER_DIR")
    if (explicit != null) return folderAt(File(explicit), "the path you gave")

    val configured = configFile().takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    if (configured != null) return folderAt(File(configured), "~/$CONFIG")

    val here = File(".").canonicalFile
    if (looksLikeFolder(here)) return Folder(here)

    throw UserError(
        "I do not know where your data folder is.",
        "Run:  jotter init /path/to/your/synced/folder",
    )
}

private fun folderAt(dir: File, source: String): Folder {
    if (!dir.isDirectory) throw UserError("$dir is not a directory (from $source).")
    return Folder(dir.canonicalFile)
}

private fun looksLikeFolder(dir: File): Boolean =
    dir.listFiles()?.any {
        it.isFile && (it.name.startsWith(CsvFormat.FOOD_PREFIX) ||
            it.name.startsWith(CsvFormat.STRENGTH_PREFIX) ||
            it.name.startsWith(CsvFormat.CARDIO_PREFIX) ||
            it.name == CsvFormat.FOOD_CATALOG_FILE)
    } == true

private fun configFile() = File(System.getProperty("user.home"), CONFIG)
private fun lastFile() = File(System.getProperty("user.home"), LAST)

private fun init(rest: List<String>) {
    val path = rest.firstOrNull()
        ?: throw UserError("init needs a path.", "jotter init /path/to/your/synced/folder")
    val dir = File(path).canonicalFile
    if (!dir.isDirectory) throw UserError("$dir is not a directory.")
    if (!looksLikeFolder(dir)) {
        println("Note: no Jotter CSV files in $dir yet. Saving it anyway.")
    }
    configFile().writeText(dir.path)
    println("Data folder set to $dir")
    println("Stored in ${configFile()}. Override any time with --dir or \$JOTTER_DIR.")
}

// ----------------------------------------------------------------- resolving

/**
 * Turns a typed name into a catalog entry, or explains why it could not.
 *
 * Creating a new entry needs --new. Typing blind is far more typo-prone than tapping a
 * chip on the phone, and a typo here would put "Coffe" in the picker permanently.
 */
private fun resolveOrFail(names: List<String>, query: String, kindLabel: String, args: Args): String {
    return when (val match = Catalog.resolve(names, query)) {
        is Catalog.Match.One -> match.name
        is Catalog.Match.Many -> throw UserError(
            "\"$query\" matches ${match.names.size} $kindLabel entries: ${match.names.joinToString(", ")}",
            "Type more of the name.",
        )
        is Catalog.Match.None -> {
            if (args.has("new")) query.trim()
            else throw UserError(
                "No $kindLabel called \"$query\".",
                buildString {
                    if (match.suggestions.isNotEmpty()) {
                        append("Did you mean: ${match.suggestions.joinToString(", ")}?  ")
                    }
                    append("Use --new to add it, or `jotter ls` to see what exists.")
                },
            )
        }
    }
}

// -------------------------------------------------------------------- logging

private fun logByName(args: Args) {
    val name = args.positional.joinToString(" ").trim()
    if (name.isEmpty()) throw UserError("Nothing to log.")
    val folder = resolveFolder(args)

    val asFood = Catalog.resolve(folder.foodCatalog.map { it.name }, name)
    val asExercise = Catalog.resolve(folder.exerciseCatalog.map { it.name }, name)

    // Foods are checked first: the bare form exists for the thing logged most often, and
    // an ambiguous name should say so rather than quietly take the exercise reading.
    when {
        asFood is Catalog.Match.One && asExercise is Catalog.Match.One -> throw UserError(
            "\"$name\" is both a food (${asFood.name}) and an exercise (${asExercise.name}).",
            "Say which: jotter food $name   or   jotter set $name 10x40",
        )
        asFood is Catalog.Match.Many -> throw UserError(
            "\"$name\" matches ${asFood.names.size} foods: ${asFood.names.joinToString(", ")}",
            "Type more of the name.",
        )
        asFood is Catalog.Match.None && asExercise is Catalog.Match.One -> throw UserError(
            "\"${asExercise.name}\" is an exercise, which needs reps and weight.",
            "Try: jotter set $name 10x40",
        )
    }
    logFood(args, explicit = false)
}

private fun logFood(args: Args, explicit: Boolean) {
    args.rejectUnknown(
        setOf("n", "qty", "note", "new") + Purpose.entries.map { it.name.lowercase() } +
            FoodScale.entries.map { it.name.lowercase() },
    )
    val folder = resolveFolder(args)
    val query = args.positional.joinToString(" ").trim()
    if (query.isEmpty()) {
        throw UserError("Which food?", "jotter food coffee    -    jotter ls food")
    }

    val names = folder.foodCatalog.map { it.name }
    val name = resolveOrFail(names, query, "food", args)
    val known = folder.foodCatalog.firstOrNull { catalogKey(it.name) == catalogKey(name) }

    val purpose = Purpose.entries.firstOrNull { args.has(it.name.lowercase()) }
        ?: known?.purpose
        ?: Purpose.DEFAULT
    val scale = FoodScale.entries.firstOrNull { args.has(it.name.lowercase()) }
        ?: known?.scale
        ?: FoodScale.DEFAULT
    val qty = (args.value("qty") ?: args.value("n"))?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val date = args.date()

    val entry = FoodEntry(
        id = CsvFormat.newId(),
        date = date,
        item = name,
        purpose = purpose,
        scale = scale,
        qty = qty,
        notes = args.value("note").orEmpty(),
        loggedAt = System.currentTimeMillis(),
    )

    if (args.has("dry-run")) {
        println("would log: ${describe(entry)}  on ${date.shortLabel()}")
        return
    }

    folder.appendFood(entry)
    rememberForUndo("food", entry.id)
    println("logged  ${describe(entry)}" + onDate(date))
    if (known == null) println("        new item - it will appear in the app's picker too")
    recap(folder, entry, date)
}

private fun describe(entry: FoodEntry): String =
    "${entry.summary()}  -  ${entry.purpose.label} / ${entry.scale.label}"

private fun onDate(date: LocalDate): String =
    if (date == LocalDate.now()) "" else "  on ${date.shortLabel()}"

/** The same nudge the app shows at the moment of logging. */
private fun recap(folder: Folder, entry: FoodEntry, date: LocalDate) {
    val key = catalogKey(entry.item)
    val recent = folder.food.filter {
        catalogKey(it.item) == key && it.date <= date && ChronoUnit.DAYS.between(it.date, date) < 30
    }
    val total = recent.sumOf { it.qty } + entry.qty
    println("        that is $total in the last 30 days")
}

private fun logSet(args: Args) {
    args.rejectUnknown(setOf("note", "new", "sets", "unit", "bw"))
    val folder = resolveFolder(args)
    if (args.positional.size < 2) {
        throw UserError(
            "set needs an exercise and reps x weight.",
            "jotter set bench 10x40    -    jotter set pullups 8 --bw",
        )
    }

    val query = args.positional.dropLast(1).joinToString(" ")
    val spec = args.positional.last()
    val name = resolveOrFail(folder.exerciseCatalog.map { it.name }, query, "exercise", args)

    val reps: Int
    val weight: Double
    if (spec.contains('x', ignoreCase = true)) {
        val parts = spec.split('x', 'X')
        reps = parts[0].trim().toIntOrNull()
            ?: throw UserError("Could not read reps from \"$spec\".", "Expected: 10x40")
        weight = parts.getOrNull(1)?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?: throw UserError("Could not read weight from \"$spec\".", "Expected: 10x40")
    } else {
        reps = spec.toIntOrNull()
            ?: throw UserError("Could not read \"$spec\".", "Expected 10x40, or 8 with --bw")
        weight = 0.0
    }

    val bodyweight = args.has("bw")
    if (weight == 0.0 && !bodyweight) {
        throw UserError(
            "A set with no weight needs --bw to say it is a bodyweight movement.",
            "jotter set pullups 8 --bw",
        )
    }

    val unit = args.value("unit") ?: folder.weightUnit()
    val date = args.date()
    val setCount = args.int("sets", 1).coerceAtLeast(1)
    val already = folder.strength.count { it.date == date && catalogKey(it.exercise) == catalogKey(name) }

    val sets = (1..setCount).map { i ->
        StrengthSet(
            id = CsvFormat.newId(),
            date = date,
            exercise = name,
            setNo = already + i,
            reps = reps,
            weight = weight,
            unit = unit,
            bodyweight = bodyweight,
            notes = args.value("note").orEmpty(),
            loggedAt = System.currentTimeMillis() + i,
        )
    }

    val summary = "$name  -  $setCount x $reps @ ${sets.first().loadLabel(unit)}"
    if (args.has("dry-run")) {
        println("would log: $summary" + onDate(date))
        return
    }
    sets.forEach { folder.appendStrength(it) }
    rememberForUndo("strength", sets.joinToString(",") { it.id })
    println("logged  $summary" + onDate(date))
}

private fun logCardio(args: Args) {
    args.rejectUnknown(setOf("note", "new", "time", "dist", "unit"))
    val folder = resolveFolder(args)
    val query = args.positional.joinToString(" ").trim()
    if (query.isEmpty()) {
        throw UserError("Which activity?", "jotter cardio run --time 30 --dist 5.2")
    }

    val name = resolveOrFail(folder.exerciseCatalog.map { it.name }, query, "activity", args)
    val minutes = args.decimal("time")
    val distance = args.decimal("dist") ?: 0.0
    if (minutes == null && distance <= 0.0) {
        throw UserError("A cardio session needs --time, --dist, or both.")
    }

    val entry = CardioEntry(
        id = CsvFormat.newId(),
        date = args.date(),
        activity = name,
        durationSec = ((minutes ?: 0.0) * 60).toInt(),
        distance = distance,
        distUnit = args.value("unit") ?: folder.distanceUnit(),
        notes = args.value("note").orEmpty(),
        loggedAt = System.currentTimeMillis(),
    )

    val parts = buildList {
        if (entry.durationSec > 0) add(formatDuration(entry.durationSec))
        if (entry.distance > 0) add("${entry.distance.fmt(2)} ${entry.distUnit}")
    }
    val summary = "$name  -  ${parts.joinToString("  -  ")}"
    if (args.has("dry-run")) {
        println("would log: $summary" + onDate(entry.date))
        return
    }
    folder.appendCardio(entry)
    rememberForUndo("cardio", entry.id)
    println("logged  $summary" + onDate(entry.date))
}

// ----------------------------------------------------------------------- undo

private fun rememberForUndo(kind: String, ids: String) =
    runCatching { lastFile().writeText("$kind $ids") }

private fun undo(args: Args) {
    val folder = resolveFolder(args)
    val note = lastFile().takeIf { it.isFile }?.readText()?.trim()
        ?: throw UserError("Nothing to undo.", "undo only removes what this command logged.")
    val kind = note.substringBefore(' ')
    val ids = note.substringAfter(' ').split(',').filter { it.isNotBlank() }
    if (ids.isEmpty()) throw UserError("Nothing to undo.")

    val removed = when (kind) {
        "food" -> ids.mapNotNull { folder.removeFood(it) }.map { describe(it) }
        "strength" -> ids.mapNotNull { folder.removeStrength(it) }
            .map { "${it.exercise} ${it.reps} x ${it.loadLabel(it.unit)}" }
        "cardio" -> ids.mapNotNull { folder.removeCardio(it) }.map { it.activity }
        else -> emptyList()
    }
    if (removed.isEmpty()) {
        throw UserError("That entry is already gone.", "Nothing was changed.")
    }
    lastFile().delete()
    removed.forEach { println("removed $it") }
}

// ---------------------------------------------------------------- read-only

private fun list(args: Args) {
    val folder = resolveFolder(args)
    val what = args.positional.firstOrNull()?.lowercase()
    val filter = args.positional.drop(1).joinToString(" ").ifBlank { null }

    val showFood = what == null || what in setOf("food", "foods")
    val showStrength = what == null || what in setOf("strength", "lift", "lifts", "exercise", "exercises")
    val showCardio = what == null || what in setOf("cardio")
    if (!showFood && !showStrength && !showCardio) {
        throw UserError("Unknown list \"$what\".", "jotter ls [food|strength|cardio] [filter]")
    }

    if (showFood) {
        val rows = folder.foodCatalog
            .filter { filter == null || it.name.contains(filter, ignoreCase = true) }
            .sortedWith(compareByDescending<com.gowthaman.workout.data.FoodDef> { it.lastUsed }
                .thenBy { catalogKey(it.name) })
        section("FOOD", rows.map {
            Triple(it.name, "${it.purpose.label} / ${it.scale.label}", summaryOf(it.useCount, "logged", it.lastUsed))
        })
    }
    if (showStrength || showCardio) {
        val kinds = buildSet {
            if (showStrength) add(Kind.STRENGTH)
            if (showCardio) add(Kind.CARDIO)
        }
        val rows = folder.exerciseCatalog
            .filter { it.kind in kinds }
            .filter { filter == null || it.name.contains(filter, ignoreCase = true) }
            .sortedWith(compareByDescending<com.gowthaman.workout.data.ExerciseDef> { it.lastUsed }
                .thenBy { catalogKey(it.name) })
        section(if (kinds.size == 1 && showCardio) "CARDIO" else "EXERCISES", rows.map {
            Triple(it.name, it.kind.name.lowercase(), summaryOf(it.useCount, if (it.kind == Kind.CARDIO) "sessions" else "sets", it.lastUsed))
        })
    }
}

private fun summaryOf(count: Int, noun: String, last: LocalDate?): String =
    if (last == null) "never logged" else "$count $noun, ${last.agoLabel()}"

private fun section(title: String, rows: List<Triple<String, String, String>>) {
    println(title)
    if (rows.isEmpty()) {
        println("  (nothing yet)")
        println()
        return
    }
    val width = rows.maxOf { it.first.length }.coerceAtMost(28)
    val midWidth = rows.maxOf { it.second.length }.coerceAtMost(22)
    rows.forEach { (name, mid, tail) ->
        println("  ${name.padEnd(width)}  ${mid.padEnd(midWidth)}  $tail")
    }
    println()
}

private fun showDay(args: Args) {
    val folder = resolveFolder(args)
    val date = args.positional.firstOrNull()?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
            ?: throw UserError("Could not read the date \"$it\".", "Expected YYYY-MM-DD")
    } ?: args.date()

    val (sets, cardio, food) = folder.entriesOn(date)
    println("${date.shortLabel()}  (${date.agoLabel()})")
    println()
    if (sets.isEmpty() && cardio.isEmpty() && food.isEmpty()) {
        println("  nothing logged")
        return
    }
    if (sets.isNotEmpty()) {
        println("  TRAINING")
        sets.groupBy { catalogKey(it.exercise) }.forEach { (_, rows) ->
            val unit = rows.first().unit
            println("    ${rows.first().exercise}: " +
                rows.sortedBy { it.setNo }.joinToString(", ") { "${it.reps} @ ${it.loadLabel(unit)}" })
        }
        println()
    }
    if (cardio.isNotEmpty()) {
        cardio.forEach {
            val bits = buildList {
                if (it.durationSec > 0) add(formatDuration(it.durationSec))
                if (it.distance > 0) add("${it.distance.fmt(2)} ${it.distUnit}")
            }
            println("  CARDIO")
            println("    ${it.activity}: ${bits.joinToString("  -  ")}")
        }
        println()
    }
    if (food.isNotEmpty()) {
        println("  FOOD")
        food.forEach { println("    ${it.summary().padEnd(24)} ${it.purpose.label} / ${it.scale.label}") }
        println()
        println("    ${food.sumOf { it.qty }} items")
    }
}

// --------------------------------------------------------------- interactive

private fun interactive() {
    val folder = resolveFolder(Args(emptyList()))
    val recent = folder.foodCatalog
        .filter { it.lastUsed != null }
        .sortedWith(compareByDescending<com.gowthaman.workout.data.FoodDef> { it.lastUsed }
            .thenByDescending { it.useCount })
        .take(12)

    if (recent.isEmpty()) {
        println("Nothing logged yet. Try:  jotter food coffee --new --drink --everyday")
        return
    }

    println("What did you have?  (number or name, blank to cancel)")
    println()
    recent.forEachIndexed { i, def ->
        println("  ${(i + 1).toString().padStart(2)}. ${def.name.padEnd(22)} ${def.purpose.label} / ${def.scale.label}")
    }
    println()
    print("> ")
    val input = readlnOrNull()?.trim().orEmpty()
    if (input.isEmpty()) return println("nothing logged")

    val chosen = input.toIntOrNull()?.let { recent.getOrNull(it - 1)?.name } ?: input
    logFood(Args(listOf(chosen)), explicit = true)
}

// ---------------------------------------------------------------------- help

private fun help(topic: String?) {
    when (topic) {
        "food" -> println(HELP_FOOD)
        "set", "lift" -> println(HELP_SET)
        "cardio" -> println(HELP_CARDIO)
        "ls", "list" -> println(HELP_LS)
        null -> println(HELP_MAIN)
        else -> throw UserError("No help for \"$topic\".", "Try: jotter help")
    }
}

private val HELP_MAIN = """
jotter - log training and eating from the command line

  Writes straight into your synced folder, in the same CSV the phone app uses.
  The app picks changes up the next time it opens.

USAGE
  jotter                       pick from a list of what you log most
  jotter <food>                log a food you have logged before
  jotter food <name> [opts]    the same, said explicitly
  jotter set <ex> <reps>x<wt>  log a strength set
  jotter cardio <act> [opts]   log a cardio session
  jotter ls [what] [filter]    see what names exist
  jotter today [date]          show a day
  jotter undo                  remove what you just logged
  jotter init <path>           remember where your data folder is
  jotter help [command]        more detail on one command

EVERYWHERE
  --dir <path>      use this folder for one command
  --date YYYY-MM-DD log against another day
  --yesterday       shorthand for the above
  --dry-run         show what would be written, write nothing

NAMES
  Names are matched loosely and case does not matter: "cof" finds Coffee, and
  logging "coffee" writes "Coffee" so you never end up with two of them.
  An unknown name is refused rather than guessed - add it with --new.

FINDING THE FOLDER, in order
  --dir, then ${'$'}JOTTER_DIR, then ~/.jotter, then the current directory.

EXAMPLES
  jotter coffee                       one coffee, with its usual purpose and rating
  jotter biscuits -n 2 --indulgent    two, overriding the remembered rating
  jotter set bench 10x40 --sets 3     three sets of ten at 40
  jotter cardio run --time 32 --dist 5.4
  jotter ls food cof                  which foods match "cof"
""".trimIndent()

private val HELP_FOOD = """
jotter food <name> [options]        log something you ate or drank

  Purpose and rating default to however you last logged that item, so most of
  the time the name alone is the whole command.

OPTIONS
  -n, --n <count>    how many (default 1). Occurrences, not portions.
  --breakfast --lunch --dinner --snack --drink      override the purpose
  --healthy --everyday --indulgent                  override the rating
  --note <text>      a note
  --new              allow creating a name that is not in the catalog yet

EXAMPLES
  jotter food coffee
  jotter coffee -n 2
  jotter food "flat white" --new --drink --indulgent
  jotter food cake --indulgent --note "birthday"
""".trimIndent()

private val HELP_SET = """
jotter set <exercise> <reps>x<weight> [options]      log a strength set

  Weight unit is taken from your most recent weighted set, so it matches the app
  without needing your phone's settings.

OPTIONS
  --sets <n>     log the same set n times (default 1)
  --bw           a bodyweight movement; weight is extra load, and may be absent
  --unit <kg|lb> override the inferred unit
  --note <text>  a note
  --new          allow an exercise name that is not in the catalog yet

EXAMPLES
  jotter set bench 10x40
  jotter set squat 8x100 --sets 3
  jotter set pullups 8 --bw
  jotter set dips 6x10 --bw          six reps with 10 of added load
""".trimIndent()

private val HELP_CARDIO = """
jotter cardio <activity> [options]      log a cardio session

OPTIONS
  --time <minutes>  duration, may be decimal
  --dist <amount>   distance, in your usual unit
  --unit <km|mi>    override the inferred unit
  --note <text>     a note
  --new             allow an activity not in the catalog yet

EXAMPLES
  jotter cardio run --time 32 --dist 5.4
  jotter cardio cycling --time 45
""".trimIndent()

private val HELP_LS = """
jotter ls [food|strength|cardio] [filter]      see what names exist

  With no arguments, lists everything you have ever logged, most recent first,
  with each item's remembered defaults and how often you have logged it.

EXAMPLES
  jotter ls
  jotter ls food
  jotter ls food cof
  jotter ls strength
""".trimIndent()
