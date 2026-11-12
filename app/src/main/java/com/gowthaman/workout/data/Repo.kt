package com.gowthaman.workout.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Single source of truth for logged data.
 *
 * The whole data set is held in memory (a few years of training is well under a megabyte)
 * and written back to CSV after every change. Reads are therefore instant and the files on
 * disk are always current, which is what lets a sync client pick changes up immediately.
 */
class Repo(context: Context, private val prefs: Prefs) {

    enum class Status { NO_FOLDER, LOADING, READY, ERROR }

    private val appContext = context.applicationContext
    private val store = CsvStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _status = MutableStateFlow(Status.NO_FOLDER)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _strength = MutableStateFlow<List<StrengthSet>>(emptyList())
    val strength: StateFlow<List<StrengthSet>> = _strength.asStateFlow()

    private val _cardio = MutableStateFlow<List<CardioEntry>>(emptyList())
    val cardio: StateFlow<List<CardioEntry>> = _cardio.asStateFlow()

    private val _catalog = MutableStateFlow<List<ExerciseDef>>(emptyList())
    val catalog: StateFlow<List<ExerciseDef>> = _catalog.asStateFlow()

    /** Catalog entries that came from file but have no logged data yet. */
    private var storedOnly: List<ExerciseDef> = emptyList()

    /** The folder's README and analysis script only need writing once per folder. */
    private var docsEnsured = false

    private val folderUri: Uri? get() = prefs.current.folderUri?.let(Uri::parse)

    init {
        if (folderUri != null) reload() else _status.value = Status.NO_FOLDER
    }

    // -------------------------------------------------------------------- folder

    /** Persists the chosen SAF tree so it survives reboots, then loads from it. */
    fun useFolder(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.update { it.copy(folderUri = uri.toString()) }
        docsEnsured = false
        reload()
    }

    fun forgetFolder() {
        prefs.update { it.copy(folderUri = null) }
        docsEnsured = false
        _strength.value = emptyList()
        _cardio.value = emptyList()
        _catalog.value = emptyList()
        storedOnly = emptyList()
        _status.value = Status.NO_FOLDER
    }

