package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalOutlookTest {
    // 16 h waking day, 8 h goal.
    private fun day(irlHours: Double, elapsedHours: Double) = DaySummary(
        date = LocalDate.of(2026, 10, 17),
        irlMillis = (irlHours * HOUR).toLong(),
        wakingElapsedMillis = (elapsedHours * HOUR).toLong(),
        wakingTotalMillis = 16 * HOUR,
        goalMillis = 8 * HOUR,
        longestMillis = 0, sessions = 0, unlocks = 0, glances = 0,
    )

    @Test
    fun `plenty of time left is on track`() {
        val o = GoalOutlook.of(day(irlHours = 2.0, elapsedHours = 4.0)) // 6 h needed of 12 h left
        assertEquals(GoalOutlook.Status.ON_TRACK, o.status)
        // At risk once 6 h needed exceeds 75% of what's left: after 4 more hours of phone use.
        assertEquals(4 * HOUR, o.worsensInMillis)
    }

    @Test
    fun `needing most of the time left is at risk`() {
        val o = GoalOutlook.of(day(irlHours = 4.0, elapsedHours = 11.0)) // 4 h needed of 5 h left
        assertEquals(GoalOutlook.Status.AT_RISK, o.status)
        assertEquals(1 * HOUR, o.worsensInMillis)
    }

    @Test
    fun `needing more than the time left is out of reach`() {
        val o = GoalOutlook.of(day(irlHours = 4.0, elapsedHours = 13.0)) // 4 h needed of 3 h left
        assertEquals(GoalOutlook.Status.OUT_OF_REACH, o.status)
        assertNull(o.worsensInMillis)
    }

    @Test
    fun `goal reached is met`() {
        assertEquals(GoalOutlook.Status.MET, GoalOutlook.of(day(irlHours = 8.5, elapsedHours = 13.0)).status)
    }
}
