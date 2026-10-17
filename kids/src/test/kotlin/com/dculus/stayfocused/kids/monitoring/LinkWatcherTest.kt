package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.sync.DeviceLinkState
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.FakeKidsHomeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LinkWatcherTest {
    private val target = DeviceTarget("parent-1", "device-1")
    private val home = FakeKidsHomeRepository()
    private val watcher = LinkWatcher(home)

    @Test
    fun staysQuietWhileLinkedAndReturnsWhenTheServerSaysGone() =
        runTest {
            var gone = false
            val job =
                launch {
                    watcher.untilGone(target)
                    gone = true
                }
            home.link.emit(DeviceLinkState.Linked)
            runCurrent()
            assertEquals(false, gone)

            home.link.emit(DeviceLinkState.Gone)
            runCurrent()
            assertEquals(true, gone)
            job.join()
        }

    @Test
    fun aSingleRefusedReadIsNotEnough() =
        runTest {
            // Refused once (say, a rejected App Check token), then the device is readable again.
            home.queueRun(DeviceLinkState.Unreadable)
            var gone = false
            val job =
                launch {
                    watcher.untilGone(target)
                    gone = true
                }
            home.link.emit(DeviceLinkState.Linked)
            advanceTimeBy(LinkWatcher.RECHECK_MILLIS + 1)
            runCurrent()
            assertEquals(false, gone)
            assertEquals(2, home.linkWatches)
            job.cancel()
        }

    @Test
    fun readsThatKeepBeingRefusedCountAsGone() =
        runTest {
            repeat(LinkWatcher.REFUSALS_TO_CONFIRM) { home.queueRun(DeviceLinkState.Unreadable) }
            watcher.untilGone(target)
            assertEquals(LinkWatcher.REFUSALS_TO_CONFIRM, home.linkWatches)
        }

    @Test
    fun aLinkedReadInBetweenStartsTheCountOver() =
        runTest {
            home.queueRun(DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            // Linked and then refused again: the two earlier refusals no longer count.
            home.queueRun(DeviceLinkState.Linked, DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            watcher.untilGone(target)
            // 2 refusals, the linked run (resets, then 1 refusal), 2 more = confirmed on the 3rd consecutive one.
            assertEquals(5, home.linkWatches)
        }

    @Test
    fun aFailedLookBetweenRefusalsBreaksTheRun() =
        runTest {
            home.queueRun(DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            home.linkFlows.addLast(flow { throw IOException("offline") })
            home.queueRun(DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            home.queueRun(DeviceLinkState.Unreadable)
            watcher.untilGone(target)
            // Two refusals, a failure (count restarts), then three more in a row.
            assertEquals(6, home.linkWatches)
        }
}
