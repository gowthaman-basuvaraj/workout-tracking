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
import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.gowthaman.workout.lock.Biometrics
import com.gowthaman.workout.logic.Reminder
import com.gowthaman.workout.logic.ReminderSettings
import com.gowthaman.workout.logic.Reminders
import com.gowthaman.workout.notify.ReminderAlarm
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.Units
import com.gowthaman.workout.data.fmt

@Composable
fun SettingsScreen(vm: AppViewModel, onOpenGuide: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
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

        ReminderCard(vm = vm, settings = reminders)

        LockCard(enabled = lockEnabled, onChange = vm::setLockEnabled)

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
 *
 * "Editing finishes" has three meanings here, and all of them save, because the one that
 * used to be the only one was the one nobody could see. The save button sat under the
 * field, under the help text — which is under the keyboard the moment you tap in. A value
 * could sit there unsaved for days.
 *
 *  - The tick inside the field, which is level with what you just typed.
 *  - The keyboard's own done key, which is on the thing covering everything else.
 *  - Moving focus away, because leaving a field you have finished with is not a reason to
 *    throw the number away.
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
    // Focus arrives unfocused, so without this the first composition would count as
    // leaving the field and commit a value nobody typed.
    var everFocused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val commit = { if (text != value) onCommit(text) }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = if (decimal) input.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                else input.filter(Char::isDigit).take(4)
            },
            label = { Text(label) },
            suffix = suffix?.let { { Text(it) } },
            // In the field rather than under it, so it is where your eye already is and
            // the keyboard cannot hide it. It appears only when there is a change to
            // save, and inside the field it can appear without moving anything.
            trailingIcon = {
                if (text != value) {
                    IconButton(onClick = { commit(); focus.clearFocus() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save $label")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        everFocused = true
                    } else if (everFocused) {
                        everFocused = false
                        commit()
                    }
                },
        )
        if (help != null) {
            Text(
                help,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Reminders: one switch over a list.
 *
 * The switch is the thing that is off by default and the thing that asks for the
 * notification permission. The list under it is empty until someone adds to it, and the
 * meal names are offered as one-tap suggestions because "breakfast, lunch, dinner" is the
 * reason anyone wants more than one of these.
 *
 * The switch stays off if the permission is refused. Showing it as on while the platform
 * drops every notification would be a lie the app tells once and the user finds out about
 * a week later.
 */
@Composable
private fun ReminderCard(vm: AppViewModel, settings: ReminderSettings) {
    val context = LocalContext.current
    var deniedNotice by remember { mutableStateOf(false) }

    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        deniedNotice = !granted
        if (granted) vm.updateReminders { it.copy(enabled = true) }
    }

    SectionCard(title = "reminders") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remind me to log", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        !settings.enabled -> "Off. Nothing is scheduled."
                        settings.active.isEmpty() && settings.hasAny ->
                            "On, but no days chosen — nothing will fire"
                        settings.active.isEmpty() -> "On. Add a reminder below."
                        else -> "${settings.active.size} scheduled"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { wanted ->
                    deniedNotice = false
                    when {
                        !wanted -> vm.updateReminders { it.copy(enabled = false) }
                        ReminderAlarm.canPost(context) ->
                            vm.updateReminders { it.copy(enabled = true) }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> vm.updateReminders { it.copy(enabled = true) }
                    }
                },
            )
        }

        if (deniedNotice) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Android refused the notification permission, so reminders stay off. You " +
                    "can grant it in the system app settings and switch this on again.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!settings.enabled) return@SectionCard

        settings.reminders.forEach { reminder ->
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            ReminderRow(
                reminder = reminder,
                onEdit = { block -> vm.editReminder(reminder.id, block) },
                onRemove = { vm.removeReminder(reminder.id) },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            "ADD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Reminders.SUGGESTIONS.forEach { (label, time) ->
                val (hour, minute) = time
                val already = settings.reminders.any { it.label.equals(label, ignoreCase = true) }
                FilledTonalButton(
                    onClick = { vm.addReminder(label, hour, minute) },
                    enabled = !already,
                ) { Text(label, maxLines = 1) }
            }
            FilledTonalButton(onClick = { vm.addReminder("", 20, 0) }) {
                Text("Something else", maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Each one asks whether there is anything to write down. None of them check " +
                "whether you logged anything, and none will ever say you missed a day.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Android decides exactly when to deliver them, so one can arrive a little " +
                "after the time you pick. Asking for it to the minute would mean asking " +
                "for the alarm-clock permission, which a nudge does not need.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One reminder: its name, its time, its days, and a way to be rid of it. */
@Composable
private fun ReminderRow(
    reminder: Reminder,
    onEdit: ((Reminder) -> Reminder) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var label by remember(reminder.id, reminder.label) { mutableStateOf(reminder.label) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(24) },
            label = { Text("Name") },
            placeholder = { Text("Breakfast") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEdit { it.copy(label = label) } }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state ->
                    if (!state.isFocused && label != reminder.label) {
                        onEdit { it.copy(label = label) }
                    }
                },
        )
        Spacer(Modifier.width(4.dp))
        FilledTonalButton(onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onEdit { it.copy(hour = hour, minute = minute) } },
                reminder.hour,
                reminder.minute,
                true,
            ).show()
        }) { Text(reminder.timeLabel) }
        SmallIconButton(Icons.Filled.Clear, "Remove ${reminder.title}", onClick = onRemove)
    }

    Spacer(Modifier.height(8.dp))
    ChipFlow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in reminder.days,
                onClick = {
                    onEdit {
                        it.copy(days = if (day in it.days) it.days - day else it.days + day)
                    }
                },
                // SHORT, not NARROW: narrow gives "M T W T F S S", where Tuesday and
                // Thursday are both T and the weekend is two esses. Two rows of readable
                // chips beats one row of guesswork.
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }

    if (!reminder.isActive) {
        Spacer(Modifier.height(6.dp))
        Text(
            "No days chosen, so this one never fires.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The optional lock.
 *
 * The caveat is not small print. The log is plain CSV in a folder the user chose, usually
 * synced to a laptop — anyone holding the unlocked phone can read it from Files without
 * going near this app. Saying "locked" without saying that would be the app claiming a
 * protection it does not provide, which is worse than having no lock at all.
 */
@Composable
private fun LockCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Re-checked on every composition rather than remembered: someone can leave, enrol a
    // fingerprint in the phone's settings, and come straight back.
    val unavailable = Biometrics.unavailableReason(context)

    SectionCard(title = "lock") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unlock with biometrics", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        unavailable != null -> "Unavailable"
                        enabled -> "Asked for after a minute away, and on a cold start"
                        else -> "Off. The app opens straight away."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                enabled = unavailable == null,
                onCheckedChange = onChange,
            )
        }

        if (unavailable != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                unavailable,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "This locks the app, not your files. Your log is plain CSV in the folder you " +
                "picked, and anyone with this phone unlocked can read it from a file " +
                "manager, or from your laptop if the folder is synced. Encrypting it " +
                "would mean you could no longer open it anywhere else, which is the whole " +
                "point of keeping it this way.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your screen lock works as well as your fingerprint, so a sensor that stops " +
                "recognising you cannot shut you out.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
