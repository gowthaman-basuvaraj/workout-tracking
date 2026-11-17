package com.gowthaman.workout.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.YearMonth

/**
 * Reads and writes the data set through the Storage Access Framework.
 *
 * This class is only I/O: which document to open, and how to read or truncate it. What a
 * row looks like lives in [CsvFormat] in :core, shared with the desktop CLI so the two
 * can never disagree about the file format.
 *
 * Month-partitioned files are what make the folder safe under Syncthing/Dropbox: logging
 * today only rewrites the current month, and there is no database or lock file.
 */
class CsvStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    data class Snapshot(
        val strength: List<StrengthSet> = emptyList(),
        val cardio: List<CardioEntry> = emptyList(),
        val catalog: List<ExerciseDef> = emptyList(),
        val food: List<FoodEntry> = emptyList(),
        val foodCatalog: List<FoodDef> = emptyList(),
    )

    // ------------------------------------------------------------------ reading

    fun load(treeUri: Uri): Snapshot {
        val root = requireRoot(treeUri)

        val strength = ArrayList<StrengthSet>()
        val cardio = ArrayList<CardioEntry>()
        val food = ArrayList<FoodEntry>()
        var catalog: List<ExerciseDef> = emptyList()
        var foodCatalog: List<FoodDef> = emptyList()

        for (file in root.listFiles()) {
            if (!file.isFile) continue
            val name = file.name ?: continue
            if (CsvFormat.isConflictCopy(name)) continue
            when {
                name == CsvFormat.FOOD_CATALOG_FILE ->
                    foodCatalog = CsvFormat.parseFoodCatalog(readText(file.uri))
                name == CsvFormat.CATALOG_FILE ->
                    catalog = CsvFormat.parseCatalog(readText(file.uri))
                !name.endsWith(CsvFormat.EXT) -> Unit
                name.startsWith(CsvFormat.STRENGTH_PREFIX) ->
                    strength += CsvFormat.parseStrength(readText(file.uri))
                name.startsWith(CsvFormat.CARDIO_PREFIX) ->
                    cardio += CsvFormat.parseCardio(readText(file.uri))
                name.startsWith(CsvFormat.FOOD_PREFIX) ->
                    food += CsvFormat.parseFood(readText(file.uri))
            }
        }

        return Snapshot(
            strength = strength.sortedWith(compareBy({ it.date }, { it.exercise }, { it.setNo })),
            cardio = cardio.sortedWith(compareBy({ it.date }, { it.activity })),
            catalog = catalog,
            food = food.sortedWith(compareBy({ it.date }, { it.loggedAt })),
            foodCatalog = foodCatalog,
        )
    }

    private fun readText(uri: Uri): String =
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull().orEmpty()

    // ------------------------------------------------------------------ writing

    fun saveStrengthMonth(treeUri: Uri, month: YearMonth, rows: List<StrengthSet>) =
        writeOrDelete(treeUri, CsvFormat.strengthFile(month), rows) { CsvFormat.renderStrength(it) }

    fun saveCardioMonth(treeUri: Uri, month: YearMonth, rows: List<CardioEntry>) =
        writeOrDelete(treeUri, CsvFormat.cardioFile(month), rows) { CsvFormat.renderCardio(it) }

    fun saveFoodMonth(treeUri: Uri, month: YearMonth, rows: List<FoodEntry>) =
        writeOrDelete(treeUri, CsvFormat.foodFile(month), rows) { CsvFormat.renderFood(it) }

    fun saveCatalog(treeUri: Uri, defs: List<ExerciseDef>) =
        writeText(treeUri, CsvFormat.CATALOG_FILE, MIME_CSV, CsvFormat.renderCatalog(defs))

    fun saveFoodCatalog(treeUri: Uri, defs: List<FoodDef>) =
        writeText(treeUri, CsvFormat.FOOD_CATALOG_FILE, MIME_CSV, CsvFormat.renderFoodCatalog(defs))

    /**
     * Publishes the settings into the folder for the desktop CLI to read.
     *
     * Strictly one-way: the app never reads this file back, so it is always the phone's
     * values that win and there is nothing to merge.
     */
    fun saveSettings(treeUri: Uri, settings: Settings) =
        writeText(treeUri, CsvFormat.SETTINGS_FILE, MIME_CSV, CsvFormat.renderSettings(settings))

    /** An emptied month leaves no file behind rather than a lone header. */
    private fun <T> writeOrDelete(
        treeUri: Uri,
        name: String,
        rows: List<T>,
        render: (List<T>) -> String,
    ) {
        if (rows.isEmpty()) deleteIfPresent(treeUri, name)
        else writeText(treeUri, name, MIME_CSV, render(rows))
    }

    /**
     * Keeps the folder's schema README and pandas starter script current.
     *
     * Both are generated reference material, not user content, so they are rewritten when
     * the copy in the folder predates the one shipped with the app — otherwise adding a
     * column would leave every existing folder documenting a schema that no longer exists.
     */
    fun ensureDocs(treeUri: Uri) {
        val root = requireRoot(treeUri)
        refreshDoc(treeUri, root, DOC_README, MIME_MD)
        refreshDoc(treeUri, root, DOC_SCRIPT, MIME_PYTHON)
    }

    private fun refreshDoc(treeUri: Uri, root: DocumentFile, name: String, mime: String) {
        val existing = findByName(root, name)
        if (existing != null && readText(existing.uri).contains(DOCS_MARKER)) return
        val content = asset(name)
        if (content.isNotEmpty()) writeText(treeUri, name, mime, content)
    }

    private fun asset(name: String): String =
        runCatching {
            appContext.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrDefault("")

    // ----------------------------------------------------------------- plumbing

    private fun requireRoot(treeUri: Uri): DocumentFile =
        DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("Data folder is not reachable. Re-select it in Settings.")

    /**
     * Looks a file up by its base name, tolerating providers that append an extension of
     * their own on create (ask for analyze.py, get analyze.py.txt), so the next run finds
     * that file instead of creating a second one beside it.
     */
    private fun findByName(root: DocumentFile, name: String): DocumentFile? {
        val stem = name.substringBeforeLast('.')
        return root.listFiles().firstOrNull { f ->
            if (!f.isFile) return@firstOrNull false
            val n = f.name ?: return@firstOrNull false
            n == name || n.startsWith("$stem.")
        }
    }

    private fun writeText(treeUri: Uri, name: String, mime: String, text: String) {
        val root = requireRoot(treeUri)
        val file = findByName(root, name)
            ?: root.createFile(mime, name)
            ?: error("Could not create $name in the data folder.")
        // "wt" truncates first, so a shorter file never keeps a tail from the old one.
        val stream = resolver.openOutputStream(file.uri, "wt")
            ?: error("Could not write $name in the data folder.")
        stream.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    private fun deleteIfPresent(treeUri: Uri, name: String) {
        findByName(requireRoot(treeUri), name)?.delete()
    }

    companion object {
        private const val MIME_CSV = "text/csv"
        private const val MIME_MD = "text/markdown"
        private const val MIME_PYTHON = "text/x-python"
        private const val DOC_README = "README.md"
        private const val DOC_SCRIPT = "analyze.py"

        /** Bumped whenever the generated docs change; see refreshDoc. */
        private const val DOCS_MARKER = "jotter-docs v4"

        fun newId(): String = CsvFormat.newId()
    }
}
