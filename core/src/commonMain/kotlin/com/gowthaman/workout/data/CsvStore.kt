package com.gowthaman.workout.data

/**
 * The data set, as a folder of CSV files.
 *
 * This is the layer that knows which files exist and what goes in them: that
 * `food-2026-11.csv` holds November's food, that an emptied month leaves no file rather
 * than a lone header, that a conflict copy dropped in by a sync client is ignored. None of
 * that is platform-specific, so none of it lives on a platform.
 *
 * All the actual I/O goes through [DocumentStore], which is the only thing a new platform
 * has to implement. This class used to be the Android one and carried a `treeUri` through
 * every method; the store is bound to its location now, so that parameter is gone.
 *
 * Month-partitioned files are what make the folder safe under Syncthing or Dropbox:
 * logging today only rewrites the current month, and there is no database or lock file.
 */
class CsvStore(private val store: DocumentStore) {

    data class Snapshot(
        val strength: List<StrengthSet> = emptyList(),
        val cardio: List<CardioEntry> = emptyList(),
        val catalog: List<ExerciseDef> = emptyList(),
        val food: List<FoodEntry> = emptyList(),
        val foodCatalog: List<FoodDef> = emptyList(),
        val sleep: List<SleepNight> = emptyList(),
        val stress: List<StressEvent> = emptyList(),
        val stressCatalog: List<StressDef> = emptyList(),
    )

    // ------------------------------------------------------------------ reading

    fun load(): Snapshot {
        val strength = ArrayList<StrengthSet>()
        val cardio = ArrayList<CardioEntry>()
        val food = ArrayList<FoodEntry>()
        val sleep = ArrayList<SleepNight>()
        val stress = ArrayList<StressEvent>()
        var catalog: List<ExerciseDef> = emptyList()
        var foodCatalog: List<FoodDef> = emptyList()
        var stressCatalog: List<StressDef> = emptyList()

        for (name in store.list()) {
            if (CsvFormat.isConflictCopy(name)) continue
            when {
                name == CsvFormat.FOOD_CATALOG_FILE ->
                    foodCatalog = CsvFormat.parseFoodCatalog(text(name))
                name == CsvFormat.STRESS_CATALOG_FILE ->
                    stressCatalog = CsvFormat.parseStressCatalog(text(name))
                name == CsvFormat.CATALOG_FILE ->
                    catalog = CsvFormat.parseCatalog(text(name))
                !name.endsWith(CsvFormat.EXT) -> Unit
                name.startsWith(CsvFormat.STRENGTH_PREFIX) ->
                    strength += CsvFormat.parseStrength(text(name))
                name.startsWith(CsvFormat.CARDIO_PREFIX) ->
                    cardio += CsvFormat.parseCardio(text(name))
                name.startsWith(CsvFormat.FOOD_PREFIX) ->
                    food += CsvFormat.parseFood(text(name))
                name.startsWith(CsvFormat.SLEEP_PREFIX) ->
                    sleep += CsvFormat.parseSleep(text(name))
                name.startsWith(CsvFormat.STRESS_PREFIX) ->
                    stress += CsvFormat.parseStress(text(name))
            }
        }

        return Snapshot(
            strength = strength.sortedWith(compareBy({ it.date }, { it.exercise }, { it.setNo })),
            cardio = cardio.sortedWith(compareBy({ it.date }, { it.activity })),
            catalog = catalog,
            food = food.sortedWith(compareBy({ it.date }, { it.loggedAt })),
            foodCatalog = foodCatalog,
            sleep = sleep.sortedBy { it.date },
            stress = stress.sortedWith(compareBy({ it.date }, { it.loggedAt })),
            stressCatalog = stressCatalog,
        )
    }

    private fun text(name: String): String = store.read(name).orEmpty()

    // ------------------------------------------------------------------ writing

