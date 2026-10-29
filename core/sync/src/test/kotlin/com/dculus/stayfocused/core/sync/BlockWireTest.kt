package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.CycleScope
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockWireTest {
    private val at = Instant.parse("2026-10-17T10:00:00Z")

    private fun base(
        type: BlockType,
        target: BlockTarget = BlockTarget.Device("d1"),
    ) = Block(
        id = "b1",
        target = target,
        type = type,
        name = "Name",
        apps = setOf("com.b", "com.a"),
        limitMins = null,
        period = null,
        useMins = null,
        restMins = null,
        range = null,
        durationMins = null,
        startedAt = null,
        days = DaysOfWeek.WEEKDAYS,
        enabled = true,
        createdAt = at,
        source = BlockSource.AI,
    )

    private fun roundTrip(block: Block) = parseWireBlock(block.id, block.toWire())

    @Test
    fun everyBlockTypeSurvivesTheTripAsAThisPhoneBlock() {
        val blocks =
            listOf(
                base(BlockType.LIMIT).copy(limitMins = 30, period = LimitPeriod.HOURLY),
                base(BlockType.CYCLE).copy(useMins = 10, restMins = 30),
                base(BlockType.CYCLE).copy(useMins = 10, restMins = 30, cycleScope = CycleScope.TOTAL),
                base(BlockType.SCHEDULE).copy(range = TimeRange.parse("22:00–07:00")),
                base(BlockType.NOW).copy(durationMins = 60, startedAt = at),
            )
        for (block in blocks) {
            assertEquals(block.copy(target = BlockTarget.ThisPhone), roundTrip(block))
        }
    }

    @Test
    fun wireKeysAreOnlyOnesTheRulesAllow() {
        val allowed =
            setOf(
                "id",
                "type",
                "name",
                "apps",
                "limitMins",
                "period",
                "useMins",
                "restMins",
                "range",
                "durationMins",
                "startedAt",
                "days",
                "enabled",
                "createdAt",
                "source",
                "cycleScope",
            )
        val wire = base(BlockType.SCHEDULE).copy(range = TimeRange.parse("09:00–17:00")).toWire()
        assertEquals(emptySet(), wire.keys - allowed)
        assertEquals(mapOf("start" to "09:00", "end" to "17:00"), wire["range"])
        assertEquals(listOf("com.a", "com.b"), wire["apps"])
        assertEquals(31, wire["days"])
    }

    @Test
    fun aDocumentMissingItsTypeFieldsIsIgnored() {
        assertNull(parseWireBlock("x", base(BlockType.LIMIT).toWire()))
        assertNull(parseWireBlock("x", base(BlockType.NOW).copy(durationMins = 5).toWire()))
    }

    @Test
    fun garbageIsIgnoredInsteadOfThrowing() {
        assertNull(parseWireBlock("x", emptyMap()))
        assertNull(parseWireBlock("x", mapOf("type" to "LIMIT", "days" to 500L, "enabled" to true)))
        val badRange = base(BlockType.SCHEDULE).toWire() + ("range" to mapOf("start" to "9", "end" to "10"))
        assertNull(parseWireBlock("x", badRange))
    }
}
