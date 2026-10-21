package com.clobrano.irlhero.domain

/**
 * Whether today's goal can still be reached, told in phone time: the goal allows a fixed amount
 * of phone time per day, and the outlook depends on how much of it is left.
 * - [Status.OUT_OF_REACH]: the phone time used is already over what the goal allows, whatever the hour.
 * - [Status.AT_RISK]: only in the final stretch of the waking day ([FINAL_STRETCH_MILLIS]), when
 *   less than [LOW_PHONE_LEFT_MILLIS] of phone time is left. A demanding goal alone never warns early.
 */
data class GoalOutlook(
    val status: Status,
    /** IRL time still missing to meet the goal. */
    val neededMillis: Long,
    val phoneUsedMillis: Long,
    /** Phone time still allowed today; negative once over. */
    val phoneLeftMillis: Long,
    /** Waking time left today. */
    val remainingMillis: Long,
) {
    enum class Status { MET, ON_TRACK, AT_RISK, OUT_OF_REACH }

    /**
     * How long until the status could get worse, or null if it can't. Phone use counts both
     * clocks down; time away only the waking time left, which can open the final stretch.
     */
    val worsensInMillis: Long?
        get() = when (status) {
            Status.ON_TRACK -> maxOf(remainingMillis - FINAL_STRETCH_MILLIS, phoneLeftMillis - LOW_PHONE_LEFT_MILLIS)
            Status.AT_RISK -> phoneLeftMillis
            else -> null
        }?.coerceAtLeast(0)

    companion object {
        const val FINAL_STRETCH_MILLIS = 3 * HOUR
        const val LOW_PHONE_LEFT_MILLIS = 1 * HOUR

        fun of(today: DaySummary): GoalOutlook {
            val remaining = (today.wakingTotalMillis - today.wakingElapsedMillis).coerceAtLeast(0)
            val left = today.phoneLeftMillis
            val status = when {
                today.goalMillis <= 0 || today.goalMet -> Status.MET
                left < 0 -> Status.OUT_OF_REACH
                remaining <= FINAL_STRETCH_MILLIS && left < LOW_PHONE_LEFT_MILLIS -> Status.AT_RISK
                else -> Status.ON_TRACK
            }
            val needed = (today.goalMillis - today.irlMillis).coerceAtLeast(0)
            return GoalOutlook(status, needed, today.phoneMillis, left, remaining)
        }
    }
}
