package com.clobrano.irlhero.domain

/**
 * When the lock-screen card's timer started: the start of the IRL session running now, or
 * null when the phone is in use.
 *
 * Broadcasts alone are not enough: when the screen comes back on within the lock delay there
 * is no lock screen, so Android sends no "user present" broadcast, and a timer cleared only by
 * that broadcast keeps counting from an old lock. So the timer is also cleared when the screen
 * comes on unlocked, and is lined up with the event log (the source the app's stats use) on
 * every refresh.
 */
class LockTimer {
    var lockedAt: Long? = null
        private set

    fun onScreenOff(now: Long) {
        if (lockedAt == null) lockedAt = now
    }

    /** [keyguardLocked] false means the screen came back on with no lock screen: phone in use. */
    fun onScreenOn(keyguardLocked: Boolean) {
        if (!keyguardLocked) lockedAt = null
    }

    fun onUnlock() {
        lockedAt = null
    }

    /**
     * [sessionRunning]: screen off or lock screen showing. [logOpenSessionStart]: start of the
     * session open in the event log, preferred when present; the log can lag a fresh lock by a
     * moment, so the broadcast time is kept until it catches up.
     */
    fun reconcile(sessionRunning: Boolean, logOpenSessionStart: Long?) {
        lockedAt = when {
            !sessionRunning -> null
            logOpenSessionStart != null -> logOpenSessionStart
            else -> lockedAt
        }
    }
}
