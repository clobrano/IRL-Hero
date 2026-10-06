package com.clobrano.irlhero.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Everything the screens need, computed in one pass from the raw events. */
data class Dashboard(
    val today: DaySummary,
    val todayStartMillis: Long,
    val todayEndMillis: Long,
    val nowMillis: Long,
    val todayWaking: List<Interval>,
    val todaySessions: List<ScoredSession>,
    val todayUnlocks: List<Long>,
    val lastSession: ScoredSession?,
    val weekStart: LocalDate,
    val weekDays: List<DaySummary>,
    val month: YearMonth,
    val monthDays: List<DaySummary>,
    val allDays: List<DaySummary>,
    val records: Records,
    val hero: HeroProgress,
    val sessions: List<Session>,
    val calc: StatsCalculator,
) {
    val isWeekRecordSession: Boolean
        get() = today.longestMillis > 0 && records.weekLongestSession?.let {
            it.endedOn == today.date && it.irlMillis == today.longestMillis
        } == true
}

object DashboardBuilder {
    /** History older than this is not scored (and is pruned from storage). */
    const val MAX_HISTORY_DAYS = 400L

    /** [pointsFrom]: first day that earns Hero Points (the day the user started using the app). */
    fun build(
        events: List<ScreenEvent>,
        rules: SessionRules,
        zone: ZoneId,
        nowMillis: Long,
        pointsFrom: LocalDate? = null,
        phoneUnlocked: Boolean = false,
    ): Dashboard {
        val log = SessionBuilder.build(events, rules, closeOpenAt = nowMillis.takeIf { phoneUnlocked })
        val calc = StatsCalculator(log, rules, zone, nowMillis)
        val today = calc.dateOf(nowMillis)
        val first = maxOf(calc.firstDate(today), today.minusDays(MAX_HISTORY_DAYS))
        val weekStart = StatsCalculator.weekStart(today)
        val month = YearMonth.from(today)
        val from = minOf(first, weekStart, month.atDay(1))
        val all = calc.days(from, month.atEndOfMonth().let { if (it.isBefore(weekStart.plusDays(6))) weekStart.plusDays(6) else it })
        val byDate = all.associateBy { it.date }
        val history = all.filter { !it.date.isBefore(first) && !it.date.isAfter(today) }
        val dayStart = calc.startOfDay(today)
        val dayEnd = calc.startOfDay(today.plusDays(1))
        val last = log.sessions.maxByOrNull { it.endMillis }
        return Dashboard(
            today = byDate.getValue(today),
            todayStartMillis = dayStart,
            todayEndMillis = dayEnd,
            nowMillis = nowMillis,
            todayWaking = calc.wakingIntervals(today),
            todaySessions = calc.sessionsOn(today),
            todayUnlocks = log.unlocks.filter { it in dayStart until dayEnd },
            lastSession = last?.let { ScoredSession(it, calc.irlOf(it)) },
            weekStart = weekStart,
            weekDays = (0L..6L).map { byDate.getValue(weekStart.plusDays(it)) },
            month = month,
            monthDays = (1..month.lengthOfMonth()).map { byDate.getValue(month.atDay(it)) },
            allDays = history,
            records = RecordsCalculator.compute(calc, log, history, today),
            hero = Gamification.progress(history, today, pointsFrom),
            sessions = log.sessions,
            calc = calc,
        )
    }
}
