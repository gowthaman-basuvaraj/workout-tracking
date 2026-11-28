package com.gowthaman.workout.data

import kotlinx.datetime.LocalDate

/**
 * The on-disk format: file names, headers, and how a row becomes text and back.
 *
 * Deliberately free of any I/O. The phone reaches the folder through the Storage Access
 * Framework and the desktop CLI through java.io.File, but both go through here, so there
 * is exactly one definition of what an Uncomplicated CSV looks like.
 */
object CsvFormat {

    const val STRENGTH_PREFIX = "strength-"
    const val CARDIO_PREFIX = "cardio-"
    const val FOOD_PREFIX = "food-"
    const val CATALOG_FILE = "exercises.csv"
    const val FOOD_CATALOG_FILE = "foods.csv"
    const val SETTINGS_FILE = "settings.csv"
    const val SLEEP_PREFIX = "sleep-"
    const val STRESS_PREFIX = "stress-"
    const val STRESS_CATALOG_FILE = "stress.csv"
    const val EXT = ".csv"

    val STRENGTH_HEADER = listOf(
        "id", "date", "exercise", "set_no", "reps",
        "weight", "unit", "bodyweight", "notes", "logged_at",
    )
    val CARDIO_HEADER = listOf(
        "id", "date", "activity", "duration_sec", "distance", "dist_unit", "notes", "logged_at",
    )
    val FOOD_HEADER = listOf(
        "id", "date", "item", "purpose", "scale", "tags", "qty", "notes", "logged_at",
    )
    val CATALOG_HEADER = listOf("name", "type", "created_at", "last_used", "uses")
    val FOOD_CATALOG_HEADER =
        listOf("name", "purpose", "scale", "tags", "created_at", "last_used", "uses")
    val SETTINGS_HEADER = listOf("key", "value")
    val SLEEP_HEADER = listOf("id", "date", "duration_min", "quality", "notes", "logged_at")
    val STRESS_HEADER = listOf(
        "id", "date", "label", "direction", "weight", "notes", "logged_at",
    )
    val STRESS_CATALOG_HEADER = listOf(
        "name", "direction", "weight", "created_at", "last_used", "uses",
    )

    /**
     * Carried as the first row of settings.csv so the warning is visible the moment the
     * file is opened, rather than only in the folder README.
     */
    const val SETTINGS_NOTE =
        "Written by the Uncomplicated app. Read-only here: the app overwrites this " +
            "file whenever a setting changes, so edits made on a computer will be lost."

    fun newId(): String = newRowId()

    fun monthSuffix(month: YearMonth): String = "${month.year.pad4()}-${month.month.pad2()}"

    fun strengthFile(month: YearMonth): String = STRENGTH_PREFIX + monthSuffix(month) + EXT
    fun cardioFile(month: YearMonth): String = CARDIO_PREFIX + monthSuffix(month) + EXT
    fun foodFile(month: YearMonth): String = FOOD_PREFIX + monthSuffix(month) + EXT
    fun sleepFile(month: YearMonth): String = SLEEP_PREFIX + monthSuffix(month) + EXT
    fun stressFile(month: YearMonth): String = STRESS_PREFIX + monthSuffix(month) + EXT

    fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    /**
     * Sync tools leave "...sync-conflict..." copies beside the original; counting them
     * would double every total, so they are skipped everywhere a folder is read.
     */
    fun isConflictCopy(name: String): Boolean = name.contains("conflict", ignoreCase = true)

    // ------------------------------------------------------------------- reading

