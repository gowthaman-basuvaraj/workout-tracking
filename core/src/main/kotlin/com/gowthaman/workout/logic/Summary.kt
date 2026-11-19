package com.gowthaman.workout.logic

import com.gowthaman.workout.data.Settings
import com.gowthaman.workout.data.fmt
import com.gowthaman.workout.data.formatDurationLong
import com.gowthaman.workout.data.formatSleep

/**
 * The dashboard as shareable plain text.
 *
 * Lives in :core so the phone's share sheet and `unc summary` produce the same thing,
 * and so it can be tested without a screen.
 *
 * Same discipline as the dashboard itself: counts, no scores, no commentary, and sections
 * simply absent when there is nothing in them. Nothing here should read as a brag or a
 * telling-off if it lands in someone else's inbox.
 */
object Summary {

    fun text(data: Dashboard, settings: Settings): String {
        if (!data.hasData) return "Uncomplicated - nothing logged yet."

        val lines = mutableListOf<String>()
        lines += "Uncomplicated - ${data.period.windowLabel}"

        training(data, settings)?.let { lines += ""; lines += it }
        sleep(data)?.let { lines += ""; lines += it }
        food(data)?.let { lines += ""; lines += it }
        stress(data)?.let { lines += ""; lines += it }

        return lines.joinToString("\n")
    }

    private fun training(data: Dashboard, settings: Settings): String? {
        if (data.setsThisPeriod == 0 && data.cardioSessions == 0) return null
        val out = mutableListOf("Training")
        if (data.setsThisPeriod > 0) {
            out += "  ${data.trainingDaysThisPeriod} days trained, ${data.setsThisPeriod} sets"
            if (data.volumeThisPeriod > 0) {
                val trend = data.volumeTrendPct
                    ?.let { if (it >= 0) " (+$it% vs previous ${data.period.days}d)" else " ($it% vs previous ${data.period.days}d)" }
                    .orEmpty()
                out += "  ${data.volumeThisPeriod.fmt(0)} ${settings.weightUnit} volume$trend"
            }
        }
        if (data.cardioSessions > 0) {
            val distance = if (data.cardioDistance > 0)
                ", ${data.cardioDistance.fmt(1)} ${settings.distanceUnit}" else ""
            out += "  cardio: ${data.cardioSessions} sessions, " +
                "${formatDurationLong(data.cardioSec)}$distance"
        }
        if (data.currentStreakDays > 0) out += "  ${data.currentStreakDays} day streak"
        return out.joinToString("\n")
    }

    private fun sleep(data: Dashboard): String? {
        if (!data.hasSleep) return null
        val out = mutableListOf("Sleep")
        // Coverage always travels with the average, so the figure cannot be read as more
        // than it is.
        out += "  ${formatSleep(data.sleepAvgMin)} average over ${data.nightsLogged} nights logged"
        out += "  ${data.shortNights} nights under 6h"
        percentages(data.sleepQualityMix.map { it.first.label to it.second }, data.nightsLogged)
            ?.let { out += "  $it" }
        return out.joinToString("\n")
    }

    private fun food(data: Dashboard): String? {
        if (!data.hasFood) return null
        val out = mutableListOf("Food")
        val byPurpose = data.foodByPurpose.take(3)
            .joinToString(", ") { (purpose, count) -> "$count ${purpose.count(count)}" }
        out += "  ${data.foodCount} logged" + if (byPurpose.isNotEmpty()) " - $byPurpose" else ""
        percentages(data.foodScaleMix.map { it.first.label to it.second }, data.foodCount)
            ?.let { out += "  $it" }
        data.topFoods.firstOrNull()?.let { (name, count) -> out += "  most logged: $name $count" }
        return out.joinToString("\n")
    }

    private fun stress(data: Dashboard): String? {
        if (!data.hasStress) return null
        val out = mutableListOf("Stress")
        if (data.busters.isNotEmpty()) {
            out += "  busters on ${data.daysWithBuster} of ${data.period.days} days - " +
                data.busters.take(3).joinToString(", ") { "${it.name} ${it.count}" }
        }
        if (data.enhancers.isNotEmpty()) {
            out += "  came up: " + data.enhancers.take(3).joinToString(", ") { "${it.name} ${it.count}" }
        }
        return out.joinToString("\n")
    }

    /** "poor 30% / ok 41% / good 29%", with the last share taking the remainder. */
    private fun percentages(parts: List<Pair<String, Int>>, total: Int): String? {
        if (total <= 0) return null
        val shown = parts.filter { it.second > 0 }
        if (shown.size < 2) return null
        val percents = shown.mapIndexed { i, (_, count) ->
            if (i < shown.lastIndex) Math.round(count * 100.0 / total).toInt() else -1
        }.toMutableList()
        percents[percents.lastIndex] = 100 - percents.dropLast(1).sum()
        return shown.mapIndexed { i, (label, _) -> "${label.lowercase()} ${percents[i]}%" }
            .joinToString(" / ")
    }
}
