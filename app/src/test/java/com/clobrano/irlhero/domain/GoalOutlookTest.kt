package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalOutlookTest {
    // 16 h waking day, 80% goal: 12 h 48 m in real life, 3 h 12 m of phone time allowed.
    private val waking = 16 * HOUR
    private val goal = SessionRules(goalPercent = 80).goalMillis(waking)

    private fun day(phoneMinutes: Long, elapsedHours: Double): DaySummary {
        val elapsed = (elapsedHours * HOUR).toLong()
        return DaySummary(
            date = LocalDate.of(2026, 10, 18),
            irlMillis = elapsed - phoneMinutes * MINUTE,
            wakingElapsedMillis = elapsed,
            wakingTotalMillis = waking,
            goalMillis = goal,
            longestMillis = 0, sessions = 0, unlocks = 0, glances = 0,
        )
    }

    @Test
    fun `phone allowance follows the goal`() {
        assertEquals(3 * HOUR + 12 * MINUTE, day(0, 0.0).phoneAllowanceMillis)
        assertEquals(13 * HOUR, SessionRules(phoneAllowanceMinutes = 180).goalMillis(waking))
    }

    @Test
    fun `a demanding goal does not warn in the morning`() {
        val o = GoalOutlook.of(day(phoneMinutes = 30, elapsedHours = 1.0)) // 08:00
        assertEquals(GoalOutlook.Status.ON_TRACK, o.status)
        assertEquals(2 * HOUR + 42 * MINUTE, o.phoneLeftMillis)
    }

    @Test
    fun `little phone time left in the final stretch is at risk`() {
        // 20:00, 2 h 30 m on the phone: 42 m left.
        val o = GoalOutlook.of(day(phoneMinutes = 150, elapsedHours = 13.0))
        assertEquals(GoalOutlook.Status.AT_RISK, o.status)
        assertEquals(42 * MINUTE, o.worsensInMillis)
    }

    @Test
    fun `plenty of phone time left in the final stretch is on track`() {
        // 20:00, 1 h 30 m on the phone: 1 h 42 m left; at risk after 42 more minutes of use.
        val o = GoalOutlook.of(day(phoneMinutes = 90, elapsedHours = 13.0))
        assertEquals(GoalOutlook.Status.ON_TRACK, o.status)
        assertEquals(42 * MINUTE, o.worsensInMillis)
    }

    @Test
    fun `the final stretch opening is the next check when phone time is already low`() {
        // 17:00, 2 h 30 m on the phone: 42 m left, final stretch from 20:00.
        val o = GoalOutlook.of(day(phoneMinutes = 150, elapsedHours = 10.0))
        assertEquals(GoalOutlook.Status.ON_TRACK, o.status)
        assertEquals(3 * HOUR, o.worsensInMillis)
    }

    @Test
    fun `over the phone allowance is out of reach at any hour`() {
        val o = GoalOutlook.of(day(phoneMinutes = 195, elapsedHours = 7.0)) // 14:00
        assertEquals(GoalOutlook.Status.OUT_OF_REACH, o.status)
        assertEquals(-3 * MINUTE, o.phoneLeftMillis)
        assertNull(o.worsensInMillis)
    }

    @Test
    fun `goal reached is met`() {
        assertEquals(GoalOutlook.Status.MET, GoalOutlook.of(day(phoneMinutes = 60, elapsedHours = 15.0)).status)
    }
}
