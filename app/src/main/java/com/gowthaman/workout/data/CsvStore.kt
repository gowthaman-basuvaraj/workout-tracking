package com.gowthaman.workout.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Reads and writes the entire data set as plain CSV inside a user-chosen folder.
 *
 * Layout (everything sits directly in the selected folder):
 *   strength-YYYY-MM.csv   one row per set
 *   cardio-YYYY-MM.csv     one row per cardio session
 *   exercises.csv          the auto-populated exercise catalog
 *   README.md, analyze.py  written once, to document the schema for desktop analysis
 *
 * Month-partitioned files are what make the folder safe to put under Syncthing/Dropbox:
 * logging today only ever rewrites the current month, and there is no database or lock
 * file for a sync tool to corrupt.
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
            // Sync tools drop "...sync-conflict..." copies next to the original; ignoring
            // them keeps a conflicted folder from double-counting every set.
            if (name.contains("conflict", ignoreCase = true)) continue
            when {
                name.startsWith(STRENGTH_PREFIX) && name.endsWith(EXT) ->
                    strength += parseStrength(readText(file.uri))
                name.startsWith(CARDIO_PREFIX) && name.endsWith(EXT) ->
                    cardio += parseCardio(readText(file.uri))
                // Checked before the "food-" prefix: "foods.csv" is the catalog, and
                // "food-2026-11.csv" is a month of entries.
                name == FOOD_CATALOG_FILE ->
                    foodCatalog = parseFoodCatalog(readText(file.uri))
                name.startsWith(FOOD_PREFIX) && name.endsWith(EXT) ->
                    food += parseFood(readText(file.uri))
                name == CATALOG_FILE ->
                    catalog = parseCatalog(readText(file.uri))
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

    private fun parseStrength(text: String): List<StrengthSet> = parseCsv(text).mapNotNull { r ->
        val date = r.str("date").toLocalDateOrNull() ?: return@mapNotNull null
        val exercise = r.str("exercise").trim()
        if (exercise.isEmpty()) return@mapNotNull null
        StrengthSet(
            id = r.str("id").ifBlank { newId() },
            date = date,
            exercise = exercise,
            setNo = r.int("set_no", 1),
            reps = r.int("reps"),
            weight = r.double("weight"),
            unit = r.str("unit", Units.KG).ifBlank { Units.KG },
            bodyweight = r.bool("bodyweight"),
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    private fun parseCardio(text: String): List<CardioEntry> = parseCsv(text).mapNotNull { r ->
        val date = r.str("date").toLocalDateOrNull() ?: return@mapNotNull null
        val activity = r.str("activity").trim()
        if (activity.isEmpty()) return@mapNotNull null
        CardioEntry(
            id = r.str("id").ifBlank { newId() },
            date = date,
            activity = activity,
            durationSec = r.int("duration_sec"),
            distance = r.double("distance"),
            distUnit = r.str("dist_unit", Units.KM).ifBlank { Units.KM },
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    private fun parseFood(text: String): List<FoodEntry> = parseCsv(text).mapNotNull { r ->
        val date = r.str("date").toLocalDateOrNull() ?: return@mapNotNull null
        val item = r.str("item").trim()
        if (item.isEmpty()) return@mapNotNull null
        FoodEntry(
            id = r.str("id").ifBlank { newId() },
            date = date,
            item = item,
            purpose = Purpose.fromName(r.str("purpose")),
            scale = FoodScale.parse(r.str("scale")),
            qty = r.int("qty", 1).coerceAtLeast(1),
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    private fun parseFoodCatalog(text: String): List<FoodDef> = parseCsv(text).mapNotNull { r ->
        val name = r.str("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        FoodDef(
            name = name,
            purpose = Purpose.fromName(r.str("purpose")),
            scale = FoodScale.parse(r.str("scale")),
            createdAt = r.long("created_at"),
            lastUsed = r.str("last_used").toLocalDateOrNull(),
            useCount = r.int("uses"),
        )
    }

    private fun parseCatalog(text: String): List<ExerciseDef> = parseCsv(text).mapNotNull { r ->
        val name = r.str("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        ExerciseDef(
            name = name,
            kind = if (r.str("type").equals(Kind.CARDIO.name, ignoreCase = true)) Kind.CARDIO
            else Kind.STRENGTH,
            createdAt = r.long("created_at"),
            lastUsed = r.str("last_used").toLocalDateOrNull(),
            useCount = r.int("uses"),
        )
    }

    // ------------------------------------------------------------------ writing

    fun saveStrengthMonth(treeUri: Uri, month: YearMonth, rows: List<StrengthSet>) {
        val name = STRENGTH_PREFIX + month.fileSuffix() + EXT
        if (rows.isEmpty()) {
            deleteIfPresent(treeUri, name)
            return
        }
        val body = buildString {
            append(Csv.row(STRENGTH_HEADER)).append('\n')
            rows.sortedWith(compareBy({ it.date }, { it.exercise }, { it.setNo })).forEach { s ->
                append(
                    Csv.row(
                        s.id,
                        s.date.toString(),
                        s.exercise,
                        s.setNo.toString(),
                        s.reps.toString(),
                        s.weight.fmt(2),
                        s.unit,
                        s.bodyweight.toString(),
                        Csv.clean(s.notes),
                        s.loggedAt.toString(),
                    )
                ).append('\n')
            }
        }
        writeText(treeUri, name, MIME_CSV, body)
    }

    fun saveCardioMonth(treeUri: Uri, month: YearMonth, rows: List<CardioEntry>) {
        val name = CARDIO_PREFIX + month.fileSuffix() + EXT
        if (rows.isEmpty()) {
            deleteIfPresent(treeUri, name)
            return
        }
        val body = buildString {
            append(Csv.row(CARDIO_HEADER)).append('\n')
            rows.sortedWith(compareBy({ it.date }, { it.activity })).forEach { c ->
                append(
                    Csv.row(
                        c.id,
                        c.date.toString(),
                        c.activity,
                        c.durationSec.toString(),
                        c.distance.fmt(3),
                        c.distUnit,
                        Csv.clean(c.notes),
                        c.loggedAt.toString(),
                    )
                ).append('\n')
            }
        }
        writeText(treeUri, name, MIME_CSV, body)
    }

    fun saveCatalog(treeUri: Uri, defs: List<ExerciseDef>) {
        val body = buildString {
            append(Csv.row(CATALOG_HEADER)).append('\n')
            defs.sortedBy { catalogKey(it.name) }.forEach { d ->
                append(
                    Csv.row(
                        d.name,
                        d.kind.name,
                        d.createdAt.toString(),
                        d.lastUsed?.toString().orEmpty(),
                        d.useCount.toString(),
                    )
                ).append('\n')
            }
        }
        writeText(treeUri, CATALOG_FILE, MIME_CSV, body)
    }

    fun saveFoodMonth(treeUri: Uri, month: YearMonth, rows: List<FoodEntry>) {
        val name = FOOD_PREFIX + month.fileSuffix() + EXT
        if (rows.isEmpty()) {
            deleteIfPresent(treeUri, name)
            return
        }
        val body = buildString {
            append(Csv.row(FOOD_HEADER)).append('\n')
            rows.sortedWith(compareBy({ it.date }, { it.loggedAt })).forEach { f ->
                append(
                    Csv.row(
                        f.id,
                        f.date.toString(),
                        f.item,
                        f.purpose.name,
                        f.scale.level.toString(),
                        f.qty.toString(),
                        Csv.clean(f.notes),
                        f.loggedAt.toString(),
                    )
                ).append('\n')
            }
        }
        writeText(treeUri, name, MIME_CSV, body)
    }

    fun saveFoodCatalog(treeUri: Uri, defs: List<FoodDef>) {
        val body = buildString {
            append(Csv.row(FOOD_CATALOG_HEADER)).append('\n')
            defs.sortedBy { catalogKey(it.name) }.forEach { d ->
                append(
                    Csv.row(
                        d.name,
                        d.purpose.name,
                        d.scale.level.toString(),
                        d.createdAt.toString(),
                        d.lastUsed?.toString().orEmpty(),
                        d.useCount.toString(),
                    )
                ).append('\n')
            }
        }
        writeText(treeUri, FOOD_CATALOG_FILE, MIME_CSV, body)
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
     * Looks a file up by its base name.
     *
     * Document providers may append an extension of their own on create — ask for
     * `analyze.py` with a mime type they map to `.txt` and you get `analyze.py.txt` back.
     * Matching on the stem means the next run finds that file instead of creating a
     * second one beside it on every single resume.
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
        const val STRENGTH_PREFIX = "strength-"
        const val CARDIO_PREFIX = "cardio-"
        const val CATALOG_FILE = "exercises.csv"
        const val FOOD_PREFIX = "food-"
        const val FOOD_CATALOG_FILE = "foods.csv"

        private const val EXT = ".csv"
        private const val MIME_CSV = "text/csv"
        private const val MIME_MD = "text/markdown"
        private const val MIME_PYTHON = "text/x-python"
        private const val DOC_README = "README.md"
        private const val DOC_SCRIPT = "analyze.py"
        /** Bumped whenever the generated docs change; see refreshDoc. */
        private const val DOCS_MARKER = "workout-log-docs v2"

        private val STRENGTH_HEADER = listOf(
            "id", "date", "exercise", "set_no", "reps",
            "weight", "unit", "bodyweight", "notes", "logged_at",
        )
        private val CARDIO_HEADER = listOf(
            "id", "date", "activity", "duration_sec", "distance", "dist_unit", "notes", "logged_at",
        )
        private val CATALOG_HEADER = listOf("name", "type", "created_at", "last_used", "uses")
        private val FOOD_HEADER = listOf(
            "id", "date", "item", "purpose", "scale", "qty", "notes", "logged_at",
        )
        private val FOOD_CATALOG_HEADER = listOf(
            "name", "purpose", "scale", "created_at", "last_used", "uses",
        )

        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

        fun YearMonth.fileSuffix(): String = String.format(Locale.US, "%04d-%02d", year, monthValue)

        fun String.toLocalDateOrNull(): LocalDate? =
            runCatching { LocalDate.parse(trim(), ISO) }.getOrNull()
    }
}
