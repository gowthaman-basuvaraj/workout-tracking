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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gowthaman.workout.AppViewModel
import com.gowthaman.workout.data.agoLabel
import com.gowthaman.workout.data.monthLabel
import com.gowthaman.workout.data.shortLabel
import com.gowthaman.workout.data.tinyLabel
import com.gowthaman.workout.data.weekdayLabel
import com.gowthaman.workout.data.FoodScale
import com.gowthaman.workout.data.SleepQuality
import com.gowthaman.workout.data.formatSleep
import com.gowthaman.workout.data.Period
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.logic.Bucket
import com.gowthaman.workout.logic.BucketUnit
import com.gowthaman.workout.logic.Dashboard
import com.gowthaman.workout.logic.Stats
import com.gowthaman.workout.logic.Summary
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onOpenExercise: (String) -> Unit,
) {
    val data by vm.dashboard.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unit = settings.weightUnit
    val period = data.period
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 2.dp),
                )
                if (data.hasData) {
                    SmallIconButton(Icons.Filled.Share, "Share this summary") {
                        shareSummary(context, Summary.text(data, settings))
                    }
                }
            }
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

        if (data.hasSleep) {
            item { SleepCard(data = data) }
        }

        if (data.hasStress) {
            item { StressCard(data = data) }
        }

        if (data.hasFood) {
            item { FoodCard(data = data) }
            item { TopFoodsCard(data = data) }
        }

        item {
            TotalsFooter(data = data)
        }
    }
}

/**
 * Hands the summary to the system share sheet as plain text.
 *
 * Text rather than an image on purpose: it pastes into a message, an email or your own
 * notes, and it stays readable by whoever receives it. Nothing is sent anywhere until the
 * share sheet is used, and the destination is always the reader's choice.
 */
private fun shareSummary(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share summary"))
}

@Composable
private fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    // Wraps rather than scrolls: a period hidden off the right edge is a period you
    // never discover.
    ChipFlow {
        Period.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = { Text(period.label, maxLines = 1) },
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

/**
 * Sleep as it was, not as a score.
 *
 * Coverage is stated next to the average on purpose: an average over 11 logged nights in
 * a 30-day window is a different claim from an average over 29, and hiding that would be
 * the most misleading number on the page.
 */
@Composable
private fun SleepCard(data: Dashboard) {
    SectionCard(title = "sleep · ${data.period.windowLabel}") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatSleep(data.sleepAvgMin),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "average over ${data.nightsLogged} night${if (data.nightsLogged == 1) "" else "s"} logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (data.sleepBuckets.any { it.volume > 0 }) {
            Spacer(Modifier.height(12.dp))
            BarChart(
                bars = data.sleepBuckets.mapIndexed { i, bucket ->
                    Bar(
                        label = bucket.axisLabel(),
                        value = (bucket.volume / 60.0).toFloat(),
                        highlight = i == data.sleepBuckets.lastIndex,
                    )
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Average hours per night in each bar; empty where nothing was logged.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        QualityMixBar(mix = data.sleepQualityMix, total = data.nightsLogged)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    data.shortNights.toString(),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "nights under 6h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${data.nightsLogged} / ${data.period.days}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "nights logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QualityMixBar(mix: List<Pair<SleepQuality, Int>>, total: Int) {
    if (total <= 0) return
    val shown = mix.filter { it.second > 0 }
    if (shown.isEmpty()) return

    val percents = shown.mapIndexed { i, (_, count) ->
        if (i < shown.lastIndex) (count * 100.0 / total).roundToInt() else -1
    }.toMutableList()
    percents[percents.lastIndex] = 100 - percents.dropLast(1).sum()

    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        shown.forEach { (quality, count) ->
            Box(
                Modifier
                    .weight(count.toFloat())
                    .fillMaxHeight()
                    .background(qualityAccent(quality))
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.forEachIndexed { i, (quality, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(qualityAccent(quality))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${quality.label} ${percents[i]}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Busters first, deliberately.
 *
 * "14 deadline events" tells you work is stressful, which you knew. What you reached for,
 * and whether you reached for anything, is the part you actually control — so it leads,
 * and there is no enhancer-to-buster ratio anywhere: you log stressors when you are least
 * able to log, so that number would claim a precision the data has not got.
 */
@Composable
private fun StressCard(data: Dashboard) {
    SectionCard(title = "stress · ${data.period.windowLabel}") {
        Text(
            "BUSTERS YOU REACHED FOR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (data.busters.isEmpty()) {
            Text(
                "Nothing logged on that side yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            data.busters.forEach { TallyRow(it) }
            Spacer(Modifier.height(6.dp))
            Text(
                "on ${data.daysWithBuster} of the last ${data.period.days} days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (data.enhancers.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "WHAT CAME UP",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            data.enhancers.forEach { TallyRow(it) }
        }
    }
}

@Composable
private fun TallyRow(tally: com.gowthaman.workout.logic.StressTally) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tally.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (tally.major > 0) {
            Text(
                "${tally.major} major",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
        }
        NumberText(tally.count.toString())
    }
}

/**
 * Counts, plainly stated. No targets and no warnings — "103 coffees" is the whole message,
 * and the reader supplies the reaction.
 */
@Composable
private fun FoodCard(data: Dashboard) {
    val period = data.period
    SectionCard(title = "food · ${period.windowLabel}") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                data.foodCount.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "things logged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (data.foodByPurpose.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                data.foodByPurpose.joinToString("  ·  ") { (purpose, count) ->
                    "$count ${purpose.count(count)}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(14.dp))
        ScaleMixBar(mix = data.foodScaleMix, total = data.foodCount)

        if (data.foodTagMix.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                data.foodTagMix.joinToString("  ·  ") { (tag, count) -> "${tag.label} $count" },
                style = MaterialTheme.typography.bodyMedium,
            )
            // Not a bar: tags overlap and are optional, so a proportional split would be
            // a lie. The coverage says what the counts are out of.
            Text(
                "tagged ${data.foodTaggedCount} of ${data.foodCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    data.cleanDayStreak.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (data.cleanDayStreak > 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "consecutive clean days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            data.lateNightSharePct?.let { pct ->
                Column(Modifier.weight(1f)) {
                    Text("$pct%", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "logged after 21:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The healthy / everyday / indulgent split as one proportional bar. */
@Composable
private fun ScaleMixBar(mix: List<Pair<FoodScale, Int>>, total: Int) {
    if (total <= 0) return
    val shown = mix.filter { it.second > 0 }
    if (shown.isEmpty()) return

    // Round every slice but the last, then give the last the remainder, so the labels
    // always add up to 100% instead of to 99.
    val percents = shown.mapIndexed { i, (_, count) ->
        if (i < shown.lastIndex) (count * 100.0 / total).roundToInt() else -1
    }.toMutableList()
    percents[percents.lastIndex] = 100 - percents.dropLast(1).sum()

    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        shown.forEach { (scale, count) ->
            Box(
                Modifier
                    .weight(count.toFloat())
                    .fillMaxHeight()
                    .background(scaleAccent(scale))
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        shown.forEachIndexed { i, (scale, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scaleAccent(scale))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${scale.label} ${percents[i]}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopFoodsCard(data: Dashboard) {
    if (data.topFoods.isEmpty()) return
    SectionCard(title = "most logged food · ${data.period.windowLabel}") {
        val max = data.topFoods.maxOf { it.second }
        data.topFoods.forEach { (name, count) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
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
