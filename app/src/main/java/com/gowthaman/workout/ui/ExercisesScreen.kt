package com.gowthaman.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.gowthaman.workout.data.ExerciseDef
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.catalogKey

@Composable
fun ExercisesScreen(
    vm: AppViewModel,
    onOpenExercise: (String) -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Kind?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val shown = remember(catalog, query, filter) {
        catalog
            .filter { filter == null || it.kind == filter }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                compareByDescending<ExerciseDef> { it.lastUsed }
                    .thenBy { catalogKey(it.name) }
            )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Exercises",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAdd = true }) { Text("Add name") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    SmallIconButton(Icons.Filled.Clear, "Clear") { query = "" }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )

        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("All") },
            )
            FilterChip(
                selected = filter == Kind.STRENGTH,
                onClick = { filter = Kind.STRENGTH },
                label = { Text("Strength") },
            )
            FilterChip(
                selected = filter == Kind.CARDIO,
                onClick = { filter = Kind.CARDIO },
                label = { Text("Cardio") },
            )
        }

        Spacer(Modifier.height(6.dp))
        HorizontalDivider()

        if (shown.isEmpty()) {
            EmptyState(
                title = if (catalog.isEmpty()) "Your list is empty" else "No matches",
                body = if (catalog.isEmpty())
                    "Exercises appear here automatically the first time you log them. " +
                        "Nothing to set up."
                else "Try a different search, or clear the filter.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(shown, key = { catalogKey(it.name) }) { def ->
                    ExerciseRow(def = def, onClick = { onOpenExercise(def.name) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    if (showAdd) {
        AddNameDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, kind ->
                vm.addExerciseName(name, kind)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ExerciseRow(def: ExerciseDef, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (def.kind == Kind.CARDIO) {
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        "cardio",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text(
                when {
                    def.lastUsed == null -> "Never logged"
                    def.kind == Kind.CARDIO ->
                        "${def.useCount} session${if (def.useCount == 1) "" else "s"}  ·  " +
                            def.lastUsed.agoLabel()
                    else ->
                        "${def.useCount} set${if (def.useCount == 1) "" else "s"}  ·  " +
                            def.lastUsed.agoLabel()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddNameDialog(onDismiss: () -> Unit, onAdd: (String, Kind) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.STRENGTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an exercise name") },
        text = {
            Column {
                Text(
                    "Optional — names also get added automatically when you log them.",
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
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == Kind.STRENGTH,
                        onClick = { kind = Kind.STRENGTH },
                        label = { Text("Strength") },
                    )
                    FilterChip(
                        selected = kind == Kind.CARDIO,
                        onClick = { kind = Kind.CARDIO },
                        label = { Text("Cardio") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, kind) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
