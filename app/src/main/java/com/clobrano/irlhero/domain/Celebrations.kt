package com.clobrano.irlhero.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** A reason to celebrate, shown once when the app is opened. */
data class Celebration(val title: String, val value: String, val detail: String, val shareTitle: String)

/** The latest day, week and month already judged (ISO date, week-start date, "YYYY-MM"). */
data class JudgedPeriods(val day: String = "", val week: String = "", val month: String = "")

data class CelebrationCheck(val celebrations: List<Celebration>, val judged: JudgedPeriods)

/**
 * Celebrations are for finished periods only, and each period is judged once:
 * - a day is over when its waking hours end (the sleep window starts), so its celebration
 *   shows the first time the app is opened after that: the same night or the next day;
 * - a week or month is over when its last day is.
 * Only the most recent finished day, week and month are judged; older unjudged ones are
 * skipped. Periods that ended before [pointsFrom] (history imported at install) are never
 * celebrated, and a record needs at least one earlier, fully tracked period to beat.
 */
object Celebrations {

    fun check(d: Dashboard, judged: JudgedPeriods, pointsFrom: LocalDate): CelebrationCheck {
        val days = d.allDays
        if (days.isEmpty()) return CelebrationCheck(emptyList(), judged)
        val byDate = days.associateBy { it.date }
        val firstTracked = days.first().date
        val today = d.today.date
        val lastDay = if (d.nowMillis >= wakingEnd(d, today)) today else today.minusDays(1)
        val out = mutableListOf<Celebration>()
        var result = judged

        // Day
        if (lastDay.toString() > judged.day) {
            result = result.copy(day = lastDay.toString())
            val day = byDate[lastDay]
            if (day != null && !lastDay.isBefore(pointsFrom)) dayCelebration(day, days)?.let { out += it }
        }

        // Week (Monday to Sunday)
        val week = if (lastDay.dayOfWeek == DayOfWeek.SUNDAY) StatsCalculator.weekStart(lastDay)
        else StatsCalculator.weekStart(lastDay).minusWeeks(1)
        if (week.toString() > judged.week) {
            result = result.copy(week = week.toString())
            if (!week.plusDays(6).isBefore(pointsFrom)) {
                val totals = days.groupBy { StatsCalculator.weekStart(it.date) }
                    .mapValues { (_, ds) -> ds.sumOf { it.irlMillis } }
                val total = totals[week] ?: 0
                val previousBest = totals.filterKeys { it.isBefore(week) && !it.isBefore(firstTracked) }.values.maxOrNull()
                if (previousBest != null && previousBest > 0 && total > previousBest) {
                    out += Celebration("Record week", Format.duration(total), "Week of ${Format.day(week)}", "Best week")
                }
            }
        }

        // Month
        val month = if (lastDay == YearMonth.from(lastDay).atEndOfMonth()) YearMonth.from(lastDay)
        else YearMonth.from(lastDay).minusMonths(1)
        if (month.toString() > judged.month) {
            result = result.copy(month = month.toString())
            if (!month.atEndOfMonth().isBefore(pointsFrom)) {
                val totals = days.groupBy { YearMonth.from(it.date) }
                    .mapValues { (_, ds) -> ds.sumOf { it.irlMillis } }
                val total = totals[month] ?: 0
                val previousBest = totals
                    .filterKeys { it.isBefore(month) && !it.atDay(1).isBefore(firstTracked) }.values.maxOrNull()
                if (previousBest != null && previousBest > 0 && total > previousBest) {
                    out += Celebration("Record month", Format.duration(total), Format.month(month), "Best month")
                }
            }
        }
        return CelebrationCheck(out, result)
    }

    private fun dayCelebration(day: DaySummary, days: List<DaySummary>): Celebration? {
        val before = days.filter { it.date.isBefore(day.date) }
        val bestBefore = before.maxOfOrNull { it.irlMillis } ?: 0
        val longestBefore = before.maxOfOrNull { it.longestMillis } ?: 0
        val bestDay = bestBefore > 0 && day.irlMillis > bestBefore
        val longest = longestBefore > 0 && day.longestMillis > longestBefore
        if (!bestDay && !longest && !day.goalMet) return null
        val parts = buildList {
            if (bestDay) add("best day ever")
            if (longest) add("longest session ever, ${Format.duration(day.longestMillis)}")
            if (day.goalMet) add("daily goal reached")
        }
        val title = if (bestDay || longest) "Record day" else "Daily goal reached"
        val detail = "${Format.day(day.date)} · " + parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
        return Celebration(title, Format.duration(day.irlMillis), detail, if (bestDay || longest) "Record day" else "Daily goal")
    }

    /** When [date]'s waking hours end (the sleep window starts), or midnight without one. */
    private fun wakingEnd(d: Dashboard, date: LocalDate): Long =
        d.calc.wakingIntervals(date).maxOfOrNull { it.end } ?: d.calc.startOfDay(date.plusDays(1))
}
