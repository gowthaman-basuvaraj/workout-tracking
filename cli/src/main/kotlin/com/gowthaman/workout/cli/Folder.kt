package com.gowthaman.workout.cli

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Catalog
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.ExerciseDef
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth

/**
 * The synced data folder, as seen from a desktop.
 *
 * The phone reaches the same folder through the Storage Access Framework; here it is a
 * plain directory. Both go through [CsvFormat], so neither can invent its own idea of the
 * file format.
 */
class Folder(val dir: File) {

    val strength: List<StrengthSet> by lazy {
        read(CsvFormat.STRENGTH_PREFIX) { CsvFormat.parseStrength(it) }
            .sortedWith(compareBy({ it.date }, { it.exercise }, { it.setNo }))
    }

    val cardio: List<CardioEntry> by lazy {
        read(CsvFormat.CARDIO_PREFIX) { CsvFormat.parseCardio(it) }
            .sortedWith(compareBy({ it.date }, { it.activity }))
    }

    val food: List<FoodEntry> by lazy {
        read(CsvFormat.FOOD_PREFIX) { CsvFormat.parseFood(it) }
            .sortedWith(compareBy({ it.date }, { it.loggedAt }))
    }

    /** Rebuilt from the logs, exactly as the app does it. */
    val foodCatalog: List<FoodDef> by lazy {
        val stored = File(dir, CsvFormat.FOOD_CATALOG_FILE)
            .takeIf { it.isFile }
            ?.let { CsvFormat.parseFoodCatalog(it.readText()) }
            .orEmpty()
        Catalog.rebuildFood(stored, food)
    }

    val exerciseCatalog: List<ExerciseDef> by lazy {
        val stored = File(dir, CsvFormat.CATALOG_FILE)
            .takeIf { it.isFile }
            ?.let { CsvFormat.parseCatalog(it.readText()) }
            .orEmpty()
        Catalog.rebuildExercises(stored, strength, cardio)
    }

    private fun <T> read(prefix: String, parse: (String) -> List<T>): List<T> =
        dir.listFiles()
            ?.filter {
                it.isFile && it.name.startsWith(prefix) && it.name.endsWith(CsvFormat.EXT) &&
                    !CsvFormat.isConflictCopy(it.name)
            }
            ?.flatMap { parse(it.readText(StandardCharsets.UTF_8)) }
            .orEmpty()

    /** True when the app has published settings.csv into this folder. */
    val hasPublishedSettings: Boolean get() = File(dir, CsvFormat.SETTINGS_FILE).isFile

    /**
     * The app's settings, as published into the folder.
     *
     * Read-only here by design: the app owns these values and overwrites the file whenever
     * one changes, so editing it on a computer achieves nothing.
     *
     * Units fall back to whatever the most recent logged row used, which is right for
     * anyone whose folder predates this file.
     */
    val settings: Settings by lazy {
        val fallback = Settings(weightUnit = inferredWeightUnit(), distanceUnit = inferredDistanceUnit())
        File(dir, CsvFormat.SETTINGS_FILE)
            .takeIf { it.isFile }
            ?.let { CsvFormat.parseSettings(it.readText(), fallback) }
            ?: fallback
    }

    private fun inferredWeightUnit(): String =
        strength.filter { it.weight > 0 }.maxByOrNull { it.loggedAt }?.unit ?: Units.KG

    private fun inferredDistanceUnit(): String =
        cardio.filter { it.distance > 0 }.maxByOrNull { it.loggedAt }?.distUnit ?: Units.KM

    fun weightUnit(): String = settings.weightUnit

    fun distanceUnit(): String = settings.distanceUnit

    // ------------------------------------------------------------------ writing

    /**
     * Appends one record to its month file, creating the file with a header if needed.
     *
     * Appending rather than rewriting is deliberate: the app rewrites whole month files,
     * and a sync client has a far smaller window to trip over a 90-byte append than over
     * a full rewrite.
     */
    fun appendFood(entry: FoodEntry) =
        append(CsvFormat.foodFile(YearMonth.from(entry.date)), CsvFormat.FOOD_HEADER, CsvFormat.foodRow(entry))

    fun appendStrength(set: StrengthSet) =
        append(CsvFormat.strengthFile(YearMonth.from(set.date)), CsvFormat.STRENGTH_HEADER, CsvFormat.strengthRow(set))

    fun appendCardio(entry: CardioEntry) =
        append(CsvFormat.cardioFile(YearMonth.from(entry.date)), CsvFormat.CARDIO_HEADER, CsvFormat.cardioRow(entry))

    private fun append(fileName: String, header: List<String>, row: String) {
        val file = File(dir, fileName)
        val needsHeader = !file.isFile || file.length() == 0L
        val text = buildString {
            if (needsHeader) append(com.gowthaman.workout.data.Csv.row(header)).append('\n')
            append(row).append('\n')
        }
        // A file the app wrote always ends in a newline, but a hand-edited one might not.
        if (!needsHeader && !endsWithNewline(file)) {
            file.appendText("\n", StandardCharsets.UTF_8)
        }
        file.appendText(text, StandardCharsets.UTF_8)
    }

    private fun endsWithNewline(file: File): Boolean {
        if (file.length() == 0L) return true
        file.inputStream().use { stream ->
            stream.channel.position(file.length() - 1)
            val last = stream.read()
            return last == '\n'.code
        }
    }

    /** Removes one row by id, used by `undo`. Rewrites the month it was in. */
    fun removeFood(id: String): FoodEntry? {
        val entry = food.firstOrNull { it.id == id } ?: return null
        val month = YearMonth.from(entry.date)
        val file = File(dir, CsvFormat.foodFile(month))
        if (!file.isFile) return null
        val kept = CsvFormat.parseFood(file.readText()).filterNot { it.id == id }
        if (kept.isEmpty()) file.delete() else file.writeText(CsvFormat.renderFood(kept), StandardCharsets.UTF_8)
        return entry
    }

    fun removeStrength(id: String): StrengthSet? {
        val set = strength.firstOrNull { it.id == id } ?: return null
        val file = File(dir, CsvFormat.strengthFile(YearMonth.from(set.date)))
        if (!file.isFile) return null
        val kept = CsvFormat.parseStrength(file.readText()).filterNot { it.id == id }
        if (kept.isEmpty()) file.delete() else file.writeText(CsvFormat.renderStrength(kept), StandardCharsets.UTF_8)
        return set
    }

    fun removeCardio(id: String): CardioEntry? {
        val entry = cardio.firstOrNull { it.id == id } ?: return null
        val file = File(dir, CsvFormat.cardioFile(YearMonth.from(entry.date)))
        if (!file.isFile) return null
        val kept = CsvFormat.parseCardio(file.readText()).filterNot { it.id == id }
        if (kept.isEmpty()) file.delete() else file.writeText(CsvFormat.renderCardio(kept), StandardCharsets.UTF_8)
        return entry
    }

    fun entriesOn(date: LocalDate): Triple<List<StrengthSet>, List<CardioEntry>, List<FoodEntry>> =
        Triple(
            strength.filter { it.date == date },
            cardio.filter { it.date == date },
            food.filter { it.date == date },
        )
}
