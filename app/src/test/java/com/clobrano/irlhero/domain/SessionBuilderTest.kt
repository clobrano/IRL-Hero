package com.clobrano.irlhero.domain

import com.clobrano.irlhero.domain.EventType.KEYGUARD_HIDDEN
import com.clobrano.irlhero.domain.EventType.KEYGUARD_SHOWN
import com.clobrano.irlhero.domain.EventType.SCREEN_OFF
import com.clobrano.irlhero.domain.EventType.SCREEN_ON
import com.clobrano.irlhero.domain.EventType.SHUTDOWN
import com.clobrano.irlhero.domain.EventType.STARTUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionBuilderTest {
    private val rules = SessionRules()
    private fun ev(type: EventType, minute: Long, second: Long = 0) = ScreenEvent(type, minute * MINUTE + second * SECOND)

    /** Lock at [off] (screen off + keyguard shown), unlock at [on] (screen on + keyguard hidden). */
    private fun lockUnlock(off: Long, on: Long) = listOf(
        ev(SCREEN_OFF, off), ev(KEYGUARD_SHOWN, off),
        ev(SCREEN_ON, on), ev(KEYGUARD_HIDDEN, on, 1),
    )

    @Test
    fun `session runs from lock to unlock`() {
        val log = SessionBuilder.build(lockUnlock(10, 40), rules)
        assertEquals(listOf(Session(10 * MINUTE, 40 * MINUTE + SECOND, 0)), log.sessions)
        assertEquals(1, log.unlocks.size)
        assertNull(log.openSessionStart)
    }

    @Test
    fun `looking at the lock screen is a glance and does not end the session`() {
        val events = listOf(
            ev(SCREEN_OFF, 0), ev(KEYGUARD_SHOWN, 0),
            ev(SCREEN_ON, 10), ev(SCREEN_OFF, 10, 20),
            ev(SCREEN_ON, 20), ev(SCREEN_OFF, 20, 5),
            ev(SCREEN_ON, 30), ev(KEYGUARD_HIDDEN, 30, 1),
        )
        val log = SessionBuilder.build(events, rules)
        assertEquals(1, log.sessions.size)
        assertEquals(2, log.sessions[0].glances)
        assertEquals(30 * MINUTE + SECOND, log.sessions[0].durationMillis)
    }

    @Test
    fun `screen on with no lock screen counts as an unlock`() {
        // Inside the lock timeout: screen off, back on, no keyguard at all.
        val events = listOf(ev(SCREEN_OFF, 0), ev(SCREEN_ON, 5))
        val log = SessionBuilder.build(events, rules)
        assertEquals(listOf(Session(0, 5 * MINUTE, 0)), log.sessions)
        assertEquals(listOf(5 * MINUTE), log.unlocks)
    }

    @Test
    fun `keyguard shown shortly after screen on means a glance`() {
        val events = listOf(
            ev(SCREEN_OFF, 0),
            ev(SCREEN_ON, 5), ev(KEYGUARD_SHOWN, 5, 1), ev(SCREEN_OFF, 5, 10),
            ev(SCREEN_ON, 9), ev(KEYGUARD_HIDDEN, 9, 2),
        )
        val log = SessionBuilder.build(events, rules)
        assertEquals(1, log.sessions.size)
        assertEquals(1, log.sessions[0].glances)
    }

    @Test
    fun `sessions shorter than the minimum are discarded but the unlock is counted`() {
        val log = SessionBuilder.build(lockUnlock(0, 1), rules)
        assertEquals(emptyList<Session>(), log.sessions)
        assertEquals(1, log.unlocks.size)
    }

    @Test
    fun `call without unlocking keeps the session running`() {
        // Answered from the lock screen: screen on, call UI over the keyguard, screen off at the ear.
        val events = listOf(
            ev(SCREEN_OFF, 0), ev(KEYGUARD_SHOWN, 0),
            ev(SCREEN_ON, 10), ev(SCREEN_OFF, 10, 3),
            ev(SCREEN_ON, 25), ev(SCREEN_OFF, 25, 2),
            ev(SCREEN_ON, 40), ev(KEYGUARD_HIDDEN, 40, 1),
        )
        val log = SessionBuilder.build(events, rules)
        assertEquals(1, log.sessions.size)
        assertEquals(40 * MINUTE + SECOND, log.sessions[0].durationMillis)
    }

    @Test
    fun `phone still locked leaves an open session`() {
        val log = SessionBuilder.build(listOf(ev(SCREEN_OFF, 0), ev(KEYGUARD_SHOWN, 0)), rules)
        assertEquals(0L, log.openSessionStart)
        assertEquals(emptyList<Session>(), log.sessions)
    }

    @Test
    fun `app on screen closes a session whose unlock is not logged yet`() {
        // The phone was locked at 10:00; the unlock that reopened the app is not in the log yet.
        val events = listOf(ev(SCREEN_OFF, 600), ev(KEYGUARD_SHOWN, 600), ev(SCREEN_ON, 667))
        val log = SessionBuilder.build(events, rules, closeOpenAt = 667 * MINUTE + 2 * SECOND)
        assertEquals(listOf(Session(600 * MINUTE, 667 * MINUTE + 2 * SECOND, 1)), log.sessions)
        assertEquals(1, log.unlocks.size)
        // Without that knowledge (e.g. the lock-screen card) the session stays open.
        assertEquals(600 * MINUTE, SessionBuilder.build(events, rules).openSessionStart)
    }

    @Test
    fun `short power off counts until the unlock after boot`() {
        val events = listOf(
            ev(SCREEN_OFF, 0), ev(KEYGUARD_SHOWN, 0),
            ev(SHUTDOWN, 10), ev(STARTUP, 60),
            ev(SCREEN_ON, 61), ev(KEYGUARD_HIDDEN, 62),
        )
        val log = SessionBuilder.build(events, rules)
        // The screen coming on at boot shows the lock screen: a glance.
        assertEquals(listOf(Session(0, 62 * MINUTE, 1)), log.sessions)
    }

    @Test
    fun `power off longer than a day counts only up to the shutdown`() {
        val events = listOf(
            ev(SCREEN_OFF, 0), ev(KEYGUARD_SHOWN, 0),
            ev(SHUTDOWN, 30), ev(STARTUP, 30 + 25 * 60),
            ev(SCREEN_ON, 30 + 25 * 60 + 1), ev(KEYGUARD_HIDDEN, 30 + 25 * 60 + 2),
        )
        val log = SessionBuilder.build(events, rules)
        assertEquals(listOf(Session(0, 30 * MINUTE, 0)), log.sessions)
    }

    @Test
    fun `boot without a known shutdown drops the open session`() {
        val events = listOf(ev(SCREEN_OFF, 0), ev(STARTUP, 120), ev(KEYGUARD_HIDDEN, 121))
        val log = SessionBuilder.build(events, rules)
        assertEquals(emptyList<Session>(), log.sessions)
    }
}
