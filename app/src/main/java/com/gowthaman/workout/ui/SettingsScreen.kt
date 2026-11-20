package com.gowthaman.workout.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.fmt

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenGuide: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sets by vm.strength.collectAsStateWithLifecycle()
    val cardio by vm.cardio.collectAsStateWithLifecycle()

    var confirmChangeFolder by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) confirmChangeFolder = uri
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        // ------------------------------------------------------------- folder
        SectionCard(title = "data folder") {
            Text(
                settings.folderUri?.let { prettyFolder(it) } ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${sets.size} sets and ${cardio.size} cardio sessions, stored as one CSV per " +
                    "month. Sync this folder with Syncthing, Dropbox or Nextcloud and the same " +
                    "files open on your laptop — README.md in the folder documents the columns, " +
                    "and analyze.py is a ready-made pandas summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ChipFlow {
                FilledTonalButton(onClick = { picker.launch(null) }) {
                    Text("Change folder", maxLines = 1)
                }
                OutlinedButton(onClick = vm::refresh) { Text("Reload", maxLines = 1) }
            }
        }

        // ---------------------------------------------------------- overload
        SectionCard(title = "progressive overload") {
            Text(
                "How often the app asks you to step up. Inside the window it suggests repeating " +
                    "your last session instead, which is how the load actually sticks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ChipFlow {
                listOf(7 to "7 days", 15 to "15 days", 30 to "Monthly")
                    .forEach { (days, label) ->
                        FilterChip(
                            selected = settings.overloadPeriodDays == days,
                            onClick = { vm.setOverloadPeriod(days) },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
            }

            Spacer(Modifier.height(16.dp))

            NumberSetting(
                label = "Weight increment",
                value = settings.weightIncrement.fmt(2),
                suffix = settings.weightUnit,
                help = "The smallest jump you can actually load. 2.5 kg for a barbell, 1 kg or " +
                    "less for dumbbells and machines.",
                decimal = true,
                onCommit = { it.toNumberOrNull()?.let(vm::setWeightIncrement) },
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberSetting(
                    label = "Rep range low",
                    value = settings.repMin.toString(),
                    modifier = Modifier.weight(1f),
                    onCommit = { it.toIntOrNull()?.let { v -> vm.setRepRange(v, settings.repMax) } },
                )
                NumberSetting(
                    label = "Rep range high",
                    value = settings.repMax.toString(),
                    modifier = Modifier.weight(1f),
                    onCommit = { it.toIntOrNull()?.let { v -> vm.setRepRange(settings.repMin, v) } },
                )
            }
            Text(
                "Double progression: reps climb to ${settings.repMax}, then the weight goes up " +
                    "${settings.weightIncrement.fmt(2)} ${settings.weightUnit} and reps reset to " +
                    "${settings.repMin}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(12.dp))

            NumberSetting(
                label = "Bodyweight rep ceiling",
                value = settings.bodyweightRepCeiling.toString(),
                help = "For pull-ups, dips and the like: once a set passes this many reps, the " +
                    "app suggests adding weight instead of more reps.",
                onCommit = { it.toIntOrNull()?.let(vm::setBodyweightCeiling) },
            )
        }

        // ------------------------------------------------------------- units
        SectionCard(title = "units") {
            Text("Weight", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                FilterChip(
                    selected = settings.weightUnit == Units.KG,
                    onClick = { vm.setWeightUnit(Units.KG) },
                    label = { Text("Kilograms", maxLines = 1) },
                )
                FilterChip(
                    selected = settings.weightUnit == Units.LB,
                    onClick = { vm.setWeightUnit(Units.LB) },
                    label = { Text("Pounds", maxLines = 1) },
                )
            }

            Spacer(Modifier.height(14.dp))

            Text("Distance", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                FilterChip(
                    selected = settings.distanceUnit == Units.KM,
                    onClick = { vm.setDistanceUnit(Units.KM) },
                    label = { Text("Kilometres", maxLines = 1) },
                )
                FilterChip(
                    selected = settings.distanceUnit == Units.MI,
                    onClick = { vm.setDistanceUnit(Units.MI) },
                    label = { Text("Miles", maxLines = 1) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Each row keeps the unit it was entered with, so switching never rewrites your " +
                    "history — old entries are converted for display.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------------------------------------------------- bodyweight
        SectionCard(title = "you") {
            NumberSetting(
                label = "Body weight",
                value = settings.userBodyweightKg.takeIf { it > 0 }?.fmt(1) ?: "",
                suffix = "kg",
                help = "Optional. When set, bodyweight exercises contribute real tonnage to your " +
                    "volume charts instead of just rep counts.",
                decimal = true,
                onCommit = { vm.setUserBodyweight(it.toNumberOrNull() ?: 0.0) },
            )
        }

        SectionCard(title = "about") {
            Text(
                "Uncomplicated keeps everything on your device, in files you own. There is no " +
                    "account, no network access and no database — just CSV in the folder you " +
                    "picked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenGuide) { Text("How it works") }
        }

        Spacer(Modifier.height(24.dp))
    }

    confirmChangeFolder?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmChangeFolder = null },
            title = { Text("Use this folder?") },
            text = {
                Text(
                    "The app will read whatever log files are already in ${prettyFolder(uri.toString())} " +
                        "and write new ones there.\n\nNothing is copied out of your current folder, " +
                        "and nothing in it is deleted. If you are moving your data, copy the CSV " +
                        "files across yourself first."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.useFolder(uri)
                    confirmChangeFolder = null
                }) { Text("Use folder") }
            },
            dismissButton = {
                TextButton(onClick = { confirmChangeFolder = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A number field that only writes the setting when editing finishes, so a half-typed value
 * never clamps itself out from under the cursor.
 */
@Composable
private fun NumberSetting(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    help: String? = null,
    decimal: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = if (decimal) input.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                else input.filter(Char::isDigit).take(4)
            },
            label = { Text(label) },
            suffix = suffix?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            if (help != null) {
                Text(
                    help,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (text != value) {
                TextButton(onClick = { onCommit(text) }) { Text("Save") }
            }
        }
    }
}

/** Turns a SAF tree uri into something a person can recognise. */
private fun prettyFolder(uri: String): String {
    val decoded = Uri.decode(uri)
    val tail = decoded.substringAfterLast("/tree/", missingDelimiterValue = decoded)
    return when {
        tail.startsWith("primary:") -> "Internal storage / " + tail.removePrefix("primary:")
        tail.contains(':') -> tail.substringAfter(':').ifBlank { tail }
        else -> tail
    }
}
