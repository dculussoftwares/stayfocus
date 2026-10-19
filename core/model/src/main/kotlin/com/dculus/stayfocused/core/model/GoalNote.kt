package com.dculus.stayfocused.core.model

/** Default daily screen-time goal, in minutes (3 h). */
const val DEFAULT_SCREEN_TIME_GOAL_MINS = 180

/** How today's screen time compares with the goal; the minute counts are always positive. */
sealed interface GoalNote {
    data class Under(
        val mins: Int,
    ) : GoalNote

    data class Over(
        val mins: Int,
    ) : GoalNote

    data object OnGoal : GoalNote
}

fun goalNote(
    todayMins: Int,
    goalMins: Int,
): GoalNote =
    when {
        todayMins < goalMins -> GoalNote.Under(goalMins - todayMins)
        todayMins > goalMins -> GoalNote.Over(todayMins - goalMins)
        else -> GoalNote.OnGoal
    }
