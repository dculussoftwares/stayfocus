package com.dculus.stayfocused.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GoalNoteTest {
    @Test
    fun `below the goal is under by the difference`() {
        assertEquals(GoalNote.Under(33), goalNote(todayMins = 147, goalMins = 180))
    }

    @Test
    fun `above the goal is over by the difference`() {
        assertEquals(GoalNote.Over(20), goalNote(todayMins = 200, goalMins = 180))
    }

    @Test
    fun `exactly at the goal is on goal`() {
        assertEquals(GoalNote.OnGoal, goalNote(todayMins = 180, goalMins = 180))
    }

    @Test
    fun `no usage is under by the whole goal`() {
        assertEquals(GoalNote.Under(DEFAULT_SCREEN_TIME_GOAL_MINS), goalNote(0, DEFAULT_SCREEN_TIME_GOAL_MINS))
    }

    @Test
    fun `one minute either side`() {
        assertEquals(GoalNote.Under(1), goalNote(179, 180))
        assertEquals(GoalNote.Over(1), goalNote(181, 180))
    }
}
