package com.clobrano.irlhero.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockTimerTest {

    @Test
    fun `screen back on within the lock delay restarts the timer at the next lock`() {
        val t = LockTimer()
        t.onScreenOff(now = 1_000)
        t.onScreenOn(keyguardLocked = false, now = 2_000) // no lock screen, so no unlock broadcast
        assertNull(t.lockedAt)
        t.onScreenOff(now = 9_000)
        assertEquals(9_000L, t.lockedAt)
    }

    @Test
    fun `glancing at the lock screen keeps the timer running`() {
        val t = LockTimer()
        t.onScreenOff(now = 1_000)
        t.onScreenOn(keyguardLocked = true, now = 2_000)
        t.onScreenOff(now = 5_000)
        assertEquals(1_000L, t.lockedAt)
        t.onUnlock(now = 6_000)
        assertNull(t.lockedAt)
    }

    @Test
    fun `no unlock broadcast at all, but a refresh saw the phone in use`() {
        val t = LockTimer()
        t.onScreenOff(now = 1_000)
        t.onScreenOn(keyguardLocked = true, now = 2_000) // unlock happens, no broadcast arrives
        t.reconcile(sessionRunning = false, logOpenSessionStart = null, now = 7_000) // check 5 s later
        t.onScreenOff(now = 60_000)
        // The log has not caught up with the new lock yet: the fresh broadcast time is used.
        t.reconcile(sessionRunning = true, logOpenSessionStart = null, now = 60_001)
        assertEquals(60_000L, t.lockedAt)
    }

    @Test
    fun `reconcile replaces a stale lock time with the log's open session`() {
        val t = LockTimer()
        t.onScreenOff(now = 1_000) // yesterday, never cleared
        t.reconcile(sessionRunning = true, logOpenSessionStart = 80_000, now = 90_000)
        assertEquals(80_000L, t.lockedAt)
    }

    @Test
    fun `reconcile keeps the broadcast time until the log catches up, and clears when in use`() {
        val t = LockTimer()
        t.onScreenOff(now = 5_000)
        t.reconcile(sessionRunning = true, logOpenSessionStart = null, now = 5_001)
        assertEquals(5_000L, t.lockedAt)
        t.reconcile(sessionRunning = false, logOpenSessionStart = 5_000, now = 8_000)
        assertNull(t.lockedAt)
    }
}
