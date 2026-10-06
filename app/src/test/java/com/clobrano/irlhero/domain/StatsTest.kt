package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val rules = SessionRules() // sleep 23:00–07:00, goal 50%, min 2 min
    private val day = LocalDate.of(2026, 10, 3)

    private fun at(date: LocalDate, h: Int, m: Int = 0) =
        LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun calc(vararg sessions: Session, now: Long = at(day.plusDays(1), 12)) =
        StatsCalculator(SessionLog(sessions.toList(), sessions.map { it.endMillis }, null), rules, zone, now)

    @Test
    fun `waking hours exclude the sleep window`() {
        val c = calc()
        assertEquals(16 * HOUR, c.wakingIntervals(day).sumOf { it.length })
        assertEquals(8 * HOUR, c.day(day).goalMillis)
    }

    @Test
    fun `sleep time does not count as IRL time`() {
        // 22:00 -> 08:00 next day: only 22–23 and 07–08 count.
        val s = Session(at(day, 22), at(day.plusDays(1), 8), 0)
        val c = calc(s)
        assertEquals(1 * HOUR, c.day(day).irlMillis)
        assertEquals(1 * HOUR, c.day(day.plusDays(1)).irlMillis)
        assertEquals(2 * HOUR, c.irlOf(s))
    }

    @Test
    fun `longest session is attributed to the day it ended with its full IRL length`() {
        val s = Session(at(day, 22), at(day.plusDays(1), 8), 0)
        val c = calc(s)
        assertEquals(0L, c.day(day).longestMillis)
        assertEquals(2 * HOUR, c.day(day.plusDays(1)).longestMillis)
        assertEquals(1, c.day(day.plusDays(1)).sessions)
    }

    @Test
    fun `today counts only waking time elapsed so far`() {
        val now = at(day, 12)
        val c = calc(Session(at(day, 8), at(day, 10), 0), now = now)
        val d = c.day(day)
        assertEquals(5 * HOUR, d.wakingElapsedMillis)
        assertEquals(2 * HOUR, d.irlMillis)
        assertEquals(0.4f, d.irlRatio, 0.001f)
    }

    @Test
    fun `goal met at half of waking hours`() {
        val c = calc(Session(at(day, 8), at(day, 16), 0))
        assertTrue(c.day(day).goalMet)
    }

    @Test
    fun `running session counts toward today until now when asked`() {
        val events = listOf(
            ScreenEvent(EventType.SCREEN_OFF, at(day, 9)),
            ScreenEvent(EventType.KEYGUARD_HIDDEN, at(day, 10)),
            ScreenEvent(EventType.SCREEN_OFF, at(day, 11)), // locked right now
            ScreenEvent(EventType.KEYGUARD_SHOWN, at(day, 11)),
        )
        val now = at(day, 11, 40)
        val card = DashboardBuilder.build(events, rules, zone, now, countOpenSessionUntilNow = true)
        assertEquals(1 * HOUR + 40 * MINUTE, card.today.irlMillis)
        assertEquals(at(day, 11), card.openSessionStart)
        // "Last session" while locked is the one that really ended, not the running one.
        assertEquals(Session(at(day, 9), at(day, 10), 0), card.lastClosedSession)
        // Default: only ended sessions count.
        assertEquals(1 * HOUR, DashboardBuilder.build(events, rules, zone, now).today.irlMillis)
    }

    @Test
    fun `dashboard builds records and week from events`() {
        val events = listOf(
            ScreenEvent(EventType.SCREEN_OFF, at(day, 9)),
            ScreenEvent(EventType.KEYGUARD_HIDDEN, at(day, 11)),
            ScreenEvent(EventType.SCREEN_OFF, at(day, 12)),
            ScreenEvent(EventType.KEYGUARD_HIDDEN, at(day, 12, 30)),
        )
        val d = DashboardBuilder.build(events, rules, zone, at(day, 20))
        assertEquals(2 * HOUR + 30 * MINUTE, d.today.irlMillis)
        assertEquals(2 * HOUR, d.records.longestSession?.irlMillis)
        assertEquals(7, d.weekDays.size)
        assertEquals(31, d.monthDays.size)
        assertEquals(2, d.today.unlocks)
    }
}
