package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class CelebrationsTest {
    private val zone = ZoneOffset.UTC
    private val rules = SessionRules() // sleep 23:00–07:00, goal 8 h

    private fun at(date: LocalDate, h: Int, m: Int = 0) =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    /** One IRL session per day, from 09:00 for [hours]. */
    private fun events(days: Map<LocalDate, Int>) = days.flatMap { (date, hours) ->
        listOf(
            ScreenEvent(EventType.SCREEN_OFF, at(date, 9)),
            ScreenEvent(EventType.KEYGUARD_HIDDEN, at(date, 9 + hours)),
        )
    }

    private val oct = (1..5).associate { LocalDate.of(2026, 10, it) to 2 } + (LocalDate.of(2026, 10, 6) to 5)
    private val start = LocalDate.of(2026, 10, 1)

    private fun check(now: Long, judged: JudgedPeriods = JudgedPeriods(day = "2026-10-04"), pointsFrom: LocalDate = start, days: Map<LocalDate, Int> = oct) =
        Celebrations.check(DashboardBuilder.build(events(days), rules, zone, now), judged, pointsFrom)

    @Test
    fun `a record day is not celebrated while the day is still running`() {
        val c = check(now = at(LocalDate.of(2026, 10, 6), 15))
        assertEquals(emptyList<Celebration>(), c.celebrations)
        assertEquals("2026-10-05", c.judged.day) // yesterday judged: no record
    }

    @Test
    fun `a record day is celebrated once its waking hours are over, and only once`() {
        val now = at(LocalDate.of(2026, 10, 6), 23, 30)
        val c = check(now)
        assertEquals(listOf("Record day"), c.celebrations.map { it.title })
        assertEquals("2026-10-06", c.judged.day)
        assertEquals(emptyList<Celebration>(), check(now + HOUR, judged = c.judged).celebrations)
    }

    @Test
    fun `yesterday's record shows on the first open of the next day`() {
        val c = check(now = at(LocalDate.of(2026, 10, 7), 10), judged = JudgedPeriods(day = "2026-10-05"))
        assertEquals(listOf("Record day"), c.celebrations.map { it.title })
        assertTrue(c.celebrations.single().detail.startsWith("Tue 6 Oct"))
    }

    @Test
    fun `days before the user started are judged silently`() {
        val c = check(now = at(LocalDate.of(2026, 10, 7), 10), pointsFrom = LocalDate.of(2026, 10, 7))
        assertEquals(emptyList<Celebration>(), c.celebrations)
        assertEquals("2026-10-06", c.judged.day)
    }

    @Test
    fun `goal day without a record gets a goal celebration`() {
        val days = (1..5).associate { LocalDate.of(2026, 10, it) to 9 } + (LocalDate.of(2026, 10, 6) to 9)
        val c = check(now = at(LocalDate.of(2026, 10, 7), 10), judged = JudgedPeriods(day = "2026-10-05"), days = days)
        assertEquals(listOf("Daily goal reached"), c.celebrations.map { it.title })
    }

    @Test
    fun `record month shows on the first open of the new month, once`() {
        val aug = (1..31).associate { LocalDate.of(2026, 8, it) to 1 }
        val sep = (1..30).associate { LocalDate.of(2026, 9, it) to 2 }
        val judged = JudgedPeriods(day = "2026-09-29", week = "2026-09-21", month = "2026-08")
        val inSeptember = check(now = at(LocalDate.of(2026, 9, 30), 12), judged = judged, pointsFrom = LocalDate.of(2026, 8, 1), days = aug + sep)
        assertTrue(inSeptember.celebrations.none { it.title == "Record month" })

        val now = at(LocalDate.of(2026, 10, 1), 10)
        val c = check(now, judged = judged, pointsFrom = LocalDate.of(2026, 8, 1), days = aug + sep)
        assertTrue(c.celebrations.any { it.title == "Record month" })
        assertEquals("2026-09", c.judged.month)
        val again = check(now + HOUR, judged = c.judged, pointsFrom = LocalDate.of(2026, 8, 1), days = aug + sep)
        assertEquals(emptyList<Celebration>(), again.celebrations)
    }
}