    fun saveStrengthMonth(month: YearMonth, rows: List<StrengthSet>) =
        writeOrDelete(CsvFormat.strengthFile(month), rows) { CsvFormat.renderStrength(it) }

    fun saveCardioMonth(month: YearMonth, rows: List<CardioEntry>) =
        writeOrDelete(CsvFormat.cardioFile(month), rows) { CsvFormat.renderCardio(it) }

    fun saveFoodMonth(month: YearMonth, rows: List<FoodEntry>) =
        writeOrDelete(CsvFormat.foodFile(month), rows) { CsvFormat.renderFood(it) }

    fun saveSleepMonth(month: YearMonth, rows: List<SleepNight>) =
        writeOrDelete(CsvFormat.sleepFile(month), rows) { CsvFormat.renderSleep(it) }

    fun saveStressMonth(month: YearMonth, rows: List<StressEvent>) =
        writeOrDelete(CsvFormat.stressFile(month), rows) { CsvFormat.renderStress(it) }

    fun saveStressCatalog(defs: List<StressDef>) =
        store.write(CsvFormat.STRESS_CATALOG_FILE, CsvFormat.renderStressCatalog(defs))

    fun saveCatalog(defs: List<ExerciseDef>) =
        store.write(CsvFormat.CATALOG_FILE, CsvFormat.renderCatalog(defs))

    fun saveFoodCatalog(defs: List<FoodDef>) =
        store.write(CsvFormat.FOOD_CATALOG_FILE, CsvFormat.renderFoodCatalog(defs))

    /**
     * Publishes the settings into the folder for the desktop CLI to read.
     *
     * Strictly one-way: the app never reads this file back, so it is always the phone's
     * values that win and there is nothing to merge.
     */
    fun saveSettings(settings: Settings) =
        store.write(CsvFormat.SETTINGS_FILE, CsvFormat.renderSettings(settings))

    /** An emptied month leaves no file behind rather than a lone header. */
    private fun <T> writeOrDelete(name: String, rows: List<T>, render: (List<T>) -> String) {
        if (rows.isEmpty()) store.delete(name) else store.write(name, render(rows))
    }

    // ---------------------------------------------------------------- appending

    /**
     * Adds one row to its month file, creating the file with a header if needed.
     *
     * Appending rather than rewriting is deliberate: the app rewrites whole month files,
     * and a sync client has a far smaller window to trip over a 90-byte append than over
     * a full rewrite.
     */
    fun appendRow(name: String, header: List<String>, row: String) {
        val existing = store.read(name)

        if (existing.isNullOrEmpty()) {
            store.write(name, Csv.row(header) + "\n" + row + "\n")
            return
        }

        // A file written before a column existed cannot take today's row as-is -- every
        // value past the missing column would land one place to the left. Bringing the
        // file up to a header that holds both costs one rewrite, once.
        val realigned = CsvFormat.appendAligned(existing, header, row)
        if (realigned != null) {
            store.write(name, realigned)
            return
        }

        // A file the app wrote always ends in a newline, but a hand-edited one might not.
        val separator = if (existing.endsWith("\n")) "" else "\n"
        store.append(name, separator + row + "\n")
    }

    fun appendStrength(set: StrengthSet) = appendRow(
        CsvFormat.strengthFile(YearMonth.from(set.date)),
        CsvFormat.STRENGTH_HEADER,
        CsvFormat.strengthRow(set),
    )

    fun appendCardio(entry: CardioEntry) = appendRow(
        CsvFormat.cardioFile(YearMonth.from(entry.date)),
        CsvFormat.CARDIO_HEADER,
        CsvFormat.cardioRow(entry),
    )

    fun appendFood(entry: FoodEntry) = appendRow(
        CsvFormat.foodFile(YearMonth.from(entry.date)),
        CsvFormat.FOOD_HEADER,
        CsvFormat.foodRow(entry),
    )

