package com.gowthaman.workout.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.cardioOf
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.StressDef
import com.gowthaman.workout.data.StressDirection
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.StressWeight
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.agoLabel
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.e1rm
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.data.formatSleep
import com.gowthaman.workout.data.fullLabel
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.parseSleepDuration
import com.gowthaman.workout.data.relativeLabel
import com.gowthaman.workout.data.shortLabel
import com.gowthaman.workout.data.summary
import com.gowthaman.workout.logic.CardioAdvice
import com.gowthaman.workout.logic.Overload
import com.gowthaman.workout.logic.StrengthAdvice
import com.gowthaman.workout.pickerNames
import com.gowthaman.workout.sessionsOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

@Composable
fun LogScreen(
    vm: AppViewModel,
    date: LocalDate,
    initialKind: Kind,
    initialExercise: String,
    onDone: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val allSets by vm.strength.collectAsStateWithLifecycle()
    val allCardio by vm.cardio.collectAsStateWithLifecycle()
    val allFood by vm.food.collectAsStateWithLifecycle()
    val foodCatalog by vm.foodCatalog.collectAsStateWithLifecycle()
    val allSleep by vm.sleep.collectAsStateWithLifecycle()
    val allStress by vm.stress.collectAsStateWithLifecycle()
    val stressCatalog by vm.stressCatalog.collectAsStateWithLifecycle()

    // An exercise handed in from the Day screen already has a kind; honour it.
    val lockedKind = remember(initialExercise, catalog) {
        catalog.firstOrNull { catalogKey(it.name) == catalogKey(initialExercise) }?.kind
    }
    var kind by remember { mutableStateOf(lockedKind ?: initialKind) }
    var exercise by remember { mutableStateOf(initialExercise) }

    val kindOrder by vm.kindOrder.collectAsStateWithLifecycle()
    val toast = rememberToaster()

    // Done closes the screen; it does not save. Nothing on the way out said so, and a
    // filled-in form simply vanished. Track whether anything was logged for the name
    // currently in the picker, so leaving with unlogged input can say so first.
    var loggedThisEntry by remember { mutableStateOf(false) }
    var warnedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(kind, exercise) {
        loggedThisEntry = false
        warnedOnce = false
    }

    val onLogged: (String) -> Unit = { message ->
        loggedThisEntry = true
        warnedOnce = false
        toast(message)
        // Clearing the picker is the visible half of the confirmation: the form you just
        // filled in is gone, so there is nothing to wonder about. Strength is the
        // exception -- its button says "Add set 2" because sets come in threes, and
        // retyping the exercise between them would be absurd.
        if (kind != Kind.STRENGTH) exercise = ""
    }

    // Warn once, then take the second tap at its word -- never trap anyone on the screen.
    // Sleep is exempt: its form has no name in the picker to judge by.
    val leave: () -> Unit = {
        val unlogged = kind != Kind.SLEEP && exercise.isNotBlank() && !loggedThisEntry
        if (unlogged && !warnedOnce) {
            warnedOnce = true
            toast("Not logged yet - use the button at the bottom of the form")
        } else {
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LogTopBar(date = date, onDone = leave)
        HorizontalDivider()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (kind == Kind.SLEEP) {
                SleepForm(
                    vm = vm,
                    date = date,
                    existing = remember(allSleep, date) { allSleep.firstOrNull { it.date == date } },
                    onLogged = onLogged,
                    onFinished = onDone,
                )
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            ExercisePicker(
                value = exercise,
                onValueChange = { exercise = it },
                options = when (kind) {
                    Kind.FOOD -> remember(foodCatalog) { foodPickerNames(foodCatalog) }
                    Kind.STRESS -> remember(stressCatalog) { stressPickerNames(stressCatalog) }
                    else -> remember(catalog, kind) { pickerNames(catalog, kind) }.map { it.name }
                },
                label = when (kind) {
                    Kind.STRENGTH -> "Exercise"
                    Kind.CARDIO -> "Activity"
                    Kind.FOOD -> "Food or drink"
                    Kind.STRESS -> "What happened"
                    Kind.SLEEP -> "Sleep"
                },
            )

            if (exercise.isBlank()) {
                Text(
                    when (kind) {
                        Kind.STRENGTH ->
                            "Type any name — \"Bench press\", \"Pull-ups\", whatever you call it. " +
                                "It joins the list once you log a set, and gets suggested next time."
                        Kind.CARDIO ->
                            "Type an activity — \"Run\", \"Cycling\", \"Rowing\". " +
                                "Duration is enough; distance is optional."
                        Kind.FOOD ->
                            "Type what you had — \"Coffee\", \"Toast\", \"Birthday cake\". " +
                                "The next time you tap it, how and why are filled in already."
                        Kind.STRESS ->
                            "Name the thing — \"Deadline\", \"Commute\", \"Walk\", \"Called mum\". " +
                                "Whether it winds you up or down is remembered per item."
                        Kind.SLEEP -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (kind == Kind.STRESS) {
                StressForm(
                    vm = vm,
                    date = date,
                    label = exercise,
                    catalog = stressCatalog,
                    allStress = allStress,
                    onLogged = onLogged,
                )
            } else if (kind == Kind.FOOD) {
                FoodForm(
                    vm = vm,
                    date = date,
                    item = exercise,
                    catalog = foodCatalog,
                    allFood = allFood,
                    onLogged = onLogged,
                )
            } else if (kind == Kind.STRENGTH) {
                StrengthForm(
                    vm = vm,
                    date = date,
                    exercise = exercise,
                    settings = settings,
                    history = remember(allSets, exercise) { sessionsOf(allSets, exercise) },
                    onLogged = onLogged,
                )
            } else {
                CardioForm(
                    vm = vm,
                    date = date,
                    activity = exercise,
                    settings = settings,
                    history = remember(allCardio, exercise) { cardioOf(allCardio, exercise) },
                    onLogged = onLogged,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        KindBar(
            order = kindOrder,
            selected = kind,
            onSelect = { picked -> kind = picked; exercise = "" },
        )
    }
}

/**
 * The five kinds, along the bottom.
 *
 * At the top of a scrolling form they were the furthest thing from a thumb on a screen
 * this tall, and logging is a one-handed job done standing up. The order comes from how
 * often each is actually reached for, so the busiest thing is nearest the thumb rather
 * than wherever the enum happened to declare it.
 *
 * Equal segments rather than wrapped chips: five chips wrapped onto two rows, and a row
 * that changes height as the order changes is worse than one that never moves.
 */
@Composable
private fun KindBar(order: List<Kind>, selected: Kind, onSelect: (Kind) -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            // No navigationBarsPadding here: Root's scaffold has already consumed that
            // inset, and applying it twice leaves a band of dead bar under the buttons.
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            order.forEach { kind ->
                val isSelected = kind == selected
                Surface(
                    onClick = { onSelect(kind) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            kind.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTopBar(date: LocalDate, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onDone)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text("Log workout", style = MaterialTheme.typography.titleMedium)
            Text(
                date.fullLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

/** Free-text field that offers what you have logged before, most recent first. */
@Composable
private fun ExercisePicker(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
) {
    val exactMatch = options.any { catalogKey(it) == catalogKey(value) }
    val matches = remember(value, options) {
        when {
            value.isBlank() -> options.take(10)
            else -> options.filter { it.contains(value.trim(), ignoreCase = true) }.take(10)
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (value.isNotEmpty()) {
                    SmallIconButton(Icons.Filled.Clear, "Clear") { onValueChange("") }
                }
            },
        )

        if (matches.isNotEmpty() && !(exactMatch && matches.size == 1)) {
            Spacer(Modifier.height(8.dp))
            QuickPickRow(options = matches, onPick = onValueChange)
        }

        if (value.isNotBlank() && !exactMatch) {
            Spacer(Modifier.height(6.dp))
            Text(
                "New — \"${value.trim()}\" will be added to your list.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

// ------------------------------------------------------------------- strength

@Composable
private fun StrengthForm(
    onLogged: (String) -> Unit,
    vm: AppViewModel,
    date: LocalDate,
    exercise: String,
    settings: Settings,
    history: List<ExerciseSession>,
) {
    val advice = remember(history, settings, date, exercise) {
        Overload.forStrength(history, settings, date)
    }
    val todaysSets = remember(history, date) {
        history.firstOrNull { it.date == date }?.sets.orEmpty()
    }

    var reps by remember(exercise) { mutableStateOf("") }
    var weight by remember(exercise) { mutableStateOf("") }
    var bodyweight by remember(exercise) { mutableStateOf(false) }
    var notes by remember(exercise) { mutableStateOf("") }

    // Seed the form once per exercise — from the set just logged today if there is one,
    // otherwise from the suggestion — so the common case is "tap Add set" and nothing else.
    androidx.compose.runtime.LaunchedEffect(exercise) {
        val fromToday = todaysSets.lastOrNull()
        val target = advice.target
        when {
            fromToday != null -> {
                reps = fromToday.reps.toString()
                weight = fromToday.weight.takeIf { it > 0 }?.fmt() ?: ""
                bodyweight = fromToday.bodyweight
            }
            target != null -> {
                reps = target.reps.toString()
                weight = target.weight.takeIf { it > 0 }?.fmt() ?: ""
                bodyweight = target.bodyweight
            }
        }
    }

    PreviousSessions(advice = advice, settings = settings)

    SuggestionCard(
        advice = advice,
        settings = settings,
        onUse = {
            advice.target?.let { target ->
                reps = target.reps.toString()
                weight = target.weight.takeIf { it > 0 }?.fmt() ?: ""
                bodyweight = target.bodyweight
            }
        },
    )

    SectionCard(title = "this set") {
        StepperField(
            label = "Reps",
            text = reps,
            onTextChange = { reps = it.filter(Char::isDigit).take(3) },
            onStep = { delta -> reps = ((reps.toIntOrNull() ?: 0) + delta).coerceAtLeast(0).toString() },
        )

        Spacer(Modifier.height(10.dp))

        StepperField(
            label = if (bodyweight) "Added weight" else "Weight",
            text = weight,
            onTextChange = { input ->
                weight = input.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
            },
            onStep = { delta ->
                val step = settings.weightIncrement
                val next = ((weight.toNumberOrNull() ?: 0.0) + delta * step).coerceAtLeast(0.0)
                weight = if (next <= 0.0) "" else next.fmt(2)
            },
            suffix = settings.weightUnit,
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = bodyweight, onCheckedChange = { bodyweight = it })
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Bodyweight movement", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (bodyweight) "Weight above is extra load — leave it empty for plain bodyweight."
                    else "Turn on for pull-ups, dips, push-ups and the like.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        val repCount = reps.toIntOrNull() ?: 0
        val canAdd = repCount > 0 && exercise.isNotBlank()

        Button(
            onClick = {
                val load = weight.toNumberOrNull() ?: 0.0
                vm.addSet(
                    date = date,
                    exercise = exercise,
                    reps = repCount,
                    weight = load,
                    bodyweight = bodyweight,
                    notes = notes,
                )
                notes = ""
                onLogged(
                    "Logged $exercise - $repCount reps " + when {
                        bodyweight && load <= 0 -> "at bodyweight"
                        bodyweight -> "at BW +${load.fmt(1)} ${settings.weightUnit}"
                        else -> "@ ${load.fmt(1)} ${settings.weightUnit}"
                    }
                )
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (todaysSets.isEmpty()) "Add set"
                else "Add set ${todaysSets.size + 1}"
            )
        }
    }

    if (todaysSets.isNotEmpty()) {
        SectionCard(title = "logged on ${date.relativeLabel().lowercase()}") {
            todaysSets.forEach { set ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${set.setNo}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    NumberText("${set.reps} reps", Modifier.width(80.dp))
                    NumberText(
                        set.loadLabel(settings.weightUnit),
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SmallIconButton(Icons.Filled.Clear, "Delete set") { vm.deleteSet(set.id) }
                }
            }
        }
    }
}

@Composable
private fun PreviousSessions(advice: StrengthAdvice, settings: Settings) {
    if (!advice.hasHistory) return

    SectionCard(title = "previous sessions") {
        advice.recent.forEach { session ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column(Modifier.width(118.dp)) {
                    Text(session.date.shortLabel(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        session.date.agoLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NumberText(session.summary(settings.weightUnit), Modifier.weight(1f))
            }
        }

        advice.bestSet?.let { best ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val e1rm = best.e1rm(settings.weightUnit)
            Text(
                buildString {
                    append("Best ever: ${best.reps} x ${best.loadLabel(settings.weightUnit)}")
                    append(" on ${best.date.shortLabel()}")
                    if (e1rm != null) append("  ·  est. 1RM ${e1rm.fmt(1)} ${settings.weightUnit}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionCard(
    advice: StrengthAdvice,
    settings: Settings,
    onUse: () -> Unit,
) {
    val title = when {
        !advice.hasHistory -> "first session"
        advice.isOverload -> "overload due — target today"
        else -> "target today"
    }

    SectionCard(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    advice.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (advice.isOverload) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (advice.isOverload) {
                    Spacer(Modifier.height(2.dp))
                    Pill(
                        "step up",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (advice.target != null) {
                FilledTonalButton(onClick = onUse) { Text("Use") }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            advice.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (advice.hasHistory) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Overload frequency: ${settings.overloadLabel.lowercase()}" +
                    (advice.daysSinceLast?.let { "  ·  last trained ${it} day${if (it == 1) "" else "s"} ago" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --------------------------------------------------------------------- cardio

@Composable
private fun CardioForm(
    onLogged: (String) -> Unit,
    vm: AppViewModel,
    date: LocalDate,
    activity: String,
    settings: Settings,
    history: List<CardioEntry>,
) {
    val advice = remember(history, settings, date, activity) {
        Overload.forCardio(history, settings, date)
    }

    var minutes by remember(activity) { mutableStateOf("") }
    var seconds by remember(activity) { mutableStateOf("") }
    var distance by remember(activity) { mutableStateOf("") }
    var notes by remember(activity) { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(activity) {
        advice.target?.let { target ->
            if (target.durationSec > 0) {
                minutes = (target.durationSec / 60).toString()
                seconds = (target.durationSec % 60).takeIf { it > 0 }?.toString() ?: ""
            }
            if (target.distance > 0) distance = target.distance.fmt(2)
        }
    }

    CardioHistory(advice = advice, settings = settings)
    CardioSuggestionCard(advice = advice, settings = settings, onUse = {
        advice.target?.let { target ->
            minutes = (target.durationSec / 60).toString()
            seconds = (target.durationSec % 60).takeIf { it > 0 }?.toString() ?: ""
            distance = target.distance.takeIf { it > 0 }?.fmt(2) ?: ""
        }
    })

    Stopwatch(onStop = { elapsed ->
        minutes = (elapsed / 60).toString()
        seconds = (elapsed % 60).toString()
    })

    SectionCard(title = "session") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter(Char::isDigit).take(2) },
                label = { Text("Seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
            label = { Text("Distance (optional)") },
            suffix = { Text(settings.distanceUnit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        val totalSec = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        val dist = distance.toNumberOrNull() ?: 0.0

        Button(
            onClick = {
                vm.addCardio(date, activity, totalSec, dist, notes)
                minutes = ""; seconds = ""; distance = ""; notes = ""
                onLogged(
                    "Logged $activity - ${formatDurationLong(totalSec)}" +
                        if (dist > 0) ", ${dist.fmt(2)} ${settings.distanceUnit}" else ""
                )
            },
            enabled = activity.isNotBlank() && (totalSec > 0 || dist > 0),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save session")
        }
    }
}

@Composable
private fun Stopwatch(onStop: (Int) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var accumulated by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(0L) }

    // Elapsed time is read off the wall clock rather than counted in ticks, so pausing,
    // resuming and a janky frame never make the timer drift.
    val elapsed = accumulated +
        if (running) (((now - startedAt) / 1000).toInt()).coerceAtLeast(0) else 0

    androidx.compose.runtime.LaunchedEffect(running, startedAt) {
        while (running) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    SectionCard(title = "timer") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration(elapsed),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!running) {
                FilledTonalButton(onClick = {
                    startedAt = System.currentTimeMillis()
                    now = startedAt
                    running = true
                }) { Text(if (elapsed > 0) "Resume" else "Start") }
            } else {
                FilledTonalButton(onClick = {
                    accumulated = elapsed
                    running = false
                    onStop(accumulated)
                }) { Text("Stop") }
            }
            if (elapsed > 0 && !running) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { accumulated = 0 }) { Text("Reset") }
            }
        }
        Text(
            "Optional — stopping the timer fills in the duration below.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardioHistory(advice: CardioAdvice, settings: Settings) {
    if (!advice.hasHistory) return

    SectionCard(title = "previous sessions") {
        advice.recent.forEach { entry ->
            val dist = Units.distance(entry.distance, entry.distUnit, settings.distanceUnit)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column(Modifier.width(118.dp)) {
                    Text(entry.date.shortLabel(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        entry.date.agoLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NumberText(
                    buildList {
                        if (entry.durationSec > 0) add(formatDuration(entry.durationSec))
                        if (dist > 0) add("${dist.fmt(2)} ${settings.distanceUnit}")
                    }.joinToString("  ·  "),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CardioSuggestionCard(
    advice: CardioAdvice,
    settings: Settings,
    onUse: () -> Unit,
) {
    SectionCard(
        title = when {
            !advice.hasHistory -> "first session"
            advice.isOverload -> "overload due — target today"
            else -> "target today"
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                advice.headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (advice.isOverload) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (advice.target != null) {
                FilledTonalButton(onClick = onUse) { Text("Use") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            advice.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------- sleep

@Composable
private fun SleepForm(
    vm: AppViewModel,
    date: LocalDate,
    existing: SleepNight?,
    onLogged: (String) -> Unit,
    onFinished: () -> Unit,
) {
    var duration by remember(date, existing) {
        mutableStateOf(existing?.let { formatSleep(it.durationMin).replace(" ", "") } ?: "")
    }
    var quality by remember(date, existing) {
        mutableStateOf(existing?.quality ?: SleepQuality.DEFAULT)
    }
    var notes by remember(date, existing) { mutableStateOf(existing?.notes.orEmpty()) }

    SectionCard(title = "the night before ${date.relativeLabel().lowercase()}") {
        Text(
            "A night is filed under the morning you woke up, so this is the sleep that " +
                "led into ${date.shortLabel()}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "how long") {
        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it },
            label = { Text("Duration") },
            placeholder = { Text("7h30") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "7h30, 7:30, 7.5 or 450m all work.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        QuickPickRow(
            options = listOf("5h30", "6h", "6h30", "7h", "7h30", "8h", "8h30"),
            onPick = { duration = it },
        )
    }

    SectionCard(title = "how it was") {
        ChipFlow {
            SleepQuality.entries.forEach { option ->
                FilterChip(
                    selected = quality == option,
                    onClick = { quality = option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your read on it, not a score. Nothing here grades the night.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "log it") {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        val minutes = parseSleepDuration(duration)
        Button(
            onClick = {
                minutes?.let {
                    vm.addSleep(date, it, quality, notes)
                    // One night per day, so a second save replaces rather than adds -- the
                    // wording has to say which happened.
                    onLogged(
                        (if (existing == null) "Logged " else "Replaced with ") +
                            "${formatSleep(it)} / ${quality.label.lowercase()}"
                    )
                    // And then leave. There is exactly one night per day, so staying on a
                    // form that can only overwrite what was just written is an extra tap
                    // asking to be made by mistake. The toast carries the confirmation
                    // out with it.
                    onFinished()
                }
            },
            enabled = minutes != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (existing == null) "Save night" else "Replace night")
        }
        if (duration.isNotBlank() && minutes == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Could not read \"$duration\" as a duration.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (existing != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Already logged: ${formatSleep(existing.durationMin)} / ${existing.quality.label}. " +
                    "Saving replaces it — one night per day.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --------------------------------------------------------------------- stress

/** Stress picker order: most recently logged first. */
fun stressPickerNames(catalog: List<StressDef>): List<String> =
    catalog.sortedWith(
        compareByDescending<StressDef> { it.lastUsed }
            .thenByDescending { it.useCount }
            .thenBy { catalogKey(it.name) }
    ).map { it.name }

@Composable
private fun StressForm(
    onLogged: (String) -> Unit,
    vm: AppViewModel,
    date: LocalDate,
    label: String,
    catalog: List<StressDef>,
    allStress: List<StressEvent>,
) {
    val known = remember(catalog, label) {
        catalog.firstOrNull { catalogKey(it.name) == catalogKey(label) }
    }
    var direction by remember(label, known) {
        mutableStateOf(known?.direction ?: StressDirection.DEFAULT)
    }
    var weight by remember(label, known) {
        mutableStateOf(known?.weight ?: StressWeight.DEFAULT)
    }
    var notes by remember(label) { mutableStateOf("") }

    SectionCard(title = "which way") {
        ChipFlow {
            StressDirection.entries.forEach { option ->
                FilterChip(
                    selected = direction == option,
                    onClick = { direction = option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (direction == StressDirection.BUSTER)
                "Something that wound you down."
            else "Something that wound you up.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "how much it counted") {
        ChipFlow {
            StressWeight.entries.forEach { option ->
                FilterChip(
                    selected = weight == option,
                    onClick = { weight = option },
                    label = { Text(option.label) },
                )
            }
        }
    }

    SectionCard(title = "log it") {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.addStress(date, label, direction, weight, notes)
                notes = ""
                onLogged("Logged $label - ${direction.label.lowercase()}")
            },
            enabled = label.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to the day")
        }
        if (known == null && label.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "New — how it is set here becomes the default next time.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }

    val todays = remember(allStress, date) { allStress.filter { it.date == date } }
    if (todays.isNotEmpty()) {
        SectionCard(title = "logged on ${date.relativeLabel().lowercase()}") {
            todays.forEach { event ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NumberText(event.label)
                        Text(
                            "${event.direction.label} / ${event.weight.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SmallIconButton(Icons.Filled.Clear, "Delete event") { vm.deleteStress(event.id) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- food

/** Food picker order: most recently eaten first, so today's usual suspects lead. */
fun foodPickerNames(catalog: List<FoodDef>): List<String> =
    catalog.sortedWith(
        compareByDescending<FoodDef> { it.lastUsed }
            .thenByDescending { it.useCount }
            .thenBy { catalogKey(it.name) }
    ).map { it.name }

@Composable
private fun FoodForm(
    onLogged: (String) -> Unit,
    vm: AppViewModel,
    date: LocalDate,
    item: String,
    catalog: List<FoodDef>,
    allFood: List<FoodEntry>,
) {
    val known = remember(catalog, item) {
        catalog.firstOrNull { catalogKey(it.name) == catalogKey(item) }
    }

    // Seeded from how this item was last logged — the whole point of the catalog.
    var purpose by remember(item, known) { mutableStateOf(known?.purpose ?: Purpose.DEFAULT) }
    var scale by remember(item, known) { mutableStateOf(known?.scale ?: FoodScale.DEFAULT) }
    var tags by remember(item, known) { mutableStateOf(known?.tags ?: emptySet()) }
    var qty by remember(item) { mutableStateOf("1") }
    var notes by remember(item) { mutableStateOf("") }

    ItemRecap(item = item, allFood = allFood, date = date)

    SectionCard(title = "what for") {
        ChipFlow {
            Purpose.entries.forEach { option ->
                FilterChip(
                    selected = purpose == option,
                    onClick = { purpose = option },
                    label = { Text(option.label) },
                )
            }
        }
    }

    SectionCard(title = "how it rates") {
        ChipFlow {
            FoodScale.entries.forEach { option ->
                FilterChip(
                    selected = scale == option,
                    onClick = { scale = option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your call, not a nutrition score. It is only here so the dashboard can show " +
                "you the mix later.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "what was in it") {
        ChipFlow {
            FoodTag.entries.forEach { option ->
                FilterChip(
                    selected = option in tags,
                    onClick = { tags = if (option in tags) tags - option else tags + option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional, and as many as apply. Leave it empty and the item simply goes " +
                "uncounted here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "log it") {
        StepperField(
            label = "How many",
            text = qty,
            onTextChange = { qty = it.filter(Char::isDigit).take(2) },
            onStep = { delta -> qty = ((qty.toIntOrNull() ?: 1) + delta).coerceAtLeast(1).toString() },
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val count = qty.toIntOrNull() ?: 1
                vm.addFood(date, item, purpose, scale, tags, count, notes)
                qty = "1"
                notes = ""
                onLogged("Logged " + (if (count > 1) "$count x $item" else item))
            },
            enabled = item.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to the day")
        }
    }

    val todays = remember(allFood, date) { allFood.filter { it.date == date } }
    if (todays.isNotEmpty()) {
        SectionCard(title = "logged on ${date.relativeLabel().lowercase()}") {
            todays.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        NumberText(entry.summary())
                        Text(
                            "${entry.purpose.label}  ·  ${entry.scale.label}" +
                                if (entry.tags.isEmpty()) ""
                                else "  ·  ${FoodTag.labels(entry.tags)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SmallIconButton(Icons.Filled.Clear, "Delete entry") { vm.deleteFood(entry.id) }
                }
            }
        }
    }
}

/**
 * How often this item has come up lately.
 *
 * Shown before the form on purpose: "this is your 24th coffee this month" lands far harder
 * at the moment of logging than it ever does on a dashboard afterwards.
 */
@Composable
private fun ItemRecap(item: String, allFood: List<FoodEntry>, date: LocalDate) {
    val key = catalogKey(item)
    val recent = remember(allFood, item, date) {
        allFood.filter {
            catalogKey(it.item) == key &&
                it.date <= date &&
                ChronoUnit.DAYS.between(it.date, date) < 30
        }
    }
    if (recent.isEmpty()) return

    val count = recent.sumOf { it.qty }
    val last = recent.maxOf { it.date }

    SectionCard(title = "this one lately") {
        Text(
            "$count time${if (count == 1) "" else "s"} in the last 30 days",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "last ${last.agoLabel(date)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Accepts both "42.5" and "42,5", because number keyboards differ by locale. */
fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
