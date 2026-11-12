package com.gowthaman.workout.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.cardioOf
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.e1rm
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.logic.CardioAdvice
import com.gowthaman.workout.logic.Overload
import com.gowthaman.workout.logic.StrengthAdvice
import com.gowthaman.workout.pickerNames
import com.gowthaman.workout.sessionsOf
import kotlinx.coroutines.delay
import java.time.LocalDate

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

    // An exercise handed in from the Day screen already has a kind; honour it.
    val lockedKind = remember(initialExercise, catalog) {
        catalog.firstOrNull { catalogKey(it.name) == catalogKey(initialExercise) }?.kind
    }
    var kind by remember { mutableStateOf(lockedKind ?: initialKind) }
    var exercise by remember { mutableStateOf(initialExercise) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LogTopBar(date = date, onDone = onDone)
        HorizontalDivider()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == Kind.STRENGTH,
                    onClick = { kind = Kind.STRENGTH; exercise = "" },
                    label = { Text("Strength") },
                )
                FilterChip(
                    selected = kind == Kind.CARDIO,
                    onClick = { kind = Kind.CARDIO; exercise = "" },
                    label = { Text("Cardio") },
                )
            }

            ExercisePicker(
                value = exercise,
                onValueChange = { exercise = it },
                options = remember(catalog, kind) { pickerNames(catalog, kind) }.map { it.name },
                label = if (kind == Kind.STRENGTH) "Exercise" else "Activity",
            )

            if (exercise.isBlank()) {
                Text(
                    if (kind == Kind.STRENGTH)
                        "Type any name — \"Bench press\", \"Pull-ups\", whatever you call it. " +
                            "It joins the list once you log a set, and gets suggested next time."
                    else
                        "Type an activity — \"Run\", \"Cycling\", \"Rowing\". " +
                            "Duration is enough; distance is optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (kind == Kind.STRENGTH) {
                StrengthForm(
                    vm = vm,
                    date = date,
                    exercise = exercise,
                    settings = settings,
                    history = remember(allSets, exercise) { sessionsOf(allSets, exercise) },
                )
            } else {
                CardioForm(
                    vm = vm,
                    date = date,
                    activity = exercise,
                    settings = settings,
                    history = remember(allCardio, exercise) { cardioOf(allCardio, exercise) },
                )
            }

            Spacer(Modifier.height(40.dp))
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
        SmallIconButton(Icons.Filled.ArrowBack, "Back", onClick = onDone)
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
                vm.addSet(
                    date = date,
                    exercise = exercise,
                    reps = repCount,
                    weight = weight.toNumberOrNull() ?: 0.0,
                    bodyweight = bodyweight,
                    notes = notes,
                )
                notes = ""
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

/** Accepts both "42.5" and "42,5", because number keyboards differ by locale. */
fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()
