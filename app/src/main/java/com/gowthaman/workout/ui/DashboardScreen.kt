package com.gowthaman.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.logic.Bucket
import com.gowthaman.workout.logic.BucketUnit
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
    val unit = settings.weightUnit
    val period = data.period

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
            PeriodSelector(selected = period, onSelect = vm::setDashboardPeriod)
        }

        if (!data.hasData) {
            item {
                EmptyState(
                    title = "Nothing to show yet",
                    body = "Log a few sessions and this page fills up: training volume, " +
                        "streaks, recent records, and which lifts are due for an overload.",
                )
            }
            return@LazyColumn
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
                    value = data.trainingDaysThisPeriod.toString(),
                    label = "days trained",
                    sub = "of the ${period.windowLabel}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = data.setsThisPeriod.toString(),
                    label = "sets",
                    sub = perWeekLabel(data.setsThisPeriod, period),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (data.volumeThisPeriod > 0) data.volumeThisPeriod.fmt(0) else "—",
                    label = "volume ($unit)",
                    sub = data.volumeTrendPct?.let { pct ->
                        (if (pct >= 0) "+$pct%" else "$pct%") + " vs previous ${period.days}d"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (data.buckets.any { it.volume > 0 }) {
            item {
                SectionCard(title = "volume ($unit)") {
                    BarChart(
                        bars = data.buckets.mapIndexed { i, bucket ->
                            Bar(
                                label = bucket.axisLabel(),
                                value = bucket.volume.toFloat(),
                                highlight = i == data.buckets.lastIndex,
                            )
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        chartCaption(period),
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
                SectionCard(title = "records in the ${period.windowLabel}") {
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

        if (data.cardioSessions > 0) {
            item {
                SectionCard(title = "cardio · ${period.windowLabel}") {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(
                                data.cardioSessions.toString(),
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
                                formatDurationLong(data.cardioSec),
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
                                if (data.cardioDistance > 0) data.cardioDistance.fmt(1) else "—",
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
                SectionCard(title = "most logged · ${period.windowLabel}") {
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
private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    // Scrolls rather than wraps, so a large system font never stacks a chip's label one
    // letter per line the way a fixed Row does.
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Period.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.label) },
            )
        }
    }
}

/** Axis text for one bar, which depends on how wide that bar is. */
private fun Bucket.axisLabel(): String = when (unit) {
    BucketUnit.DAY -> start.weekdayLabel()
    BucketUnit.WEEK -> start.tinyLabel()
    BucketUnit.MONTH -> start.monthLabel()
}

private fun chartCaption(period: Period): String {
    val (unit, count) = Stats.chartShape(period)
    val span = when (unit) {
        BucketUnit.DAY -> "Last $count days, one bar per day."
        BucketUnit.WEEK -> "Last $count weeks, Monday-start."
        BucketUnit.MONTH -> "Last $count calendar months."
    }
    return "$span Bodyweight sets only count once you set your bodyweight in Settings."
}

/** Context for a raw set count: the same number means very different things over a year. */
private fun perWeekLabel(sets: Int, period: Period): String? {
    if (sets == 0 || period == Period.WEEK) return null
    val perWeek = sets * 7.0 / period.days
    return "~${perWeek.fmt(1)} per week"
}

@Composable
private fun TotalsFooter(data: Dashboard) {
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Text(
            "ALL TIME",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
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
                "Averaging ${(data.totalTrainingDays / weeksTracked.toDouble()).fmt(1)} " +
                    "sessions per week over $weeksTracked weeks.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
