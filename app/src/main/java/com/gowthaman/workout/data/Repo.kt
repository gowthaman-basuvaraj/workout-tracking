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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    private val _food = MutableStateFlow<List<FoodEntry>>(emptyList())
    val food: StateFlow<List<FoodEntry>> = _food.asStateFlow()

    private val _foodCatalog = MutableStateFlow<List<FoodDef>>(emptyList())
    val foodCatalog: StateFlow<List<FoodDef>> = _foodCatalog.asStateFlow()

    private val _sleep = MutableStateFlow<List<SleepNight>>(emptyList())
    val sleep: StateFlow<List<SleepNight>> = _sleep.asStateFlow()

    private val _stress = MutableStateFlow<List<StressEvent>>(emptyList())
    val stress: StateFlow<List<StressEvent>> = _stress.asStateFlow()

    private val _stressCatalog = MutableStateFlow<List<StressDef>>(emptyList())
    val stressCatalog: StateFlow<List<StressDef>> = _stressCatalog.asStateFlow()

    /** Catalog entries that came from file but have no logged data yet. */
    private var storedOnly: List<ExerciseDef> = emptyList()
    private var storedFoodOnly: List<FoodDef> = emptyList()
    private var storedStressOnly: List<StressDef> = emptyList()

    /** The folder's README and analysis script only need writing once per folder. */
    private var docsEnsured = false

    private val folderUri: Uri? get() = prefs.current.folderUri?.let(Uri::parse)

    init {
        if (folderUri != null) reload() else _status.value = Status.NO_FOLDER

        // Mirror settings into the folder whenever they change, so the desktop CLI agrees
        // with the app about units and the overload window instead of guessing. The copy
        // is write-only from here; nothing reads it back.
        scope.launch {
            prefs.state
                .map { it.copy(folderUri = null) }
                .distinctUntilChanged()
                .collect { publishSettings(it) }
        }
    }

    private suspend fun publishSettings(settings: Settings) {
        val uri = folderUri ?: return
        writeLock.withLock {
            runCatching { withContext(Dispatchers.IO) { store.saveSettings(uri, settings) } }
        }
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
        _food.value = emptyList()
        _foodCatalog.value = emptyList()
        _sleep.value = emptyList()
        _stress.value = emptyList()
        _stressCatalog.value = emptyList()
        storedOnly = emptyList()
        storedFoodOnly = emptyList()
        storedStressOnly = emptyList()
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
                    _food.value = snapshot.food
                    _sleep.value = snapshot.sleep
                    _stress.value = snapshot.stress
                    storedOnly = snapshot.catalog
                    storedFoodOnly = snapshot.foodCatalog
                    storedStressOnly = snapshot.stressCatalog
                    refreshCatalog()
                    refreshFoodCatalog()
                    refreshStressCatalog()
                    runCatching {
                        withContext(Dispatchers.IO) {
                            store.saveSettings(uri, prefs.current.copy(folderUri = null))
                        }
                    }
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
            exercise = Catalog.canonical(_catalog.value.map { it.name }, name),
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
            activity = Catalog.canonical(_catalog.value.map { it.name }, name),
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

    // ------------------------------------------------------------------ food

    /**
     * Logs one item. Purpose and scale default to whatever this item was last recorded as,
     * which is what keeps a hundredth coffee down to a single tap.
     */
    fun addFood(
        date: LocalDate,
        item: String,
        purpose: Purpose,
        scale: FoodScale,
        qty: Int = 1,
        notes: String = "",
    ) = mutate {
        val name = item.trim()
        if (name.isEmpty()) return@mutate emptySet()
        val row = FoodEntry(
            id = CsvStore.newId(),
            date = date,
            item = Catalog.canonical(_foodCatalog.value.map { it.name }, name),
            purpose = purpose,
            scale = scale,
            qty = qty.coerceAtLeast(1),
            notes = Csv.clean(notes),
            loggedAt = System.currentTimeMillis(),
        )
        _food.value = _food.value + row
        setOf(YearMonth.from(date))
    }

    fun updateFood(updated: FoodEntry) = mutate {
        val old = _food.value.firstOrNull { it.id == updated.id } ?: return@mutate emptySet()
        _food.value = _food.value.map { if (it.id == updated.id) updated else it }
        setOf(YearMonth.from(old.date), YearMonth.from(updated.date))
    }

    fun deleteFood(id: String) = mutate {
        val row = _food.value.firstOrNull { it.id == id } ?: return@mutate emptySet()
        _food.value = _food.value.filter { it.id != id }
        setOf(YearMonth.from(row.date))
    }

    fun addFoodName(name: String, purpose: Purpose, scale: FoodScale) = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate emptySet()
        val key = catalogKey(trimmed)
        if (_foodCatalog.value.any { catalogKey(it.name) == key }) return@mutate emptySet()
        storedFoodOnly = storedFoodOnly + FoodDef(
            name = trimmed,
            purpose = purpose,
            scale = scale,
            createdAt = System.currentTimeMillis(),
        )
        emptySet()
    }

    fun renameFood(oldName: String, newName: String) = mutate {
        val from = catalogKey(oldName)
        val to = newName.trim()
        if (to.isEmpty() || from == catalogKey(to)) return@mutate emptySet()
        val touched = mutableSetOf<YearMonth>()
        _food.value = _food.value.map { f ->
            if (catalogKey(f.item) == from) {
                touched += YearMonth.from(f.date); f.copy(item = to)
            } else f
        }
        storedFoodOnly = storedFoodOnly.map { if (catalogKey(it.name) == from) it.copy(name = to) else it }
        touched
    }

    fun deleteFoodName(name: String, alsoDeleteHistory: Boolean) = mutate {
        val key = catalogKey(name)
        val touched = mutableSetOf<YearMonth>()
        if (alsoDeleteHistory) {
            _food.value.filter { catalogKey(it.item) == key }
                .forEach { touched += YearMonth.from(it.date) }
            _food.value = _food.value.filterNot { catalogKey(it.item) == key }
        }
        storedFoodOnly = storedFoodOnly.filterNot { catalogKey(it.name) == key }
        touched
    }

    private fun refreshFoodCatalog() {
        _foodCatalog.value = Catalog.rebuildFood(storedFoodOnly, _food.value)
        storedFoodOnly = _foodCatalog.value
    }

    private fun refreshStressCatalog() {
        _stressCatalog.value = Catalog.rebuildStress(storedStressOnly, _stress.value)
        storedStressOnly = _stressCatalog.value
    }

    // ------------------------------------------------------------------ sleep

    /**
     * Records a night, replacing any already logged for that date.
     *
     * A night is an attribute of a day rather than an event, so logging twice corrects the
     * first entry instead of producing two.
     */
    fun addSleep(
        date: LocalDate,
        durationMin: Int,
        quality: SleepQuality,
        notes: String = "",
    ) = mutate {
        if (durationMin <= 0) return@mutate emptySet()
        val row = SleepNight(
            id = CsvStore.newId(),
            date = date,
            durationMin = durationMin,
            quality = quality,
            notes = Csv.clean(notes),
            loggedAt = System.currentTimeMillis(),
        )
        _sleep.value = _sleep.value.filterNot { it.date == date } + row
        setOf(YearMonth.from(date))
    }

    fun deleteSleep(id: String) = mutate {
        val row = _sleep.value.firstOrNull { it.id == id } ?: return@mutate emptySet()
        _sleep.value = _sleep.value.filterNot { it.id == id }
        setOf(YearMonth.from(row.date))
    }

    // ----------------------------------------------------------------- stress

    fun addStress(
        date: LocalDate,
        label: String,
        direction: StressDirection,
        weight: StressWeight,
        notes: String = "",
    ) = mutate {
        val name = label.trim()
        if (name.isEmpty()) return@mutate emptySet()
        val row = StressEvent(
            id = CsvStore.newId(),
            date = date,
            label = Catalog.canonical(_stressCatalog.value.map { it.name }, name),
            direction = direction,
            weight = weight,
            notes = Csv.clean(notes),
            loggedAt = System.currentTimeMillis(),
        )
        _stress.value = _stress.value + row
        setOf(YearMonth.from(date))
    }

    fun deleteStress(id: String) = mutate {
        val row = _stress.value.firstOrNull { it.id == id } ?: return@mutate emptySet()
        _stress.value = _stress.value.filterNot { it.id == id }
        setOf(YearMonth.from(row.date))
    }

    fun addStressName(name: String, direction: StressDirection, weight: StressWeight) = mutate {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@mutate emptySet()
        val key = catalogKey(trimmed)
        if (_stressCatalog.value.any { catalogKey(it.name) == key }) return@mutate emptySet()
        storedStressOnly = storedStressOnly + StressDef(
            name = trimmed,
            direction = direction,
            weight = weight,
            createdAt = System.currentTimeMillis(),
        )
        emptySet()
    }

    fun renameStress(oldName: String, newName: String) = mutate {
        val from = catalogKey(oldName)
        val to = newName.trim()
        if (to.isEmpty() || from == catalogKey(to)) return@mutate emptySet()
        val touched = mutableSetOf<YearMonth>()
        _stress.value = _stress.value.map { e ->
            if (catalogKey(e.label) == from) {
                touched += YearMonth.from(e.date); e.copy(label = to)
            } else e
        }
        storedStressOnly = storedStressOnly.map { if (catalogKey(it.name) == from) it.copy(name = to) else it }
        touched
    }

    fun deleteStressName(name: String, alsoDeleteHistory: Boolean) = mutate {
        val key = catalogKey(name)
        val touched = mutableSetOf<YearMonth>()
        if (alsoDeleteHistory) {
            _stress.value.filter { catalogKey(it.label) == key }
                .forEach { touched += YearMonth.from(it.date) }
            _stress.value = _stress.value.filterNot { catalogKey(it.label) == key }
        }
        storedStressOnly = storedStressOnly.filterNot { catalogKey(it.name) == key }
        touched
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

    private fun refreshCatalog() {
        _catalog.value = Catalog.rebuildExercises(storedOnly, _strength.value, _cardio.value)
        storedOnly = _catalog.value
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
                refreshFoodCatalog()
                refreshStressCatalog()
                val uri = folderUri ?: return@withLock
                try {
                    withContext(Dispatchers.IO) {
                        touched.forEach { month ->
                            store.saveStrengthMonth(uri, month, _strength.value.filter { YearMonth.from(it.date) == month })
                            store.saveCardioMonth(uri, month, _cardio.value.filter { YearMonth.from(it.date) == month })
                            store.saveFoodMonth(uri, month, _food.value.filter { YearMonth.from(it.date) == month })
                            store.saveSleepMonth(uri, month, _sleep.value.filter { YearMonth.from(it.date) == month })
                            store.saveStressMonth(uri, month, _stress.value.filter { YearMonth.from(it.date) == month })
                        }
                        store.saveCatalog(uri, _catalog.value)
                        store.saveFoodCatalog(uri, _foodCatalog.value)
                        store.saveStressCatalog(uri, _stressCatalog.value)
                    }
                    _error.value = null
                } catch (t: Throwable) {
                    _error.value = t.message ?: "Could not write to the data folder."
                }
            }
        }
    }
}
