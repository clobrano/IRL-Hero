package com.clobrano.irlhero.domain

import java.time.LocalDate

/** Screen and lock events as read from the system usage log. */
enum class EventType { SCREEN_OFF, SCREEN_ON, KEYGUARD_SHOWN, KEYGUARD_HIDDEN, SHUTDOWN, STARTUP }

data class ScreenEvent(val type: EventType, val timeMillis: Long)

/** User-tunable rules that turn raw lock/unlock times into a fair score. */
data class SessionRules(
    val minSessionMillis: Long = 2 * MINUTE,
    /** Minutes after midnight when the sleep window starts (default 23:00). */
    val sleepStartMinute: Int = 23 * 60,
    /** Minutes after midnight when the sleep window ends (default 07:00). */
    val sleepEndMinute: Int = 7 * 60,
    /** Daily goal as a share of waking hours (0..100). */
    val goalPercent: Int = 50,
)

/** A valid IRL session: from lock to the next unlock. */
data class Session(
    val startMillis: Long,
    val endMillis: Long,
    val glances: Int,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

data class Interval(val start: Long, val end: Long) {
    val length: Long get() = (end - start).coerceAtLeast(0)
    fun overlap(other: Interval): Long =
        (minOf(end, other.end) - maxOf(start, other.start)).coerceAtLeast(0)
}

/** Output of the session builder. */
data class SessionLog(
    val sessions: List<Session>,
    /** Every unlock, including those that ended a too-short session. */
    val unlocks: List<Long>,
    /** Start of the session still running (phone locked right now), if any. */
    val openSessionStart: Long?,
)

data class DaySummary(
    val date: LocalDate,
    val irlMillis: Long,
    /** Waking milliseconds elapsed so far that day (full waking span for past days). */
    val wakingElapsedMillis: Long,
    val wakingTotalMillis: Long,
    val goalMillis: Long,
    val longestMillis: Long,
    val sessions: Int,
    val unlocks: Int,
    val glances: Int,
) {
    val goalMet: Boolean get() = goalMillis > 0 && irlMillis >= goalMillis
    val irlRatio: Float get() = if (wakingElapsedMillis > 0) irlMillis.toFloat() / wakingElapsedMillis else 0f
    val goalProgress: Float get() = if (goalMillis > 0) (irlMillis.toFloat() / goalMillis).coerceAtMost(1f) else 0f
}

/** A session as seen from one day: only the part outside the sleep window counts. */
data class ScoredSession(val session: Session, val irlMillis: Long)

const val SECOND = 1000L
const val MINUTE = 60 * SECOND
const val HOUR = 60 * MINUTE
const val DAY = 24 * HOUR
