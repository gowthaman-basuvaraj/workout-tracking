package com.gowthaman.workout.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import androidx.compose.ui.res.painterResource
import com.gowthaman.workout.R
import com.gowthaman.workout.data.FoodEntry
import com.gowthaman.workout.data.FoodTag
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.SleepNight
import com.gowthaman.workout.data.StrengthSet
import com.gowthaman.workout.data.StressEvent
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.agoLabel
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.formatSleep
import com.gowthaman.workout.data.fullLabel
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.relativeLabel
import com.gowthaman.workout.data.shortLabel
import com.gowthaman.workout.data.summary
import java.time.LocalDate

@Composable
fun DayScreen(
    vm: AppViewModel,
    onLog: (Kind, String?) -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenGuide: () -> Unit,
) {
    val day by vm.day.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val guideSeen by vm.guideSeen.collectAsStateWithLifecycle()
    val kindOrder by vm.kindOrder.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    var confirmDelete by remember { mutableStateOf<ExerciseSession?>(null) }

    Scaffold(
        // Root already consumes the system bars. Without this the nested Scaffold applies
        // the status-bar inset a second time, leaving a band of background above the date.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onLog(Kind.STRENGTH, null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Log") },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            DateNavigator(
                date = day.date,
                today = today,
                onShift = vm::shiftDay,
                onPick = vm::selectDate,
                onToday = vm::goToToday,
                onRefresh = vm::refresh,
                onHelp = onOpenGuide,
            )

            HorizontalDivider()

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Shown once, answered once, and never again whichever way it is
                // answered -- the (?) in the bar above is always there afterwards.
                if (!guideSeen) {
                    item {
                        SectionCard(title = "new here?") {
                            Text(
                                "Two minutes on how this works: where your data lives, how " +
                                    "logging goes, and what the app deliberately does not do.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = {
                                    vm.markGuideSeen()
                                    onOpenGuide()
                                }) { Text("Read it") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { vm.markGuideSeen() }) { Text("No thanks") }
                            }
                        }
                    }
                }

                if (day.isEmpty) {
                    item {
                        EmptyState(
                            title = if (day.date == today) "Nothing logged today"
                            else "Nothing logged on ${day.date.shortLabel()}",
                            body = "Tap Log to add an exercise or something you ate. The pickers " +
                                "fill themselves in from whatever you type, so the first entry " +
                                "starts your list.",
                            action = {
                                // Wraps whole buttons onto a second line; a plain Row
                                // squeezes the last one until its label breaks mid-word.
                                // Same order as the bar on the log screen. Two different
                                // orders for the same five things would be worse than one
                                // order that is occasionally not what you wanted.
                                CenteredChipFlow {
                                    kindOrder.forEach { kind ->
                                        FilledTonalButton(onClick = { onLog(kind, null) }) {
                                            Text(kind.label, maxLines = 1)
                                        }
                                    }
                                }
                            },
                        )
                    }
                } else {
                    item {
                        DaySummary(
                            exercises = day.strength.size + day.cardio.size,
                            foodItems = day.food.sumOf { it.qty },
                            sets = day.totalSets,
                            reps = day.totalReps,
                            volume = day.volume,
                            unit = settings.weightUnit,
                        )
                    }
                }

                items(day.strength, key = { it.exercise }) { session ->
                    StrengthGroupCard(
                        session = session,
                        unit = settings.weightUnit,
                        onOpenExercise = { onOpenExercise(session.exercise) },
                        onAddSet = { onLog(Kind.STRENGTH, session.exercise) },
                        onDeleteSet = vm::deleteSet,
                        onDeleteAll = { confirmDelete = session },
                    )
                }

                day.sleep?.let { night ->
                    item {
                        SleepRow(night = night, onDelete = { vm.deleteSleep(night.id) })
                    }
                }

                if (day.stress.isNotEmpty()) {
                    item {
                        Text(
                            "STRESS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                        )
                    }
                }

                items(day.stress, key = { it.id }) { event ->
                    StressRow(event = event, onDelete = { vm.deleteStress(event.id) })
                }

                if (day.food.isNotEmpty()) {
                    item {
                        Text(
                            "FOOD",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                        )
                    }
                }

                items(day.food, key = { it.id }) { entry ->
                    FoodRow(entry = entry, onDelete = { vm.deleteFood(entry.id) })
                }

                items(day.cardio, key = { it.id }) { entry ->
                    CardioCard(
                        entry = entry,
                        displayUnit = settings.distanceUnit,
                        onOpenExercise = { onOpenExercise(entry.activity) },
                        onDelete = { vm.deleteCardio(entry.id) },
                    )
                }

            }
        }
    }

    confirmDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${session.exercise}?") },
            text = {
                Text(
                    "This deletes all ${session.sets.size} set" +
                        "${if (session.sets.size == 1) "" else "s"} logged on " +
                        "${session.date.shortLabel()}. History on other days is untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteExerciseOnDay(session.date, session.exercise)
                    confirmDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    today: LocalDate,
    onShift: (Long) -> Unit,
    onPick: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(painterResource(R.drawable.ic_help), "How it works", onClick = onHelp)
        SmallIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") { onShift(-1) }

        Column(
            Modifier
                .weight(1f)
                .clickable {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onPick(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth,
                    ).show()
                }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.relativeLabel(today),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                // The headline already names the day for dates near today, so the second
                // line carries the year; further out it carries the distance instead.
                if (date.relativeLabel(today) == date.shortLabel()) date.agoLabel(today)
                else date.fullLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SmallIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") { onShift(1) }

        if (date != today) {
            TextButton(onClick = onToday) { Text("Today") }
        } else {
            SmallIconButton(Icons.Filled.Refresh, "Reload from folder", onClick = onRefresh)
        }
    }
}

@Composable
private fun DaySummary(
    exercises: Int,
    foodItems: Int,
    sets: Int,
    reps: Int,
    volume: Double,
    unit: String,
) {
    val parts = buildList {
        if (exercises > 0) add("$exercises ${if (exercises == 1) "entry" else "entries"}")
        if (foodItems > 0) add("$foodItems food")
        if (sets > 0) add("$sets ${if (sets == 1) "set" else "sets"}")
        if (reps > 0) add("$reps reps")
        if (volume > 0) add("${volume.fmt(0)} $unit volume")
    }
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun StrengthGroupCard(
    session: ExerciseSession,
    unit: String,
    onOpenExercise: () -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenExercise)
            ) {
                Text(
                    session.exercise,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    session.summary(unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SmallIconButton(Icons.Filled.Delete, "Remove exercise", onClick = onDeleteAll)
        }

        Spacer(Modifier.height(8.dp))

        session.sets.forEach { set ->
            SetRow(set = set, unit = unit, onDelete = { onDeleteSet(set.id) })
        }

        TextButton(onClick = onAddSet, modifier = Modifier.padding(top = 2.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add set")
        }
    }
}

@Composable
private fun SetRow(set: StrengthSet, unit: String, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${set.setNo}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        NumberText("${set.reps} reps", Modifier.width(78.dp))
        NumberText(
            set.loadLabel(unit),
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
        if (set.notes.isNotBlank()) {
            Text(
                set.notes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        SmallIconButton(Icons.Filled.Clear, "Delete set", onClick = onDelete)
    }
}

/** Last night, shown first: it sets the tone for everything else on the day. */
@Composable
private fun SleepRow(night: SleepNight, onDelete: () -> Unit) {
    SectionCard(title = "slept") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatSleep(night.durationMin),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                val (container, content) = qualityPillColors(night.quality)
                Pill(night.quality.label, container = container, content = content)
                if (night.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        night.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SmallIconButton(Icons.Filled.Clear, "Delete sleep", onClick = onDelete)
        }
    }
}

@Composable
private fun StressRow(event: StressEvent, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (container, content) = directionPillColors(event.direction)
                    Pill(event.direction.label, container = container, content = content)
                    Spacer(Modifier.width(6.dp))
                    Pill(event.weight.label)
                }
                if (event.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SmallIconButton(Icons.Filled.Clear, "Delete event", onClick = onDelete)
        }
    }
}

/** One logged item. Flat and factual — the counts on the dashboard do the editorialising. */
@Composable
private fun FoodRow(entry: FoodEntry, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.summary(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(entry.purpose.label)
                    Spacer(Modifier.width(6.dp))
                    val (container, content) = scalePillColors(entry.scale)
                    Pill(entry.scale.label, container = container, content = content)
                }
                // Their own line and a size down: purpose and rating are what every item
                // has, tags are an aside on the ones that carry them. Same left edge and
                // same shape as the row above, so the two lines read as one block.
                if (entry.tags.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    ChipFlow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        entry.tags.sortedBy { it.ordinal }.forEach { tag ->
                            Pill(
                                tag.label,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                                small = true,
                                outlined = true,
                            )
                        }
                    }
                }
                if (entry.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.notes,
                        // Indented by a pill's own padding so it starts under the pill
                        // text above rather than under the pill's edge.
                        modifier = Modifier.padding(start = 11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SmallIconButton(Icons.Filled.Clear, "Delete entry", onClick = onDelete)
        }
    }
}

@Composable
private fun CardioCard(
    entry: CardioEntry,
    displayUnit: String,
    onOpenExercise: () -> Unit,
    onDelete: () -> Unit,
) {
    val distance = Units.distance(entry.distance, entry.distUnit, displayUnit)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenExercise)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.activity,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        "cardio",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.height(4.dp))
                NumberText(
                    buildList {
                        if (entry.durationSec > 0) add(formatDuration(entry.durationSec))
                        if (distance > 0) add("${distance.fmt(2)} $displayUnit")
                        if (entry.durationSec > 0 && distance > 0) {
                            val pace = entry.durationSec / distance
                            add("${formatDuration(pace.toInt())} /$displayUnit")
                        }
                    }.joinToString("  ·  ").ifEmpty { "—" },
                    color = MaterialTheme.colorScheme.primary,
                )
                if (entry.notes.isNotBlank()) {
                    Text(
                        entry.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SmallIconButton(Icons.Filled.Clear, "Delete session", onClick = onDelete)
        }
    }
}
