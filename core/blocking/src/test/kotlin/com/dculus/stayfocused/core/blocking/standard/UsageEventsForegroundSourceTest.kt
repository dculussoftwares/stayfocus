package com.dculus.stayfocused.core.blocking.standard

import com.dculus.stayfocused.core.blocking.ForegroundAppTracker
import com.dculus.stayfocused.core.blocking.ForegroundEnvironment
import com.dculus.stayfocused.core.usage.ResumeEvent
import com.dculus.stayfocused.core.usage.ResumeEventReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UsageEventsForegroundSourceTest {
    private class FakeReader : ResumeEventReader {
        val events = mutableListOf<ResumeEvent>()
        val reads = mutableListOf<LongRange>()
        var failure: SecurityException? = null

        override fun resumedSince(
            fromMillis: Long,
            toMillis: Long,
        ): List<ResumeEvent> {
            failure?.let { throw it }
            reads += fromMillis..toMillis
            return events.filter { it.timeMillis in fromMillis until toMillis }.sortedBy { it.timeMillis }
        }
    }

    private val env =
        object : ForegroundEnvironment {
            override val ownPackage = "own"

            override fun imePackages() = emptySet<String>()

            override fun isActivity(
                packageName: String,
                className: String,
            ): Boolean? = null

            override fun nowMillis() = 0L
        }
    private val tracker = ForegroundAppTracker(env)
    private val reader = FakeReader()
    private val screenOn = MutableStateFlow(true)
    private val hasRules = MutableStateFlow(true)
    private var lostAccess = 0

    private fun TestScope.source(): UsageEventsForegroundSource {
        val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId?): Clock = this

                override fun instant(): Instant = Instant.ofEpochMilli(START + testScheduler.currentTime)
            }
        return UsageEventsForegroundSource(
            reader,
            tracker,
            object : ActiveRules {
                override val hasActiveRules = hasRules
            },
            InteractiveScreen { screenOn as Flow<Boolean> },
            clock,
            StandardTestDispatcher(testScheduler),
        )
    }

    /** The poll loop never ends on its own: stop it so runTest can finish. */
    private fun runSource(
        started: Boolean = true,
        body: suspend TestScope.(UsageEventsForegroundSource) -> Unit,
    ) = runTest {
        val s = source()
        try {
            if (started) s.start { lostAccess++ }
            body(s)
        } finally {
            s.stop()
        }
    }

    private fun event(
        pkg: String,
        atOffset: Long,
    ) = ResumeEvent(pkg, "$pkg.Main", START + atOffset)

    @Test fun firstEventOnStartIsTheNewestOfTheLastTenMinutes() =
        runSource(started = false) { s ->
            reader.events += event("a", -5 * 60_000L)
            reader.events += event("b", -60_000L)
            s.start { lostAccess++ }
            runCurrent()
            assertEquals("b", tracker.foreground.value?.packageName)
        }

    @Test fun newEventsAreDeliveredAndNotRepeatedAcrossOverlappingReads() =
        runSource { _ ->
            runCurrent()
            assertNull(tracker.foreground.value)
            reader.events += event("a", 100)
            advanceTimeBy(600)
            assertEquals("a", tracker.foreground.value?.packageName)
            reader.events += event("b", 700)
            advanceTimeBy(500)
            assertEquals("b", tracker.foreground.value?.packageName)
            // "a" is read again by the next overlapping windows but must not take the foreground back.
            advanceTimeBy(1_500)
            assertEquals("b", tracker.foreground.value?.packageName)
        }

    @Test fun anEventWrittenLateInsideTheOverlapIsStillDelivered() =
        runSource { _ ->
            runCurrent()
            advanceTimeBy(1_000)
            // Its timestamp is a second old by the time it becomes readable.
            reader.events += event("late", 1_000 - 900)
            advanceTimeBy(500)
            assertEquals("late", tracker.foreground.value?.packageName)
        }

    @Test fun revokedUsageAccessStopsPollingAndReportsOnce() =
        runSource { _ ->
            runCurrent()
            reader.failure = SecurityException("revoked")
            advanceTimeBy(5_000)
            assertEquals(1, lostAccess)
            assertNull(tracker.foreground.value)
            val reads = reader.reads.size
            reader.failure = null
            advanceTimeBy(5_000)
            assertEquals(reads, reader.reads.size)
        }

    @Test fun screenOffPausesPollingAndClearsTheForeground() =
        runSource(started = false) { s ->
            reader.events += event("a", -1_000L)
            s.start { lostAccess++ }
            runCurrent()
            assertEquals("a", tracker.foreground.value?.packageName)
            screenOn.value = false
            runCurrent()
            assertNull(tracker.foreground.value)
            val reads = reader.reads.size
            advanceTimeBy(5_000)
            assertEquals(reads, reader.reads.size)
            // Back on: the app that is in front again is picked up from the recent history.
            screenOn.value = true
            runCurrent()
            assertEquals("a", tracker.foreground.value?.packageName)
        }

    @Test fun noPollingWhileTheEngineHasNothingToEnforce() =
        runSource(started = false) { s ->
            hasRules.value = false
            s.start { lostAccess++ }
            advanceTimeBy(5_000)
            assertEquals(0, reader.reads.size)
            hasRules.value = true
            advanceTimeBy(1_000)
            assert(reader.reads.isNotEmpty())
        }

    @Test fun stopEndsPolling() =
        runSource { s ->
            advanceTimeBy(1_000)
            s.stop()
            val reads = reader.reads.size
            advanceTimeBy(5_000)
            assertEquals(reads, reader.reads.size)
        }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
