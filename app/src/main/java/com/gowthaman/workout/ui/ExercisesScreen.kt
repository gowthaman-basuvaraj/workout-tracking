package com.gowthaman.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.gowthaman.workout.data.FoodDef
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.data.Purpose
import com.gowthaman.workout.data.catalogKey
import java.time.LocalDate

/** One row of the library, whichever catalog it came from. */
private data class LibraryRow(
    val name: String,
    val kind: Kind,
    val lastUsed: LocalDate?,
    val useCount: Int,
    val detail: String,
)

private fun ExerciseDef.toRow(): LibraryRow = LibraryRow(
    name = name,
    kind = kind,
    lastUsed = lastUsed,
    useCount = useCount,
    detail = when {
        lastUsed == null -> "Never logged"
        kind == Kind.CARDIO ->
            "$useCount session${if (useCount == 1) "" else "s"}  ·  ${lastUsed.agoLabel()}"
        else -> "$useCount set${if (useCount == 1) "" else "s"}  ·  ${lastUsed.agoLabel()}"
    },
)

private fun FoodDef.toRow(): LibraryRow = LibraryRow(
    name = name,
    kind = Kind.FOOD,
    lastUsed = lastUsed,
    useCount = useCount,
    detail = when (lastUsed) {
        null -> "Never logged  ·  ${purpose.label} · ${scale.label}"
        else -> "$useCount time${if (useCount == 1) "" else "s"}  ·  ${lastUsed.agoLabel()}" +
            "  ·  ${purpose.label} · ${scale.label}"
    },
)

@Composable
fun ExercisesScreen(
    vm: AppViewModel,
    onOpenExercise: (String) -> Unit,
) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val foodCatalog by vm.foodCatalog.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Kind?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editFood by remember { mutableStateOf<FoodDef?>(null) }

    val rows = remember(catalog, foodCatalog, query, filter) {
        (catalog.map { it.toRow() } + foodCatalog.map { it.toRow() })
            .filter { filter == null || it.kind == filter }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                compareByDescending<LibraryRow> { it.lastUsed }
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
                "Library",
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
            Modifier
                .padding(horizontal = 14.dp, vertical = 2.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter == null, { filter = null }, label = { Text("All") })
            FilterChip(filter == Kind.STRENGTH, { filter = Kind.STRENGTH }, label = { Text("Strength") })
            FilterChip(filter == Kind.CARDIO, { filter = Kind.CARDIO }, label = { Text("Cardio") })
            FilterChip(filter == Kind.FOOD, { filter = Kind.FOOD }, label = { Text("Food") })
        }

        Spacer(Modifier.height(6.dp))
        HorizontalDivider()

        if (rows.isEmpty()) {
            EmptyState(
                title = if (catalog.isEmpty() && foodCatalog.isEmpty()) "Your list is empty"
                else "No matches",
                body = if (catalog.isEmpty() && foodCatalog.isEmpty())
                    "Exercises and foods appear here automatically the first time you log " +
                        "them. Nothing to set up."
                else "Try a different search, or clear the filter.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(rows, key = { "${it.kind}:${catalogKey(it.name)}" }) { row ->
                    LibraryItem(
                        row = row,
                        onClick = {
                            if (row.kind == Kind.FOOD) {
                                editFood = foodCatalog.firstOrNull {
                                    catalogKey(it.name) == catalogKey(row.name)
                                }
                            } else {
                                onOpenExercise(row.name)
                            }
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddNameDialog(
            onDismiss = { showAdd = false },
            onAddExercise = { name, kind -> vm.addExerciseName(name, kind); showAdd = false },
            onAddFood = { name, purpose, scale ->
                vm.addFoodName(name, purpose, scale); showAdd = false
            },
        )
    }

    editFood?.let { def ->
        FoodItemDialog(
            def = def,
            onDismiss = { editFood = null },
            onRename = { newName -> vm.renameFood(def.name, newName); editFood = null },
            onDelete = { withHistory -> vm.deleteFoodName(def.name, withHistory); editFood = null },
        )
    }
}

@Composable
private fun LibraryItem(row: LibraryRow, onClick: () -> Unit) {
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
                    row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (row.kind != Kind.STRENGTH) {
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        if (row.kind == Kind.CARDIO) "cardio" else "food",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text(
                row.detail,
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
private fun AddNameDialog(
    onDismiss: () -> Unit,
    onAddExercise: (String, Kind) -> Unit,
    onAddFood: (String, Purpose, FoodScale) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.STRENGTH) }
    var purpose by remember { mutableStateOf(Purpose.DEFAULT) }
    var scale by remember { mutableStateOf(FoodScale.DEFAULT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a name") },
        text = {
            Column {
                Text(
                    "Optional — names are also added automatically when you log them.",
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
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(kind == Kind.STRENGTH, { kind = Kind.STRENGTH }, label = { Text("Strength") })
                    FilterChip(kind == Kind.CARDIO, { kind = Kind.CARDIO }, label = { Text("Cardio") })
                    FilterChip(kind == Kind.FOOD, { kind = Kind.FOOD }, label = { Text("Food") })
                }
                if (kind == Kind.FOOD) {
                    Spacer(Modifier.height(12.dp))
                    Text("Usual purpose", style = MaterialTheme.typography.labelMedium)
                    ChipFlow {
                        Purpose.entries.forEach {
                            FilterChip(purpose == it, { purpose = it }, label = { Text(it.label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Usual rating", style = MaterialTheme.typography.labelMedium)
                    ChipFlow {
                        FoodScale.entries.forEach {
                            FilterChip(scale == it, { scale = it }, label = { Text(it.label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (kind == Kind.FOOD) onAddFood(name, purpose, scale)
                    else onAddExercise(name, kind)
                },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FoodItemDialog(
    def: FoodDef,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(def.name) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var alsoHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(def.name) },
        text = {
            Column {
                Text(
                    buildString {
                        append("${def.useCount} time${if (def.useCount == 1) "" else "s"} logged")
                        def.lastUsed?.let { append(", last ${it.agoLabel()}") }
                        append(".\nUsually ${def.purpose.label.lowercase()}, ")
                        append("rated ${def.scale.label.lowercase()}.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Renaming rewrites every past entry too.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (confirmingDelete) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoHistory, onCheckedChange = { alsoHistory = it })
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Also delete everything logged for it",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (name.trim() != def.name && name.isNotBlank()) {
                TextButton(onClick = { onRename(name) }) { Text("Rename") }
            } else if (confirmingDelete) {
                TextButton(onClick = { onDelete(alsoHistory) }) {
                    Text(if (alsoHistory) "Delete everything" else "Remove name")
                }
            } else {
                TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
