package com.dculus.stayfocused.core.blocking.evaluator

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class CycleStateMachineTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val min = 60_000L

    private fun cycle(
        use: Int? = 10,
        rest: Int? = 30,
    ) = Block(
        id = "c",
        target = BlockTarget.ThisPhone,
        type = BlockType.CYCLE,
        name = "c",
        apps = setOf("a"),
        limitMins = null,
        period = null,
        useMins = use,
        restMins = rest,
        range = null,
        durationMins = null,
        startedAt = null,
        days = DaysOfWeek.ALL,
        enabled = true,
        createdAt = Instant.EPOCH,
        source = BlockSource.MANUAL,
    )

    @Test
    fun `accumulates foreground time below the use window`() {
        val s = CycleStateMachine.advance(CycleState(), 4 * min, now, cycle())
        assertEquals(CycleState(4 * min, null), s)
        assertEquals(CycleState(9 * min, null), CycleStateMachine.advance(s, 5 * min, now, cycle()))
    }

    @Test
    fun `locks when the use window is used up`() {
        val s = CycleStateMachine.advance(CycleState(6 * min), 4 * min, now, cycle())
        assertEquals(CycleState(0, now.plusSeconds(30 * 60)), s)
    }

    @Test
    fun `locks when the use window is exceeded`() {
        val s = CycleStateMachine.advance(CycleState(), 25 * min, now, cycle())
        assertEquals(CycleState(0, now.plusSeconds(30 * 60)), s)
    }

    @Test
    fun `foreground time while resting is ignored`() {
        val locked = CycleState(0, now.plusSeconds(600))
        assertEquals(locked, CycleStateMachine.advance(locked, 5 * min, now, cycle()))
    }

    @Test
    fun `first open after the rest starts a fresh window`() {
        val locked = CycleState(0, now)
        assertEquals(CycleState(3 * min, null), CycleStateMachine.advance(locked, 3 * min, now, cycle()))
        assertEquals(
            CycleState(0, now.plusSeconds(30 * 60)),
            CycleStateMachine.advance(locked, 10 * min, now, cycle()),
        )
    }

    @Test
    fun `negative deltas are treated as zero`() {
        assertEquals(CycleState(2 * min, null), CycleStateMachine.advance(CycleState(2 * min), -5 * min, now, cycle()))
    }

    @Test
    fun `a block without a use window resets the state`() {
        assertEquals(CycleState(), CycleStateMachine.advance(CycleState(5 * min), min, now, cycle(use = null)))
    }

    @Test
    fun `a missing rest means no rest`() {
        assertEquals(CycleState(0, now), CycleStateMachine.advance(CycleState(), 10 * min, now, cycle(rest = null)))
    }
}