    fun appendStress(event: StressEvent) = appendRow(
        CsvFormat.stressFile(YearMonth.from(event.date)),
        CsvFormat.STRESS_HEADER,
        CsvFormat.stressRow(event),
    )

    /**
     * Writes a night, replacing any already recorded for that date.
     *
     * The one rewrite in the logger's normal path: one night per day means an append
     * could silently produce two, and a duplicate night is worse than a larger write.
     */
    fun putSleep(night: SleepNight) {
        val name = CsvFormat.sleepFile(YearMonth.from(night.date))
        val existing = store.read(name)?.let { CsvFormat.parseSleep(it) }.orEmpty()
        store.write(name, CsvFormat.renderSleep(existing.filterNot { it.date == night.date } + night))
    }

    // ----------------------------------------------------------------- removing

    /**
     * Drops one row by id from the month it is in, and reports whether it was there.
     *
     * The month is rewritten rather than appended to, which is unavoidable: taking a line
     * out of the middle of a file is not an append on any platform.
     */
    private fun <T> removeFrom(
        name: String,
        parse: (String) -> List<T>,
        render: (List<T>) -> String,
        keep: (T) -> Boolean,
    ): Boolean {
        val text = store.read(name) ?: return false
        val all = parse(text)
        val kept = all.filter(keep)
        if (kept.size == all.size) return false
        if (kept.isEmpty()) store.delete(name) else store.write(name, render(kept))
        return true
    }

    fun removeStrength(set: StrengthSet): Boolean = removeFrom(
        CsvFormat.strengthFile(YearMonth.from(set.date)),
        { CsvFormat.parseStrength(it) },
        { CsvFormat.renderStrength(it) },
    ) { it.id != set.id }

    fun removeCardio(entry: CardioEntry): Boolean = removeFrom(
        CsvFormat.cardioFile(YearMonth.from(entry.date)),
        { CsvFormat.parseCardio(it) },
        { CsvFormat.renderCardio(it) },
    ) { it.id != entry.id }

    fun removeFood(entry: FoodEntry): Boolean = removeFrom(
        CsvFormat.foodFile(YearMonth.from(entry.date)),
        { CsvFormat.parseFood(it) },
        { CsvFormat.renderFood(it) },
    ) { it.id != entry.id }

    fun removeStress(event: StressEvent): Boolean = removeFrom(
        CsvFormat.stressFile(YearMonth.from(event.date)),
        { CsvFormat.parseStress(it) },
        { CsvFormat.renderStress(it) },
    ) { it.id != event.id }

    fun removeSleep(night: SleepNight): Boolean = removeFrom(
        CsvFormat.sleepFile(YearMonth.from(night.date)),
        { CsvFormat.parseSleep(it) },
        { CsvFormat.renderSleep(it) },
    ) { it.id != night.id }

    // --------------------------------------------------------------------- docs

    /**
     * Keeps the folder's schema README and pandas starter script current.
     *
     * Both are generated reference material, not user content, so they are rewritten when
     * the copy in the folder predates the one shipped with the app -- otherwise adding a
     * column would leave every existing folder documenting a schema that no longer exists.
     *
     * The policy is here; the files themselves are bundled differently on each platform
     * (Android assets, a desktop resource), so the caller supplies the content.
     */
    fun ensureDocs(asset: (String) -> String) {
        for (name in DOCS) {
            val existing = store.read(name)
            if (existing != null && existing.contains(DOCS_MARKER)) continue
            val content = asset(name)
            if (content.isNotEmpty()) store.write(name, content)
        }
    }

    companion object {
        const val DOC_README = "README.md"
        const val DOC_SCRIPT = "analyze.py"

        private val DOCS = listOf(DOC_README, DOC_SCRIPT)

        /** Bumped whenever the generated docs change; see ensureDocs. */
        private const val DOCS_MARKER = "uncomplicated-docs v7"

        fun newId(): String = CsvFormat.newId()
    }
}
