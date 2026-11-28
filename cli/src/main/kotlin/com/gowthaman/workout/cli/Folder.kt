package com.gowthaman.workout.cli

import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.Catalog
import com.gowthaman.workout.data.CsvFormat
import com.gowthaman.workout.data.CsvStore
import com.gowthaman.workout.data.ExerciseDef
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.StressDef
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.Units
import java.io.File
import kotlinx.datetime.LocalDate

/**
 * The synced data folder, as seen from a desktop.
 *
 * The phone reaches the same folder through the Storage Access Framework; here it is a
 * plain directory. Both are a [CsvStore] over a [com.gowthaman.workout.data.DocumentStore],
 * so the two cannot disagree about what logging something does -- which they previously
 * could, and once did: this file used to carry its own copy of the append-and-rewrite
 * rules, and the copy that drifted was the one that wrote a nine-column row under an
 * eight-column header.
 *
 * What is left here is the part that genuinely is the CLI's own: reading the whole folder
 * once per command, rebuilding the catalogs, and inferring units for folders that predate
 * settings.csv.
 */
class Folder(val dir: File) {

    private val csv = CsvStore(DirectoryStore(dir))

    /**
     * The folder, read once.
     *
     * A command is a single short-lived process, so one read up front is simpler than
     * five lazy ones and makes the CLI agree with the app about load order.
     */
    private val snapshot by lazy { csv.load() }

    val strength: List<StrengthSet> get() = snapshot.strength
    val cardio: List<CardioEntry> get() = snapshot.cardio
    val food: List<FoodEntry> get() = snapshot.food
    val sleep: List<SleepNight> get() = snapshot.sleep
    val stress: List<StressEvent> get() = snapshot.stress

    val stressCatalog: List<StressDef> by lazy {
        Catalog.rebuildStress(snapshot.stressCatalog, stress)
    }

    /** Rebuilt from the logs, exactly as the app does it. */
    val foodCatalog: List<FoodDef> by lazy {
        Catalog.rebuildFood(snapshot.foodCatalog, food)
    }

    val exerciseCatalog: List<ExerciseDef> by lazy {
        Catalog.rebuildExercises(snapshot.catalog, strength, cardio)
    }

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

    fun appendFood(entry: FoodEntry) = csv.appendFood(entry)

    fun appendStrength(set: StrengthSet) = csv.appendStrength(set)

    fun appendStress(event: StressEvent) = csv.appendStress(event)

    fun appendCardio(entry: CardioEntry) = csv.appendCardio(entry)

    fun putSleep(night: SleepNight) = csv.putSleep(night)

    fun sleepOn(date: LocalDate): SleepNight? = sleep.firstOrNull { it.date == date }

    // ----------------------------------------------------------------- removing

    /** Removes one row by id, used by `undo`. Rewrites the month it was in. */
    fun removeFood(id: String): FoodEntry? =
        food.firstOrNull { it.id == id }?.takeIf { csv.removeFood(it) }

    fun removeStrength(id: String): StrengthSet? =
        strength.firstOrNull { it.id == id }?.takeIf { csv.removeStrength(it) }

    fun removeCardio(id: String): CardioEntry? =
        cardio.firstOrNull { it.id == id }?.takeIf { csv.removeCardio(it) }

    fun removeStress(id: String): StressEvent? =
        stress.firstOrNull { it.id == id }?.takeIf { csv.removeStress(it) }

    fun removeSleep(id: String): SleepNight? =
        sleep.firstOrNull { it.id == id }?.takeIf { csv.removeSleep(it) }

    fun entriesOn(date: LocalDate): Triple<List<StrengthSet>, List<CardioEntry>, List<FoodEntry>> =
        Triple(
            strength.filter { it.date == date },
            cardio.filter { it.date == date },
            food.filter { it.date == date },
        )
}
