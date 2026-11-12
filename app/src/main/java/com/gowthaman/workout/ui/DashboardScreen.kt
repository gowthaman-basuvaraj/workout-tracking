package com.gowthaman.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.logic.Dashboard
import com.gowthaman.workout.logic.Stats
import java.time.LocalDate

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenExercise: (String) -> Unit,
) {
    val data by vm.dashboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    if (!data.hasData) {
        EmptyState(
            title = "Nothing to show yet",
            body = "Log a few sessions and this page fills up: weekly volume, streaks, recent " +
                "records, and which lifts are due for an overload.",
        )
        return
    }

    val unit = settings.weightUnit

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Dashboard",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = data.currentStreakDays.toString(),
                    label = if (data.currentStreakDays == 1) "day streak" else "days streak",
                    sub = if (data.currentStreakDays == 0) "train today to start one" else "keep going",
                    modifier = Modifier.weight(1f),
                    accent = if (data.currentStreakDays > 0) MaterialTheme.colorScheme.tertiary else null,
                )
                StatTile(
                    value = data.trainingDaysThisWeek.toString(),
                    label = "days this week",
                    sub = "${data.trainingDaysLast30} in last 30",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = data.setsThisWeek.toString(),
                    label = "sets this week",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (data.volumeThisWeek > 0) data.volumeThisWeek.fmt(0) else "—",
                    label = "volume this week ($unit)",
                    sub = data.volumeTrendPct?.let { pct ->
                        if (pct >= 0) "+$pct% vs last week" else "$pct% vs last week"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (data.weeks.any { it.volume > 0 }) {
            item {
                SectionCard(title = "weekly volume ($unit)") {
                    BarChart(
                        bars = data.weeks.mapIndexed { i, week ->
                            Bar(
                                label = week.start.tinyLabel(),
                                value = week.volume.toFloat(),
                                highlight = i == data.weeks.lastIndex,
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Last 8 weeks, Monday-start. Bodyweight sets only count once you set " +
                            "your bodyweight in Settings.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (data.overloadDue.isNotEmpty()) {
            item {
                SectionCard(title = "overload due (${settings.overloadLabel.lowercase()})") {
                    data.overloadDue.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExercise(item.exercise) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.exercise,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "no step up in ${item.daysSinceProgress} days  ·  " +
                                        "last done ${item.lastDone.agoLabel()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            NumberText(
                                item.suggestion,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }

        if (data.recentPrs.isNotEmpty()) {
            item {
                SectionCard(title = "records in the last 30 days") {
                    data.recentPrs.forEach { pr ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExercise(pr.exercise) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pr.exercise,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    pr.date.shortLabel() + "  ·  " + pr.date.agoLabel(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                NumberText(pr.label, color = MaterialTheme.colorScheme.primary)
                                pr.e1rm?.let {
                                    Text(
                                        "est. 1RM ${it.fmt(1)} $unit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (data.cardioSessionsThisWeek > 0) {
            item {
                SectionCard(title = "cardio this week") {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.cardioSessionsThisWeek.toString(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "sessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatDurationLong(data.cardioSecThisWeek),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (data.cardioDistanceThisWeek > 0)
                                    data.cardioDistanceThisWeek.fmt(1) else "—",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                settings.distanceUnit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (data.topExercises.isNotEmpty()) {
            item {
                SectionCard(title = "most logged") {
                    val max = data.topExercises.maxOf { it.second }
                    data.topExercises.forEach { (name, count) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExercise(name) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            // A proportional bar reads faster than the bare number.
                            Box(
                                Modifier
                                    .width(64.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(count / max.toFloat())
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            NumberText("$count")
                        }
                    }
                }
            }
        }

        item {
            TotalsFooter(data = data)
        }
    }
}

@Composable
private fun TotalsFooter(data: Dashboard) {
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            buildList {
                add("${data.totalTrainingDays} training days")
                add("${data.totalSets} sets")
                add("${data.exerciseCount} exercises")
                data.firstDay?.let { add("since ${it.shortLabel()}") }
            }.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val weeksTracked = data.firstDay?.let {
            java.time.temporal.ChronoUnit.WEEKS.between(Stats.weekStart(it), LocalDate.now()) + 1
        } ?: 0
        if (weeksTracked > 1) {
            Text(
                "Averaging ${"%.1f".format(data.totalTrainingDays / weeksTracked.toDouble())} " +
                    "sessions per week over $weeksTracked weeks.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
