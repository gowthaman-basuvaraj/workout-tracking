package com.gowthaman.workout.data

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.datetime.LocalDate
import com.gowthaman.workout.data.YearMonth

/**
 * Single source of truth for logged data.
 *
 * The whole data set is held in memory (a few years of training is well under a megabyte)
 * and written back to CSV after every change. Reads are therefore instant and the files on
 * disk are always current, which is what lets a sync client pick changes up immediately.
 */
class Repo(
    private val prefs: Prefs,
    /**
     * Opens the data folder at a location this repository does not interpret.
     *
     * The string came from [Settings.folderUri] and means whatever the platform that
     * wrote it says it means -- a SAF tree URI on Android, a path on a desktop. Keeping
     * it opaque here is what lets the folder be chosen on one platform and simply handed
     * over on another.
     */
    private val openStore: (String) -> DocumentStore,
    /** Reads one of the generated docs out of the app's own bundle. */
    private val bundledDoc: (String) -> String,
    /**
     * Where blocking file work goes.
     *
     * Injected rather than reached for: `Dispatchers.IO` does not exist in common code,
     * and passing it in means a test can hand over an immediate dispatcher instead of
     * waiting on real threads.
     */
    private val io: CoroutineDispatcher,
) {

    enum class Status { NO_FOLDER, LOADING, READY, ERROR }
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val writeLock = Mutex()

    /**
     * Seeded from the saved folder rather than from NO_FOLDER.
     *
     * The folder is in SharedPreferences and is known synchronously, but the load is
     * not. Starting at NO_FOLDER meant that between construction and the loading
     * coroutine actually running, the app said it had no folder -- and the first-run
     * picker appeared, telling someone with years of entries that there was nothing
     * there. A second or two of that is genuinely frightening.
     */
    private val _status = MutableStateFlow(
        if (prefs.current.folderUri != null) Status.LOADING else Status.NO_FOLDER
    )
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * Whether a load has ever finished.
     *
     * LOADING means "there has never been anything on screen", which is the only time a
     * full-screen loading state is the right answer. The refresh on every onResume must
     * not throw the screen away and start again; it has real data to leave up.
     */
    @Volatile
    private var everLoaded = false

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

    private val folderLocation: String? get() = prefs.current.folderUri

    /**
     * The archive, pointed at the folder currently chosen.
     *
     * Rebuilt per call rather than held, because the folder can change underneath it
     * (Settings can re-pick or forget one) and a [SafStore] is a URI and a context --
     * cheaper to construct than to invalidate correctly.
     */
    private val archive: CsvStore? get() = folderLocation?.let { CsvStore(openStore(it)) }

    init {
        if (folderLocation != null) reload() else _status.value = Status.NO_FOLDER

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
        val csv = archive ?: return
        writeLock.withLock {
            runCatching { withContext(io) { csv.saveSettings(settings) } }
        }
    }

    // -------------------------------------------------------------------- folder

    /**
     * Remembers the folder so it survives a restart, then loads from it.
     *
     * Whatever the platform had to do to earn access to that location -- Android has to
     * take a persistable permission on the tree -- has happened before this is called.
     */
    fun useFolder(location: String) {
        prefs.update { it.copy(folderUri = location) }
        docsEnsured = false
        // A different folder holds different data, so this is a first load again.
        everLoaded = false
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
        everLoaded = false
        _status.value = Status.NO_FOLDER
    }

    fun reload() {
        val csv = archive
        if (csv == null) {
            _status.value = Status.NO_FOLDER
            return
        }
        // Set before the coroutine, not inside it: the gap between the two is exactly
        // the window the screen was getting wrong.
        if (!everLoaded) _status.value = Status.LOADING
        scope.launch {
            // Shares the write lock: a load must never interleave with a write, and two
            // loads must never race each other (startup and the onResume refresh overlap,
            // and both would otherwise try to create the folder's README side by side).
            writeLock.withLock {
                try {
                    val snapshot = withContext(io) {
                        if (!docsEnsured) {
                            csv.ensureDocs(bundledDoc)
                            docsEnsured = true
                        }
                        csv.load()
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
                        withContext(io) {
                            csv.saveSettings(prefs.current.copy(folderUri = null))
                        }
                    }
                    _error.value = null
                    everLoaded = true
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
            loggedAt = nowMillis(),
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
            loggedAt = nowMillis(),
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
        tags: Set<FoodTag> = emptySet(),
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
            tags = tags,
            qty = qty.coerceAtLeast(1),
            notes = Csv.clean(notes),
            loggedAt = nowMillis(),
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
            createdAt = nowMillis(),
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
            loggedAt = nowMillis(),
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
            loggedAt = nowMillis(),
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
            createdAt = nowMillis(),
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
            createdAt = nowMillis(),
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
                val csv = archive ?: return@withLock
                try {
                    withContext(io) {
                        touched.forEach { month ->
                            csv.saveStrengthMonth(month, _strength.value.filter { YearMonth.from(it.date) == month })
                            csv.saveCardioMonth(month, _cardio.value.filter { YearMonth.from(it.date) == month })
                            csv.saveFoodMonth(month, _food.value.filter { YearMonth.from(it.date) == month })
                            csv.saveSleepMonth(month, _sleep.value.filter { YearMonth.from(it.date) == month })
                            csv.saveStressMonth(month, _stress.value.filter { YearMonth.from(it.date) == month })
                        }
                        csv.saveCatalog(_catalog.value)
                        csv.saveFoodCatalog(_foodCatalog.value)
                        csv.saveStressCatalog(_stressCatalog.value)
                    }
                    _error.value = null
                } catch (t: Throwable) {
                    _error.value = t.message ?: "Could not write to the data folder."
                }
            }
        }
    }
}
