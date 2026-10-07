package com.dculus.stayfocused.core.blocking.screen

import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

class BlockCountdownTest {
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private val utc = ZoneId.of("UTC")

    private fun block(
        reason: BlockReason,
        until: Instant?,
    ) = Decision.Block(reason, "b1", until)

    @Test
    fun `mmss rounds up and never goes negative`() {
        assertEquals("0:00", mmss(0))
        assertEquals("0:01", mmss(1))
        assertEquals("4:59", mmss(299_000))
        assertEquals("5:00", mmss(299_001))
        assertEquals("0:00", mmss(-5_000))
    }

    @Test
    fun `hms pads and floors`() {
        assertEquals("00:00:00", hms(-1))
        assertEquals("05:12:09", hms(((5 * 60 + 12) * 60 + 9) * 1000L + 999))
    }

    @Test
    fun `countdown reasons use mmss under an hour and hms above`() {
        assertEquals(
            BlockTimeLine.Countdown("4:30"),
            timeLineFor(block(BlockReason.BREAK, now.plusSeconds(270)), now, utc),
        )
        assertEquals(
            BlockTimeLine.Countdown("05:00:00"),
            timeLineFor(block(BlockReason.LIMIT_DAILY, now.plusSeconds(5 * 3600L)), now, utc),
        )
    }

    @Test
    fun `countdown never shows 60 minutes`() {
        assertEquals(
            BlockTimeLine.Countdown("59:59"),
            timeLineFor(block(BlockReason.BREAK, now.plusSeconds(3599)), now, utc),
        )
        assertEquals(
            BlockTimeLine.Countdown("00:59:59"),
            timeLineFor(block(BlockReason.BREAK, now.plusMillis(3_599_500)), now, utc),
        )
        assertEquals(
            BlockTimeLine.Countdown("01:00:00"),
            timeLineFor(block(BlockReason.BREAK, now.plusSeconds(3600)), now, utc),
        )
    }

    @Test
    fun `schedule and timed manual lock show a clock time`() {
        val until = Instant.parse("2026-10-07T18:30:00Z")
        assertEquals(BlockTimeLine.Until("18:30"), timeLineFor(block(BlockReason.SCHEDULE, until), now, utc))
        assertEquals(BlockTimeLine.Until("18:30"), timeLineFor(block(BlockReason.MANUAL_LOCK, until), now, utc))
    }

    @Test
    fun `open ended manual lock shows nothing`() {
        assertEquals(BlockTimeLine.None, timeLineFor(block(BlockReason.MANUAL_LOCK, null), now, utc))
    }

    @Test
    fun `past end clamps countdown to zero`() {
        assertEquals(
            BlockTimeLine.Countdown("0:00"),
            timeLineFor(block(BlockReason.FOCUS, now.minusSeconds(3)), now, utc),
        )
    }

    @Test
    fun `default decision source allows once the end has passed`() =
        runBlocking {
            val source = TimeBasedBlockDecisionSource()
            val initial = block(BlockReason.BREAK, now)
            assertEquals(initial, source.current("p", initial, now.minusSeconds(1)))
            assertEquals(Decision.Allow, source.current("p", initial, now))
            val open = block(BlockReason.MANUAL_LOCK, null)
            assertEquals(open, source.current("p", open, now.plusSeconds(99_999)))
        }
}
