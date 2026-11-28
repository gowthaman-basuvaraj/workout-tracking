package com.gowthaman.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.cardioOf
import com.gowthaman.workout.data.CardioEntry
import com.gowthaman.workout.data.ExerciseSession
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.agoLabel
import com.gowthaman.workout.data.catalogKey
import com.gowthaman.workout.data.e1rm
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDuration
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.data.loadLabel
import com.gowthaman.workout.data.score
import com.gowthaman.workout.data.shortLabel
import com.gowthaman.workout.data.tinyLabel
import com.gowthaman.workout.data.today
import com.gowthaman.workout.data.volume
import com.gowthaman.workout.logic.Overload
import com.gowthaman.workout.sessionsOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
fun ExerciseDetailScreen(
    vm: AppViewModel,
    exercise: String,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onLogNow: (Kind) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val allSets by vm.strength.collectAsStateWithLifecycle()
    val allCardio by vm.cardio.collectAsStateWithLifecycle()

    val kind = remember(catalog, exercise) {
        catalog.firstOrNull { catalogKey(it.name) == catalogKey(exercise) }?.kind ?: Kind.STRENGTH
    }

    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = exercise,
            subtitle = if (kind == Kind.CARDIO) "Cardio" else "Strength",
            onBack = onBack,
            onRename = { showRename = true },
            onDelete = { showDelete = true },
        )
        HorizontalDivider()

        if (kind == Kind.CARDIO) {
            CardioDetail(
                history = remember(allCardio, exercise) { cardioOf(allCardio, exercise) },
                settings = settings,
                onOpenDay = onOpenDay,
                onLogNow = { onLogNow(Kind.CARDIO) },
            )
        } else {
            StrengthDetail(
                history = remember(allSets, exercise) { sessionsOf(allSets, exercise) },
                settings = settings,
                onOpenDay = onOpenDay,
                onLogNow = { onLogNow(Kind.STRENGTH) },
            )
        }
    }

    if (showRename) {
        RenameDialog(
            current = exercise,
            onDismiss = { showRename = false },
            onRename = { newName ->
                vm.renameExercise(exercise, newName)
                showRename = false
                onBack()
            },
        )
    }

    if (showDelete) {
        DeleteDialog(
            name = exercise,
            onDismiss = { showDelete = false },
            onDelete = { withHistory ->
                vm.deleteExercise(exercise, withHistory)
                showDelete = false
                onBack()
            },
        )
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            SmallIconButton(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

// ------------------------------------------------------------------- strength

@Composable
private fun StrengthDetail(
    history: List<ExerciseSession>,
    settings: Settings,
    onOpenDay: (LocalDate) -> Unit,
    onLogNow: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = "Nothing logged yet",
            body = "Once you log a set, this screen shows every session, your best set and " +
                "what to aim for next.",
            action = { FilledTonalButton(onClick = onLogNow) { Text("Log it now") } },
        )
        return
    }

    val unit = settings.weightUnit
    val today = today()
    val allSets = history.flatMap { it.sets }
    val best = allSets.maxByOrNull { it.score(unit) }
    val advice = remember(history, settings) {
        // Advise for the next session rather than for today, so today's own sets count as history.
        Overload.forStrength(history, settings, today.plus(1, DateTimeUnit.DAY))
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = history.size.toString(),
                    label = "sessions",
                    sub = "since ${history.first().date.tinyLabel()}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = allSets.size.toString(),
                    label = "sets",
                    sub = "${allSets.sumOf { it.reps }} reps",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = best?.let { "${it.reps} x ${it.loadLabel(unit)}" } ?: "—",
                    label = "best set",
                    sub = best?.date?.tinyLabel(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                )
                val bestE1rm = allSets.mapNotNull { it.e1rm(unit) }.maxOrNull()
                StatTile(
                    value = bestE1rm?.let { "${it.fmt(1)} $unit" } ?: "—",
                    label = "best est. 1RM",
                    sub = "Epley estimate",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = if (advice.isOverload) "overload due" else "next session") {
                Text(
                    advice.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (advice.isOverload) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    advice.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onLogNow) { Text("Log this exercise") }
            }
        }

        // Progression needs at least two points to show anything meaningful.
        val trend = history.mapNotNull { session ->
            session.sets.mapNotNull { it.e1rm(unit) }.maxOrNull()?.toFloat()
                ?: session.sets.maxOfOrNull { it.reps.toFloat() }
        }
        if (trend.size >= 2) {
            item {
                val weighted = allSets.any { it.weight > 0 }
                SectionCard(
                    title = if (weighted) "estimated 1rm trend" else "top reps trend",
                ) {
                    Sparkline(values = trend)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            history.first().date.tinyLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            history.last().date.tinyLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "EVERY SESSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }

        items(history.asReversed(), key = { it.date.toString() }) { session ->
            SessionRow(
                session = session,
                settings = settings,
                isBestDay = best?.date == session.date,
                onClick = { onOpenDay(session.date) },
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: ExerciseSession,
    settings: Settings,
    isBestDay: Boolean,
    onClick: () -> Unit,
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.date.shortLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        session.date.agoLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isBestDay) {
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            "best",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                NumberText(
                    session.summary(settings.weightUnit),
                    color = MaterialTheme.colorScheme.primary,
                )
                val volume = session.volume(settings.weightUnit, settings.userBodyweightKg)
                Text(
                    "${session.sets.size} sets  ·  ${session.totalReps} reps" +
                        if (volume > 0) "  ·  ${volume.fmt(0)} ${settings.weightUnit} volume" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.sets.any { it.notes.isNotBlank() }) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        // The same note on every set of a session should read once, not N times.
                        session.sets.map { it.notes }.filter { it.isNotBlank() }
                            .distinct().joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------- cardio

@Composable
private fun CardioDetail(
    history: List<CardioEntry>,
    settings: Settings,
    onOpenDay: (LocalDate) -> Unit,
    onLogNow: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = "Nothing logged yet",
            body = "Log a session and this screen tracks every one of them, plus what to aim " +
                "for next.",
            action = { FilledTonalButton(onClick = onLogNow) { Text("Log it now") } },
        )
        return
    }

    val unit = settings.distanceUnit
    val today = today()
    val advice = remember(history, settings) {
        Overload.forCardio(history, settings, today.plus(1, DateTimeUnit.DAY))
    }
    val totalSec = history.sumOf { it.durationSec }
    val totalDist = history.sumOf { Units.distance(it.distance, it.distUnit, unit) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = history.size.toString(),
                    label = "sessions",
                    sub = "since ${history.first().date.tinyLabel()}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatDurationLong(totalSec),
                    label = "total time",
                    sub = if (totalDist > 0) "${totalDist.fmt(1)} $unit" else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            val longest = history.maxByOrNull { Units.distance(it.distance, it.distUnit, unit) }
            val best = longest?.let { Units.distance(it.distance, it.distUnit, unit) } ?: 0.0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (best > 0) "${best.fmt(2)} $unit" else "—",
                    label = "longest distance",
                    sub = longest?.date?.tinyLabel(),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                )
                val longestTime = history.maxByOrNull { it.durationSec }
                StatTile(
                    value = longestTime?.durationSec?.let { formatDuration(it) } ?: "—",
                    label = "longest session",
                    sub = longestTime?.date?.tinyLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionCard(title = if (advice.isOverload) "overload due" else "next session") {
                Text(
                    advice.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (advice.isOverload) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    advice.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onLogNow) { Text("Log this activity") }
            }
        }

        val trend = history.map { entry ->
            val dist = Units.distance(entry.distance, entry.distUnit, unit)
            if (dist > 0) dist.toFloat() else entry.durationSec / 60f
        }
        if (trend.size >= 2) {
            item {
                SectionCard(
                    title = if (history.any { it.distance > 0 }) "distance trend" else "duration trend"
                ) {
                    Sparkline(values = trend)
                }
            }
        }

        item {
            Text(
                "EVERY SESSION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }

        items(history.asReversed(), key = { it.id }) { entry ->
            val dist = Units.distance(entry.distance, entry.distUnit, unit)
            SectionCard(modifier = Modifier.clickable { onOpenDay(entry.date) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.date.shortLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        entry.date.agoLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                NumberText(
                    buildList {
                        if (entry.durationSec > 0) add(formatDuration(entry.durationSec))
                        if (dist > 0) add("${dist.fmt(2)} $unit")
                        if (entry.durationSec > 0 && dist > 0) {
                            add("${formatDuration((entry.durationSec / dist).toInt())} /$unit")
                        }
                    }.joinToString("  ·  "),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (entry.notes.isNotBlank()) {
                    Text(
                        entry.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- dialogs

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename exercise") },
        text = {
            Column {
                Text(
                    "Every past entry is rewritten too, so your history stays in one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name.trim() != current,
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(
    name: String,
    onDismiss: () -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var alsoHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = {
            Column {
                Text(
                    "By default this only removes the name from your picker. Past entries stay " +
                        "in the CSV files, and the name reappears if you log it again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoHistory, onCheckedChange = { alsoHistory = it })
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Also delete all logged history",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(alsoHistory) }) {
                Text(if (alsoHistory) "Delete everything" else "Remove name")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
