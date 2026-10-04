package com.clobrano.irlhero.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Computes per-day summaries, honouring the sleep window and midnight splits. */
class StatsCalculator(
    private val log: SessionLog,
    private val rules: SessionRules,
    private val zone: ZoneId,
    private val nowMillis: Long,
) {
    private val sessionsByEndDate: Map<LocalDate, List<Session>> =
        log.sessions.groupBy { dateOf(it.endMillis) }

    fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Waking intervals of [date] (the day minus the sleep window). */
    fun wakingIntervals(date: LocalDate): List<Interval> {
        val dayStart = startOfDay(date)
        val dayEnd = startOfDay(date.plusDays(1))
        fun at(minute: Int) = date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
        val s = rules.sleepStartMinute
        val e = rules.sleepEndMinute
        return when {
            s == e -> listOf(Interval(dayStart, dayEnd))
            s > e -> listOf(Interval(at(e), at(s)))                         // sleep crosses midnight
            else -> listOf(Interval(dayStart, at(s)), Interval(at(e), dayEnd)) // e.g. 01:00–09:00
        }.filter { it.length > 0 }
    }

    /** IRL part of [session] that falls on [date]'s waking hours. */
    fun irlOn(session: Session, date: LocalDate): Long {
        val iv = Interval(session.startMillis, session.endMillis)
        return wakingIntervals(date).sumOf { it.overlap(iv) }
    }

    /** Whole IRL length of a session across every day it spans (sleep excluded). */
    fun irlOf(session: Session): Long {
        var d = dateOf(session.startMillis)
        val last = dateOf(session.endMillis)
        var total = 0L
        while (!d.isAfter(last)) {
            total += irlOn(session, d)
            d = d.plusDays(1)
        }
        return total
    }

    /** Sessions overlapping [date], with their IRL time on that day. */
    fun sessionsOn(date: LocalDate): List<ScoredSession> {
        val day = Interval(startOfDay(date), startOfDay(date.plusDays(1)))
        return log.sessions
            .filter { day.overlap(Interval(it.startMillis, it.endMillis)) > 0 }
            .map { ScoredSession(it, irlOn(it, date)) }
    }

    fun day(date: LocalDate): DaySummary {
        val waking = wakingIntervals(date)
        val wakingTotal = waking.sumOf { it.length }
        val elapsedWindow = Interval(Long.MIN_VALUE / 2, nowMillis)
        val wakingElapsed = waking.sumOf { it.overlap(elapsedWindow) }
        val irl = sessionsOn(date).sumOf { it.irlMillis }
        val ended = sessionsByEndDate[date].orEmpty()
        val dayStart = startOfDay(date)
        val dayEnd = startOfDay(date.plusDays(1))
        return DaySummary(
            date = date,
            irlMillis = irl,
            wakingElapsedMillis = wakingElapsed,
            wakingTotalMillis = wakingTotal,
            goalMillis = wakingTotal * rules.goalPercent / 100,
            longestMillis = ended.maxOfOrNull { irlOf(it) } ?: 0L,
            sessions = ended.count { irlOf(it) > 0 },
            unlocks = log.unlocks.count { it in dayStart until dayEnd },
            glances = ended.sumOf { it.glances },
        )
    }

    fun days(from: LocalDate, to: LocalDate): List<DaySummary> {
        val out = mutableListOf<DaySummary>()
        var d = from
        while (!d.isAfter(to)) {
            out += day(d)
            d = d.plusDays(1)
        }
        return out
    }

    /** First day with any data (or today). */
    fun firstDate(today: LocalDate): LocalDate {
        val first = listOfNotNull(
            log.sessions.minOfOrNull { it.startMillis },
            log.unlocks.minOrNull(),
        ).minOrNull() ?: return today
        return dateOf(first)
    }

    companion object {
        fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun monthOf(date: LocalDate): YearMonth = YearMonth.from(date)
    }
}
