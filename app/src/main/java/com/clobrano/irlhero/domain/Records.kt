package com.clobrano.irlhero.domain

import java.time.LocalDate
import java.time.YearMonth

data class SessionRecord(val irlMillis: Long, val endedOn: LocalDate)
data class DayRecord(val irlMillis: Long, val date: LocalDate)
data class WeekRecord(val irlMillis: Long, val weekStart: LocalDate)
data class MonthRecord(val irlMillis: Long, val month: YearMonth)

data class Records(
    val longestSession: SessionRecord?,
    val bestDay: DayRecord?,
    val bestWeek: WeekRecord?,
    val bestMonth: MonthRecord?,
    val fewestUnlocks: DayRecord?,
    val weekLongestSession: SessionRecord?,
    val weekBestDay: DayRecord?,
    val monthLongestSession: SessionRecord?,
    val monthBestDay: DayRecord?,
)

object RecordsCalculator {

    /** Days with at least this much waking time tracked qualify for "fewest unlocks". */
    private const val FEWEST_UNLOCKS_MIN_TRACKED = 12 * HOUR

    fun compute(calc: StatsCalculator, log: SessionLog, days: List<DaySummary>, today: LocalDate): Records {
        val sessions = log.sessions.map { SessionRecord(calc.irlOf(it), calc.dateOf(it.endMillis)) }
            .filter { it.irlMillis > 0 }
        val dayRecs = days.filter { it.irlMillis > 0 }.map { DayRecord(it.irlMillis, it.date) }

        val weekStart = StatsCalculator.weekStart(today)
        val month = YearMonth.from(today)

        val weeks = days.groupBy { StatsCalculator.weekStart(it.date) }
            .map { (start, ds) -> WeekRecord(ds.sumOf { it.irlMillis }, start) }
            .filter { it.irlMillis > 0 }
        val months = days.groupBy { YearMonth.from(it.date) }
            .map { (m, ds) -> MonthRecord(ds.sumOf { it.irlMillis }, m) }
            .filter { it.irlMillis > 0 }

        val fewest = days
            .filter { it.date.isBefore(today) && it.wakingElapsedMillis >= FEWEST_UNLOCKS_MIN_TRACKED && it.irlMillis > 0 }
            .minByOrNull { it.unlocks }
            ?.let { DayRecord(it.unlocks.toLong(), it.date) }

        return Records(
            longestSession = sessions.maxByOrNull { it.irlMillis },
            bestDay = dayRecs.maxByOrNull { it.irlMillis },
            bestWeek = weeks.maxByOrNull { it.irlMillis },
            bestMonth = months.maxByOrNull { it.irlMillis },
            fewestUnlocks = fewest,
            weekLongestSession = sessions.filter { !it.endedOn.isBefore(weekStart) }.maxByOrNull { it.irlMillis },
            weekBestDay = dayRecs.filter { !it.date.isBefore(weekStart) }.maxByOrNull { it.irlMillis },
            monthLongestSession = sessions.filter { YearMonth.from(it.endedOn) == month }.maxByOrNull { it.irlMillis },
            monthBestDay = dayRecs.filter { YearMonth.from(it.date) == month }.maxByOrNull { it.irlMillis },
        )
    }
}
