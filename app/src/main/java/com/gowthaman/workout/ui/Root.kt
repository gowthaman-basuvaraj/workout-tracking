package com.gowthaman.workout.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.Kind
import com.gowthaman.workout.lock.Biometrics
import com.gowthaman.workout.data.Repo
import java.time.LocalDate

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DAY("day", "Day", Icons.Filled.DateRange),
    EXERCISES("exercises", "Library", Icons.AutoMirrored.Filled.List),
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.Home),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

object Routes {
    const val LOG = "log"
    const val GUIDE = "guide"

    fun log(date: LocalDate, kind: Kind, exercise: String? = null): String =
        "$LOG?date=$date&kind=${kind.name}&exercise=${Uri.encode(exercise.orEmpty())}"

    fun exercise(name: String): String = "exercise/${Uri.encode(name)}"
}

@Composable
fun Root(vm: AppViewModel) {
    val status by vm.status.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()

    // Before everything, including the folder picker: whatever is on the other side of
    // this is the thing the lock exists to keep off a borrowed phone.
    if (locked) {
        LockGate(onUnlocked = vm::unlock)
        return
    }

    if (status == Repo.Status.NO_FOLDER) {
        FolderGate(onPick = vm::useFolder)
        return
    }

    // Only ever the very first load. A refresh leaves the data that is already on screen
    // alone, because replacing a day you are looking at with a spinner would be worse
    // than a slightly stale number for a moment.
    if (status == Repo.Status.LOADING) {
        LoadingGate()
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.dismissError()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // The log screen is a focused, full-screen task; the tab bar would only get in the way.
            if (currentRoute == null || !currentRoute.startsWith(Routes.LOG)) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy
                            ?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.DAY.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Tab.DAY.route) {
                DayScreen(
                    vm = vm,
                    onLog = { kind, exercise ->
                        nav.navigate(Routes.log(vm.selectedDate.value, kind, exercise))
                    },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                    onOpenGuide = { nav.navigate(Routes.GUIDE) },
                )
            }

            composable(Tab.EXERCISES.route) {
                ExercisesScreen(
                    vm = vm,
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }

            composable(Tab.DASHBOARD.route) {
                DashboardScreen(
                    vm = vm,
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }

            composable(Tab.SETTINGS.route) {
                SettingsScreen(vm = vm, onOpenGuide = { nav.navigate(Routes.GUIDE) })
            }

            composable(Routes.GUIDE) {
                GuideScreen(onBack = { nav.popBackStack() })
            }

            composable("exercise/{name}") { entry ->
                val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
                ExerciseDetailScreen(
                    vm = vm,
                    exercise = name,
                    onBack = { nav.popBackStack() },
                    onOpenDay = { date ->
                        vm.selectDate(date)
                        nav.navigate(Tab.DAY.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onLogNow = { kind ->
                        nav.navigate(Routes.log(vm.selectedDate.value, kind, name))
                    },
                )
            }

            composable("${Routes.LOG}?date={date}&kind={kind}&exercise={exercise}") { entry ->
                val args = entry.arguments
                val date = runCatching { LocalDate.parse(args?.getString("date")) }
                    .getOrDefault(vm.selectedDate.value)
                val kind = runCatching { Kind.valueOf(args?.getString("kind") ?: Kind.STRENGTH.name) }
                    .getOrDefault(Kind.STRENGTH)
                val exercise = Uri.decode(args?.getString("exercise").orEmpty())
                LogScreen(
                    vm = vm,
                    date = date,
                    initialKind = kind,
                    initialExercise = exercise,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

/** First-run screen: nothing can be logged until there is somewhere to log it to. */
/**
 * Shown while the folder is being read for the first time.
 *
 * Deliberately says which folder work is happening in: a synced folder on a slow
 * provider can take a few seconds, and "reading your folder" explains that, where a bare
 * spinner leaves someone wondering whether the app has lost everything.
 */
@Composable
private fun LoadingGate() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("Uncomplicated", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Reading your folder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 24.dp).size(28.dp),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

/**
 * The screen behind the fingerprint.
 *
 * It prompts by itself when it appears, because an extra tap before the prompt is a tap
 * for nothing. The button is for afterwards: cancelling the system prompt has to leave
 * something to press, or the app is a dead end until it is force-stopped.
 */
@Composable
private fun LockGate(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var message by remember { mutableStateOf<String?>(null) }

    val ask: () -> Unit = {
        val host = activity
        if (host == null) {
            message = "Could not show the unlock prompt."
        } else {
            message = null
            Biometrics.prompt(
                activity = host,
                onSuccess = onUnlocked,
                onFailure = { reason -> message = reason },
            )
        }
    }

    LaunchedEffect(Unit) { ask() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("Uncomplicated", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Locked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Button(onClick = ask, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
            }
        }
    }
}

/** Compose hands out a context; the biometric prompt wants the activity behind it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun FolderGate(onPick: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPick(uri)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(
                    "Uncomplicated",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "A log, not a scoreboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Choose where your data lives",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Everything is stored as plain CSV in a folder you pick — one file per month. " +
                        "Point Syncthing, Dropbox or Nextcloud at that folder and your log is on " +
                        "your laptop too, ready for pandas or a spreadsheet.\n\n" +
                        "Pick an empty folder you control, such as Documents/uncomplicated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = { picker.launch(null) },
                    modifier = Modifier.padding(top = 28.dp),
                ) {
                    Text("Choose data folder")
                }
            }
        }
    }
}