    fun reload() {
        val uri = folderUri
        if (uri == null) {
            _status.value = Status.NO_FOLDER
            return
        }
        scope.launch {
            // Shares the write lock: a load must never interleave with a write, and two
            // loads must never race each other (startup and the onResume refresh overlap,
            // and both would otherwise try to create the folder's README side by side).
            writeLock.withLock {
                _status.value = Status.LOADING
                try {
                    val snapshot = withContext(Dispatchers.IO) {
                        if (!docsEnsured) {
                            store.ensureDocs(uri)
                            docsEnsured = true
                        }
                        store.load(uri)
                    }
                    _strength.value = snapshot.strength
                    _cardio.value = snapshot.cardio
                    storedOnly = snapshot.catalog
                    refreshCatalog()
                    _error.value = null
                    _status.value = Status.READY
                } catch (t: Throwable) {
                    _error.value = t.message ?: "Could not read the data folder."
                    _status.value = Status.ERROR
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // --------------------------------------------------------------- strength

    /** Appends one set, numbered after whatever is already logged for that exercise that day. */
    fun addStrengthSet(
        date: LocalDate,
        exercise: String,
        reps: Int,
        weight: Double,
        bodyweight: Boolean,
        notes: String = "",
    ) = mutate {
        val name = exercise.trim()
        if (name.isEmpty()) return@mutate emptySet()
        val key = catalogKey(name)
        val nextSetNo = _strength.value
            .count { it.date == date && catalogKey(it.exercise) == key } + 1
        val row = StrengthSet(
            id = CsvStore.newId(),
            date = date,
            exercise = canonicalName(name, Kind.STRENGTH),
            setNo = nextSetNo,
            reps = reps.coerceAtLeast(0),
            weight = weight.coerceAtLeast(0.0),
            unit = prefs.current.weightUnit,
            bodyweight = bodyweight,
            notes = Csv.clean(notes),
            loggedAt = System.currentTimeMillis(),
        )
        _strength.value = _strength.value + row
        setOf(YearMonth.from(date))
    }

    fun updateStrengthSet(updated: StrengthSet) = mutate {
        val old = _strength.value.firstOrNull { it.id == updated.id } ?: return@mutate emptySet()
        _strength.value = _strength.value.map { if (it.id == updated.id) updated else it }
        setOf(YearMonth.from(old.date), YearMonth.from(updated.date))
    }

    fun deleteStrengthSet(id: String) = mutate {
        val row = _strength.value.firstOrNull { it.id == id } ?: return@mutate emptySet()
        _strength.value = _strength.value.filter { it.id != id }
        renumber(row.date, row.exercise)
        setOf(YearMonth.from(row.date))
    }

    /** Removes every set of one exercise on one day. */
    fun deleteStrengthGroup(date: LocalDate, exercise: String) = mutate {
        val key = catalogKey(exercise)
        val gone = _strength.value.filter { it.date == date && catalogKey(it.exercise) == key }
        if (gone.isEmpty()) return@mutate emptySet()
        _strength.value = _strength.value - gone.toSet()
        setOf(YearMonth.from(date))
    }

    /** Keeps set_no contiguous after a delete, so the CSV stays tidy. */
    private fun renumber(date: LocalDate, exercise: String) {
        val key = catalogKey(exercise)
        val affected = _strength.value
            .filter { it.date == date && catalogKey(it.exercise) == key }
            .sortedBy { it.setNo }
        if (affected.isEmpty()) return
        val fixed = affected.mapIndexed { i, s -> s.copy(setNo = i + 1) }.associateBy { it.id }
        _strength.value = _strength.value.map { fixed[it.id] ?: it }
    }

    // ----------------------------------------------------------------- cardio

    fun addCardio(
        date: LocalDate,
        activity: String,
        durationSec: Int,
        distance: Double,
        notes: String = "",
    ) = mutate {
        val name = activity.trim()
        if (name.isEmpty()) return@mutate emptySet()
        val row = CardioEntry(
            id = CsvStore.newId(),
            date = date,
            activity = canonicalName(name, Kind.CARDIO),
            durationSec = durationSec.coerceAtLeast(0),
            distance = distance.coerceAtLeast(0.0),
            distUnit = prefs.current.distanceUnit,
            notes = Csv.clean(notes),
            loggedAt = System.currentTimeMillis(),
        )
        _cardio.value = _cardio.value + row
        setOf(YearMonth.from(date))
    }

    fun updateCardio(updated: CardioEntry) = mutate {
        val old = _cardio.value.firstOrNull { it.id == updated.id } ?: return@mutate emptySet()
        _cardio.value = _cardio.value.map { if (it.id == updated.id) updated else it }
        setOf(YearMonth.from(old.date), YearMonth.from(updated.date))
    }

    fun deleteCardio(id: String) = mutate {
        val row = _cardio.value.firstOrNull { it.id == id } ?: return@mutate emptySet()
        _cardio.value = _cardio.value.filter { it.id != id }
        setOf(YearMonth.from(row.date))
    }

    // ---------------------------------------------------------------- catalog

    /** Adds a name to the picker without logging anything against it yet. */
    fun addCatalogEntry(name: String, kind: Kind) = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate emptySet()
        val key = catalogKey(trimmed)
        if (_catalog.value.any { catalogKey(it.name) == key }) return@mutate emptySet()
        storedOnly = storedOnly + ExerciseDef(
            name = trimmed,
            kind = kind,
            createdAt = System.currentTimeMillis(),
        )
        emptySet()
    }

    /** Renames an exercise everywhere, including in past months. */
    fun renameExercise(oldName: String, newName: String) = mutate {
        val from = catalogKey(oldName)
        val to = newName.trim()
        if (to.isEmpty() || from == catalogKey(to)) return@mutate emptySet()

        val touched = mutableSetOf<YearMonth>()
        _strength.value = _strength.value.map { s ->
            if (catalogKey(s.exercise) == from) {
                touched += YearMonth.from(s.date); s.copy(exercise = to)
            } else s
        }
        _cardio.value = _cardio.value.map { c ->
            if (catalogKey(c.activity) == from) {
                touched += YearMonth.from(c.date); c.copy(activity = to)
            } else c
        }
        storedOnly = storedOnly.map { if (catalogKey(it.name) == from) it.copy(name = to) else it }
        touched
    }

    /** Removes an exercise and, if asked, everything ever logged against it. */
    fun deleteExercise(name: String, alsoDeleteHistory: Boolean) = mutate {
        val key = catalogKey(name)
        val touched = mutableSetOf<YearMonth>()

        if (alsoDeleteHistory) {
            _strength.value.filter { catalogKey(it.exercise) == key }
                .forEach { touched += YearMonth.from(it.date) }
            _cardio.value.filter { catalogKey(it.activity) == key }
                .forEach { touched += YearMonth.from(it.date) }
            _strength.value = _strength.value.filterNot { catalogKey(it.exercise) == key }
            _cardio.value = _cardio.value.filterNot { catalogKey(it.activity) == key }
        }
        storedOnly = storedOnly.filterNot { catalogKey(it.name) == key }
        touched
    }

    /**
     * Rebuilds the picker list from logged data, merged with names that exist only in
     * `exercises.csv`. Usage counts and last-used dates always come from the real data, so
     * the catalog can never drift out of sync with the logs.
     */
    private fun refreshCatalog() {
        val byKey = LinkedHashMap<String, ExerciseDef>()

        storedOnly.forEach { def -> byKey[catalogKey(def.name)] = def.copy(useCount = 0, lastUsed = null) }

        _strength.value.groupBy { catalogKey(it.exercise) }.forEach { (key, rows) ->
            val newest = rows.maxByOrNull { it.loggedAt } ?: rows.first()
            val existing = byKey[key]
            byKey[key] = ExerciseDef(
                name = existing?.name ?: newest.exercise,
                kind = Kind.STRENGTH,
                createdAt = existing?.createdAt?.takeIf { it > 0L }
                    ?: rows.minOf { it.loggedAt },
                lastUsed = rows.maxOf { it.date },
                useCount = rows.size,
            )
        }

        _cardio.value.groupBy { catalogKey(it.activity) }.forEach { (key, rows) ->
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

        _catalog.value = byKey.values.sortedBy { catalogKey(it.name) }
        storedOnly = _catalog.value
    }

    /** Reuses the spelling already in the catalog, so casing stays consistent per exercise. */
    private fun canonicalName(name: String, kind: Kind): String {
        val key = catalogKey(name)
        return _catalog.value.firstOrNull { catalogKey(it.name) == key && it.kind == kind }?.name
            ?: name
    }

    // --------------------------------------------------------------- plumbing

    /**
     * Applies an in-memory change and persists the months it touched.
     * Serialised so two quick taps can never interleave two writes of the same file.
     */
    private fun mutate(block: () -> Set<YearMonth>) {
        scope.launch {
            writeLock.withLock {
                val touched = try {
                    block()
                } catch (t: Throwable) {
                    _error.value = t.message ?: "Could not apply the change."
                    return@withLock
                }
                refreshCatalog()
                val uri = folderUri ?: return@withLock
                try {
                    withContext(Dispatchers.IO) {
                        touched.forEach { month ->
                            store.saveStrengthMonth(uri, month, _strength.value.filter { YearMonth.from(it.date) == month })
                            store.saveCardioMonth(uri, month, _cardio.value.filter { YearMonth.from(it.date) == month })
                        }
                        store.saveCatalog(uri, _catalog.value)
                    }
                    _error.value = null
                } catch (t: Throwable) {
                    _error.value = t.message ?: "Could not write to the data folder."
                }
            }
        }
    }
}