    fun parseStrength(text: String): List<StrengthSet> = parseCsv(text).mapNotNull { r ->
        val date = parseDate(r.str("date")) ?: return@mapNotNull null
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

    fun parseCardio(text: String): List<CardioEntry> = parseCsv(text).mapNotNull { r ->
        val date = parseDate(r.str("date")) ?: return@mapNotNull null
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

    fun parseFood(text: String): List<FoodEntry> = parseCsv(text).mapNotNull { r ->
        val date = parseDate(r.str("date")) ?: return@mapNotNull null
        val item = r.str("item").trim()
        if (item.isEmpty()) return@mapNotNull null
        FoodEntry(
            id = r.str("id").ifBlank { newId() },
            date = date,
            item = item,
            purpose = Purpose.fromName(r.str("purpose")),
            scale = FoodScale.parse(r.str("scale")),
            // Absent in files written before tags existed, which reads as "untagged".
            tags = FoodTag.parse(r.str("tags")),
            qty = r.int("qty", 1).coerceAtLeast(1),
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    fun parseSleep(text: String): List<SleepNight> = parseCsv(text).mapNotNull { r ->
        val date = parseDate(r.str("date")) ?: return@mapNotNull null
        val minutes = r.int("duration_min")
        if (minutes <= 0) return@mapNotNull null
        SleepNight(
            id = r.str("id").ifBlank { newId() },
            date = date,
            durationMin = minutes,
            quality = SleepQuality.parse(r.str("quality")),
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    fun parseStress(text: String): List<StressEvent> = parseCsv(text).mapNotNull { r ->
        val date = parseDate(r.str("date")) ?: return@mapNotNull null
        val label = r.str("label").trim()
        if (label.isEmpty()) return@mapNotNull null
        StressEvent(
            id = r.str("id").ifBlank { newId() },
            date = date,
            label = label,
            direction = StressDirection.fromName(r.str("direction")),
            weight = StressWeight.parse(r.str("weight")),
            notes = r.str("notes"),
            loggedAt = r.long("logged_at"),
        )
    }

    fun parseStressCatalog(text: String): List<StressDef> = parseCsv(text).mapNotNull { r ->
        val name = r.str("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        StressDef(
            name = name,
            direction = StressDirection.fromName(r.str("direction")),
            weight = StressWeight.parse(r.str("weight")),
            createdAt = r.long("created_at"),
            lastUsed = parseDate(r.str("last_used")),
            useCount = r.int("uses"),
        )
    }

    fun parseCatalog(text: String): List<ExerciseDef> = parseCsv(text).mapNotNull { r ->
        val name = r.str("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        ExerciseDef(
            name = name,
            kind = if (r.str("type").equals(Kind.CARDIO.name, ignoreCase = true)) Kind.CARDIO
            else Kind.STRENGTH,
            createdAt = r.long("created_at"),
            lastUsed = parseDate(r.str("last_used")),
            useCount = r.int("uses"),
        )
    }

    fun parseFoodCatalog(text: String): List<FoodDef> = parseCsv(text).mapNotNull { r ->
        val name = r.str("name").trim()
        if (name.isEmpty()) return@mapNotNull null
        FoodDef(
            name = name,
            purpose = Purpose.fromName(r.str("purpose")),
            scale = FoodScale.parse(r.str("scale")),
            tags = FoodTag.parse(r.str("tags")),
            createdAt = r.long("created_at"),
            lastUsed = parseDate(r.str("last_used")),
            useCount = r.int("uses"),
        )
    }

    /**
     * The app's settings, as the desktop sees them.
     *
     * One-way on purpose. The phone owns these values, and this is a copy so the CLI can
     * agree with the app about units and the overload window instead of guessing. Nothing
     * reads it back on the phone, so there is no edit to conflict over.
     *
     * [Settings.folderUri] is left out: it is a device-specific content:// uri and means
     * nothing on a laptop.
     */
    fun renderSettings(settings: Settings): String = buildString {
        appendLine(Csv.row(SETTINGS_HEADER))
        appendLine(Csv.row("_note", SETTINGS_NOTE))
        listOf(
            "overload_period_days" to settings.overloadPeriodDays.toString(),
            "weight_unit" to settings.weightUnit,
            "distance_unit" to settings.distanceUnit,
            "weight_increment" to settings.weightIncrement.fmt(2),
            "rep_min" to settings.repMin.toString(),
            "rep_max" to settings.repMax.toString(),
            "bodyweight_rep_ceiling" to settings.bodyweightRepCeiling.toString(),
            "user_bodyweight_kg" to settings.userBodyweightKg.fmt(2),
            "dashboard_period" to settings.dashboardPeriod.name,
        ).forEach { (key, value) -> appendLine(Csv.row(key, value)) }
    }

    /** Missing or unreadable values fall back to the defaults rather than failing. */
    fun parseSettings(text: String, base: Settings = Settings()): Settings {
        val values = parseCsv(text).associate { it.str("key").trim() to it.str("value").trim() }
        fun int(key: String, fallback: Int) = values[key]?.toIntOrNull() ?: fallback
        fun num(key: String, fallback: Double) =
            values[key]?.replace(',', '.')?.toDoubleOrNull() ?: fallback
        fun unit(key: String, fallback: String, allowed: Set<String>) =
            values[key]?.takeIf { it in allowed } ?: fallback

        return base.copy(
            overloadPeriodDays = int("overload_period_days", base.overloadPeriodDays),
            weightUnit = unit("weight_unit", base.weightUnit, setOf(Units.KG, Units.LB)),
            distanceUnit = unit("distance_unit", base.distanceUnit, setOf(Units.KM, Units.MI)),
            weightIncrement = num("weight_increment", base.weightIncrement),
            repMin = int("rep_min", base.repMin),
            repMax = int("rep_max", base.repMax),
            bodyweightRepCeiling = int("bodyweight_rep_ceiling", base.bodyweightRepCeiling),
            userBodyweightKg = num("user_bodyweight_kg", base.userBodyweightKg),
            dashboardPeriod = Period.fromName(values["dashboard_period"]),
        )
    }

    // ------------------------------------------------------------------- writing

    fun renderStrength(rows: List<StrengthSet>): String = buildString {
        append(Csv.row(STRENGTH_HEADER)).append('\n')
        rows.sortedWith(compareBy({ it.date }, { it.exercise }, { it.setNo })).forEach { s ->
            append(strengthRow(s)).append('\n')
        }
    }

    fun renderCardio(rows: List<CardioEntry>): String = buildString {
        append(Csv.row(CARDIO_HEADER)).append('\n')
        rows.sortedWith(compareBy({ it.date }, { it.activity })).forEach { c ->
            append(cardioRow(c)).append('\n')
        }
    }

    fun renderFood(rows: List<FoodEntry>): String = buildString {
        append(Csv.row(FOOD_HEADER)).append('\n')
        rows.sortedWith(compareBy({ it.date }, { it.loggedAt })).forEach { f ->
            append(foodRow(f)).append('\n')
        }
    }

    fun renderCatalog(defs: List<ExerciseDef>): String = buildString {
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

    fun renderFoodCatalog(defs: List<FoodDef>): String = buildString {
        append(Csv.row(FOOD_CATALOG_HEADER)).append('\n')
        defs.sortedBy { catalogKey(it.name) }.forEach { d ->
            append(
                Csv.row(
                    d.name,
                    d.purpose.name,
                    d.scale.level.toString(),
                    FoodTag.render(d.tags),
                    d.createdAt.toString(),
                    d.lastUsed?.toString().orEmpty(),
                    d.useCount.toString(),
                )
            ).append('\n')
        }
    }

    fun renderSleep(rows: List<SleepNight>): String = buildString {
        appendLine(Csv.row(SLEEP_HEADER))
        rows.sortedBy { it.date }.forEach { appendLine(sleepRow(it)) }
    }

    fun renderStress(rows: List<StressEvent>): String = buildString {
        appendLine(Csv.row(STRESS_HEADER))
        rows.sortedWith(compareBy({ it.date }, { it.loggedAt })).forEach { appendLine(stressRow(it)) }
    }

    fun renderStressCatalog(defs: List<StressDef>): String = buildString {
        appendLine(Csv.row(STRESS_CATALOG_HEADER))
        defs.sortedBy { catalogKey(it.name) }.forEach { d ->
            appendLine(
                Csv.row(
                    d.name,
                    d.direction.name,
                    d.weight.level.toString(),
                    d.createdAt.toString(),
                    d.lastUsed?.toString().orEmpty(),
                    d.useCount.toString(),
                )
            )
        }
    }

    /** A single record, with no header — what the CLI appends to an existing file. */
    fun strengthRow(s: StrengthSet): String = Csv.row(
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

    fun cardioRow(c: CardioEntry): String = Csv.row(
        c.id,
        c.date.toString(),
        c.activity,
        c.durationSec.toString(),
        c.distance.fmt(3),
        c.distUnit,
        Csv.clean(c.notes),
        c.loggedAt.toString(),
    )

    fun sleepRow(n: SleepNight): String = Csv.row(
        n.id,
        n.date.toString(),
        n.durationMin.toString(),
        n.quality.level.toString(),
        Csv.clean(n.notes),
        n.loggedAt.toString(),
    )

    fun stressRow(e: StressEvent): String = Csv.row(
        e.id,
        e.date.toString(),
        e.label,
        e.direction.name,
        e.weight.level.toString(),
        Csv.clean(e.notes),
        e.loggedAt.toString(),
    )

    /**
     * Fits one new row into a file whose header may predate a column.
     *
     * The CLI appends rather than rewrites, so a row written against today's header and
     * appended to a file written against an older one would shift every value past the
     * difference: a tag landing in the `qty` column and a quantity in the notes. Columns
     * are addressed by name on the way in, so nothing would complain -- the row would
     * just be quietly wrong.
     *
     * Returns null when the file already agrees and a plain append is safe. Otherwise
     * returns the whole file rewritten under a header holding both sets of columns, with
     * [row] appended to it. Columns the file has and [header] does not are kept rather
     * than dropped, so a column added by a later version of the app survives a log from
     * an older CLI.
     */
    fun appendAligned(existing: String, header: List<String>, row: String): String? {
        val lines = Csv.lines(existing)
        if (lines.isEmpty()) return null
        val fileHeader = Csv.parse(lines.first())
        if (fileHeader == header) return null

        val merged = header + fileHeader.filterNot { it in header }
        val body = lines.drop(1).map { Csv.row(align(Csv.parse(it), fileHeader, merged)) }
        val fitted = Csv.row(align(Csv.parse(row), header, merged))
        return (listOf(Csv.row(merged)) + body + fitted).joinToString("\n", postfix = "\n")
    }

    /** One record moved from [from]'s column order into [to]'s, blank-filling the rest. */
    private fun align(values: List<String>, from: List<String>, to: List<String>): List<String> =
        to.map { column ->
            val i = from.indexOf(column)
            if (i >= 0 && i < values.size) values[i] else ""
        }

    fun foodRow(f: FoodEntry): String = Csv.row(
        f.id,
        f.date.toString(),
        f.item,
        f.purpose.name,
        f.scale.level.toString(),
        FoodTag.render(f.tags),
        f.qty.toString(),
        Csv.clean(f.notes),
        f.loggedAt.toString(),
    )
}
