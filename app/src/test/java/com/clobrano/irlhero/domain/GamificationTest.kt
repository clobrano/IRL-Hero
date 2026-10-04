package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GamificationTest {
    private val today = LocalDate.of(2026, 10, 8) // Thursday

    private fun day(date: LocalDate, irlHours: Long, longestHours: Long = 0) = DaySummary(
        date = date, irlMillis = irlHours * HOUR, wakingElapsedMillis = 16 * HOUR, wakingTotalMillis = 16 * HOUR,
        goalMillis = 8 * HOUR, longestMillis = longestHours * HOUR, sessions = 1, unlocks = 10, glances = 0,
    )

    @Test
    fun `points add minutes, goal bonus and record bonus`() {
        val days = listOf(
            day(today.minusDays(1), irlHours = 4, longestHours = 1),  // 240, sets the bar
            day(today, irlHours = 9, longestHours = 2),               // 540 + 50 goal + 100 best day + 100 longest
        )
        val p = Gamification.progress(days, today)
        assertEquals(240L + 540 + 50 + 100 + 100, p.points)
        assertEquals(HeroLevel.SIDEKICK, p.level)
    }

    @Test
    fun `streak counts goal days and one shield per week`() {
        val days = (6 downTo 0).map { back ->
            val met = back != 2 // one miss on Tuesday, covered by the week's shield
            day(today.minusDays(back.toLong()), if (met) 9 else 1)
        }
        assertEquals(6, Gamification.streak(days, today))
    }

    @Test
    fun `streak breaks on a second miss in the same week`() {
        val days = listOf(
            day(today.minusDays(3), 9), day(today.minusDays(2), 1), day(today.minusDays(1), 1), day(today, 9),
        )
        assertEquals(1, Gamification.streak(days, today))
    }

    @Test
    fun `today not yet met keeps yesterday's streak`() {
        val days = listOf(day(today.minusDays(2), 9), day(today.minusDays(1), 9), day(today, 2))
        assertEquals(2, Gamification.streak(days, today))
    }

    @Test
    fun `share text matches the PRD example shape`() {
        val d = day(today, 9).copy(irlMillis = 9 * HOUR + 20 * MINUTE, longestMillis = 2 * HOUR + 5 * MINUTE, unlocks = 31)
        val text = ShareText.daily(d, isWeekRecordSession = true, streak = 6, level = HeroLevel.HERO)
        assertEquals(
            """
            In Real Life Hero — Thu 8 Oct
            9 h 20 m in real life (58% of my day)
            Longest stretch: 2 h 05 m — new weekly record!
            31 unlocks · Streak: 6 days · Level: Hero
            #InRealLifeHero
            """.trimIndent(),
            text,
        )
    }
}
