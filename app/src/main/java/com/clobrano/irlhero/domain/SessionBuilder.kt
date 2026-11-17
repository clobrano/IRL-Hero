package com.clobrano.irlhero.domain

/**
 * Turns raw screen/keyguard events into IRL sessions.
 *
 * - A session starts when the screen turns off.
 * - It ends when the user unlocks (keyguard hidden), or when the screen comes back on
 *   with no lock screen (e.g. inside the lock timeout).
 * - Turning the screen on and looking at the lock screen is a glance: allowed, counted.
 * - Calls placed or answered without unlocking never end a session.
 * - An unlock followed by screen off within [SessionRules.slipMillis] is a slip (an accidental
 *   unlock): the session goes on, and the unlocked seconds are not counted as IRL time.
 * - Sessions shorter than [SessionRules.minSessionMillis] are discarded.
 * - A shutdown/boot gap counts only if both are known and the gap is under 24 h.
 */
object SessionBuilder {

    /** How long after a screen-on we wait for a keyguard event before deciding. */
    private const val KEYGUARD_GRACE_MILLIS = 2 * SECOND
    private const val MAX_POWER_OFF_MILLIS = DAY

    /**
     * [closeOpenAt]: a session still open in the log is counted as ending at that time.
     * The app on screen means the phone is unlocked, but the system can log the unlock a
     * moment late; the lock-screen card uses it to count the running session so far.
     */
    fun build(events: List<ScreenEvent>, rules: SessionRules, closeOpenAt: Long? = null): SessionLog {
        val sorted = events.sortedWith(compareBy({ it.timeMillis }, { it.type.ordinal }))
        val sessions = mutableListOf<Session>()
        val unlocks = mutableListOf<Long>()

        var start: Long? = null
        var glances = 0
        val slips = mutableListOf<Interval>()
        var locked = false
        var shutdownAt: Long? = null

        fun close(at: Long) {
            val s = start ?: return
            if (at - s >= rules.minSessionMillis) sessions += Session(s, at, glances, slips.toList())
            start = null
            glances = 0
            slips.clear()
        }

        /** [index]: the event that unlocked, to look ahead for a quick screen off (a slip). */
        fun unlock(at: Long, index: Int? = null) {
            val slipEnd = if (start != null && index != null) slipEnd(sorted, index, at, rules.slipMillis) else null
            if (slipEnd != null) {
                slips += Interval(at, slipEnd)
                return
            }
            unlocks += at
            close(at)
        }

        for ((i, e) in sorted.withIndex()) {
            when (e.type) {
                EventType.SCREEN_OFF -> if (start == null) {
                    start = e.timeMillis
                    glances = 0
                }

                EventType.KEYGUARD_SHOWN -> locked = true

                EventType.KEYGUARD_HIDDEN -> {
                    locked = false
                    unlock(e.timeMillis, i)
                }

                EventType.SCREEN_ON -> if (start != null) {
                    when (nextKeyguardEvent(sorted, i)) {
                        EventType.KEYGUARD_HIDDEN -> Unit // the unlock itself closes the session
                        EventType.KEYGUARD_SHOWN -> glances++
                        else -> if (locked) glances++ else unlock(e.timeMillis, i)
                    }
                }

                EventType.SHUTDOWN -> {
                    if (start == null) {
                        start = e.timeMillis
                        glances = 0
                    }
                    shutdownAt = e.timeMillis
                }

                EventType.STARTUP -> {
                    val off = shutdownAt
                    if (off == null || e.timeMillis - off > MAX_POWER_OFF_MILLIS) {
                        // Unknown or too-long power-off: count up to the shutdown only.
                        if (off != null) close(off) else {
                            start = null
                            glances = 0
                        }
                    }
                    // A booted phone sits on the lock screen; the next unlock ends the session.
                    locked = true
                    shutdownAt = null
                }
            }
        }
        val open = start
        if (closeOpenAt != null && open != null && closeOpenAt >= open) {
            unlock(closeOpenAt)
        }
        return SessionLog(sessions, unlocks, open)
    }

    /** When the screen went off again, if within [slipMillis] of the unlock at [at]. */
    private fun slipEnd(sorted: List<ScreenEvent>, index: Int, at: Long, slipMillis: Long): Long? {
        if (slipMillis <= 0) return null
        for (j in index + 1 until sorted.size) {
            val next = sorted[j]
            if (next.timeMillis - at > slipMillis) return null
            if (next.type == EventType.SCREEN_OFF || next.type == EventType.SHUTDOWN) return next.timeMillis
        }
        return null
    }

    private fun nextKeyguardEvent(sorted: List<ScreenEvent>, index: Int): EventType? {
        val t = sorted[index].timeMillis
        for (j in index + 1 until sorted.size) {
            val next = sorted[j]
            if (next.timeMillis - t > KEYGUARD_GRACE_MILLIS) return null
            if (next.type == EventType.KEYGUARD_HIDDEN || next.type == EventType.KEYGUARD_SHOWN) return next.type
            if (next.type == EventType.SCREEN_OFF) return null
        }
        return null
    }
}
