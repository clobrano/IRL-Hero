package com.clobrano.irlhero.domain

/**
 * Whether today's goal can still be reached with the waking time left.
 * - [Status.AT_RISK]: reaching it needs more than [RISK_SHARE] of the time left away from the phone.
 * - [Status.OUT_OF_REACH]: even staying off the phone for the rest of the day would not be enough.
 */
data class GoalOutlook(
    val status: Status,
    /** IRL time still needed to meet the goal. */
    val neededMillis: Long,
    /** Waking time left today. */
    val remainingMillis: Long,
) {
    enum class Status { MET, ON_TRACK, AT_RISK, OUT_OF_REACH }

    /**
     * How long phone use from now on can go before the status gets worse, or null if it can't.
     * Time away from the phone never makes it worse, so this is the moment to check again.
     */
    val worsensInMillis: Long?
        get() = when (status) {
            Status.ON_TRACK -> remainingMillis - (neededMillis / RISK_SHARE).toLong()
            Status.AT_RISK -> remainingMillis - neededMillis
            else -> null
        }?.coerceAtLeast(0)

    companion object {
        const val RISK_SHARE = 0.75

        fun of(today: DaySummary): GoalOutlook {
            val needed = (today.goalMillis - today.irlMillis).coerceAtLeast(0)
            val remaining = (today.wakingTotalMillis - today.wakingElapsedMillis).coerceAtLeast(0)
            val status = when {
                today.goalMillis <= 0 || needed == 0L -> Status.MET
                needed > remaining -> Status.OUT_OF_REACH
                needed > remaining * RISK_SHARE -> Status.AT_RISK
                else -> Status.ON_TRACK
            }
            return GoalOutlook(status, needed, remaining)
        }
    }
}
