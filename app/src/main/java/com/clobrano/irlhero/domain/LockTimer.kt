package com.clobrano.irlhero.domain

/**
 * When the lock-screen card's timer started: the start of the IRL session running now, or
 * null when the phone is in use.
 *
 * Screen and unlock broadcasts are not reliable enough on their own: the "user present"
 * broadcast is missing on some lock types and devices, and never comes when the screen
 * returns within the lock delay. A timer cleared only by it keeps counting from an old lock.
 * So the timer also remembers when the phone was last seen in use (a start older than that
 * is stale), and it is lined up with the event log, the source the app's stats use, on every
 * refresh.
 */
class LockTimer {
    var lockedAt: Long? = null
        private set

    private var lastSeenInUse: Long = Long.MIN_VALUE

    fun onScreenOff(now: Long) {
        val start = lockedAt
        if (start == null || start < lastSeenInUse) lockedAt = now
    }

    /** [keyguardLocked] false means the screen came back on with no lock screen: phone in use. */
    fun onScreenOn(keyguardLocked: Boolean, now: Long) {
        if (!keyguardLocked) markInUse(now)
    }

    fun onUnlock(now: Long) = markInUse(now)

    /**
     * [sessionRunning]: screen off or lock screen showing. [logOpenSessionStart]: start of the
     * session open in the event log, preferred when present; the log can lag a fresh lock by a
     * moment, so the broadcast time is kept until it catches up.
     */
    fun reconcile(sessionRunning: Boolean, logOpenSessionStart: Long?, now: Long) {
        when {
            !sessionRunning -> markInUse(now)
            logOpenSessionStart != null -> lockedAt = logOpenSessionStart
        }
    }

    private fun markInUse(now: Long) {
        lockedAt = null
        lastSeenInUse = maxOf(lastSeenInUse, now)
    }
}
