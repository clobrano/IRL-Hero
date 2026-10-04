package com.clobrano.irlhero.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

enum class HeroLevel(val title: String, val minPoints: Long) {
    ROOKIE("Rookie", 0),
    SIDEKICK("Sidekick", 1_000),
    HERO("Hero", 5_000),
    LEGEND("Legend", 20_000),
    MYTH("Myth", 60_000);

    val next: HeroLevel? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forPoints(points: Long): HeroLevel = entries.last { points >= it.minPoints }
    }
}

data class HeroProgress(
    val points: Long,
    val level: HeroLevel,
    /** 0..1 progress towards the next level (1 when at the top level). */
    val levelProgress: Float,
    val streakDays: Int,
)

object Gamification {
    const val GOAL_BONUS = 50L
    const val RECORD_BONUS = 100L

    /**
     * Points: 1 per IRL minute, +50 per goal day, +100 each time the best day or the
     * longest session is beaten (the first day only sets the bar).
     * [days] must be in chronological order.
     */
    fun progress(days: List<DaySummary>, today: LocalDate): HeroProgress {
        var points = 0L
        var bestDay = 0L
        var bestSession = 0L
        for ((i, d) in days.withIndex()) {
            points += d.irlMillis / MINUTE
            if (d.goalMet) points += GOAL_BONUS
            if (d.irlMillis > bestDay) {
                if (i > 0 && bestDay > 0) points += RECORD_BONUS
                bestDay = d.irlMillis
            }
            if (d.longestMillis > bestSession) {
                if (i > 0 && bestSession > 0) points += RECORD_BONUS
                bestSession = d.longestMillis
            }
        }
        val level = HeroLevel.forPoints(points)
        val next = level.next
        val progress = if (next == null) 1f
        else (points - level.minPoints).toFloat() / (next.minPoints - level.minPoints)
        return HeroProgress(points, level, progress, streak(days, today))
    }

    /**
     * Consecutive goal days ending today (if met) or yesterday. One missed day per ISO
     * week is covered by a shield.
     */
    fun streak(days: List<DaySummary>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var count = if (byDate[today]?.goalMet == true) 1 else 0
        val shieldsUsed = mutableSetOf<Pair<Int, Int>>()
        var d = today.minusDays(1)
        while (true) {
            val summary = byDate[d] ?: break
            if (summary.goalMet) {
                count++
            } else {
                val week = d.get(IsoFields.WEEK_BASED_YEAR) to d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                if (count == 0 || week in shieldsUsed) break
                shieldsUsed += week
            }
            d = d.minusDays(1)
        }
        return count
    }
}
