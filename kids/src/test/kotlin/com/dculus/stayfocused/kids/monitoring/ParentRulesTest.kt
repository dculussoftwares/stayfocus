package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.sync.CommandHandler
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.FakeDeviceBlocksRepository
import com.dculus.stayfocused.core.sync.RemoteCommand
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParentRulesTest {
    private val target = DeviceTarget("parent-1", "device-1")
    private val remote = FakeDeviceBlocksRepository()
    private val blocks = FakeBlockRepository()
    private val locked = FakeLockedAppsRepository()
    private val settings = FakeSettingsRepository()
    private val clock = TestClock()
    private val targets =
        object : ReportTargetStore {
            override var target: DeviceTarget? = this@ParentRulesTest.target
        }
    private val signedIn =
        object : DeviceAuth {
            override val uid = flowOf<String?>("child")

            override suspend fun ensureSignedIn() = "child"
        }
    private val links =
        object : ParentLinkStore {
            override var parentName: String? = "Asha"
            override var parentUid: String? = "parent-1"
            override var deviceId: String? = "device-1"
        }
    private val mirror = ParentRulesMirror(remote, signedIn, blocks, locked, settings, links)

    private fun block(
        id: String,
        limit: Int = 30,
    ) = Block(
        id = id,
        target = BlockTarget.ThisPhone,
        type = BlockType.LIMIT,
        name = "Limit $id",
        apps = setOf("com.example.a"),
        limitMins = limit,
        period = LimitPeriod.DAILY,
        useMins = null,
        restMins = null,
        range = null,
        durationMins = null,
        startedAt = null,
        days = DaysOfWeek.ALL,
        enabled = true,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        source = BlockSource.MANUAL,
    )

    private suspend fun local() = blocks.observeByTarget(BlockTarget.ThisPhone).first()

    @Test
    fun aBlockTheParentCreatedIsStoredForTheEngine() =
        runTest {
            mirror.apply(listOf(block("a")))
            assertEquals(listOf(block("a")), local())
        }

    @Test
    fun changedBlocksAreReplacedAndRemovedOnesDropped() =
        runTest {
            mirror.apply(listOf(block("a"), block("b")))
            mirror.apply(listOf(block("a", limit = 45)))
            assertEquals(listOf(block("a", limit = 45)), local())
        }

    @Test
    fun syncNowFetchesFromTheServer() =
        runTest {
            remote.blocks.value = listOf(block("a"))
            mirror.syncNow(target)
            assertEquals(listOf(block("a")), local())
        }

    @Test
    fun syncNowFailsWhenOfflineAndKeepsTheStoredBlocks() =
        runTest {
            mirror.apply(listOf(block("a")))
            remote.offline = true
            assertFailsWith<IllegalStateException> { mirror.syncNow(target) }
            assertEquals(listOf(block("a")), local())
        }

    @Test
    fun clearEndsEverythingTheParentSet() =
        runTest {
            mirror.apply(listOf(block("a")))
            locked.lock("com.example.a", BlockTarget.ThisPhone)
            settings.setFocusSession(FocusSession(clock.instant(), clock.instant().plusSeconds(60), 1))
            links.parentUid = null
            mirror.clear()
            assertTrue(local().isEmpty())
            assertTrue(locked.observeAll().first().isEmpty())
            assertNull(settings.settings.first().focusSession)
        }

    @Test
    fun blockAndUnblockLockAndUnlockTheApp() =
        runTest {
            BlockAppHandler(locked, mirror).handle(RemoteCommand.BlockApp("com.example.a"))
            BlockAppHandler(locked, mirror).handle(RemoteCommand.BlockApp("com.example.a"))
            assertEquals(listOf("com.example.a"), locked.observeByTarget(BlockTarget.ThisPhone).first().map { it.pkg })
            UnblockAppHandler(locked, mirror).handle(RemoteCommand.UnblockApp("com.example.a"))
            assertTrue(locked.observeAll().first().isEmpty())
        }

    @Test
    fun startFocusSetsTheSessionAndMirrorsItsEnd() =
        runTest {
            StartFocusHandler(settings, remote, targets, clock, mirror).handle(RemoteCommand.StartFocus(25))
            val session = settings.settings.first().focusSession
            assertEquals(clock.instant(), session?.startedAt)
            assertEquals(clock.instant().plus(Duration.ofMinutes(25)), session?.endsAt)
            assertEquals(listOf<Instant?>(session?.endsAt), remote.focusEnds)
        }

    @Test
    fun aMaximumFocusSessionIsMirroredWithinTheRulesEightHours() =
        runTest {
            StartFocusHandler(settings, remote, targets, clock, mirror).handle(RemoteCommand.StartFocus(480))
            assertEquals(clock.instant().plus(Duration.ofMinutes(480 - 5)), remote.focusEnds.single())
            // The phone itself still blocks for the full length.
            assertEquals(
                clock.instant().plus(Duration.ofMinutes(480)),
                settings.settings
                    .first()
                    .focusSession
                    ?.endsAt,
            )
        }

    @Test
    fun clearAfterRelinkingStillEndsTheOldParentsLocksAndFocusButKeepsNewBlocks() =
        runTest {
            locked.lock("com.example.a", BlockTarget.ThisPhone)
            settings.setFocusSession(FocusSession(clock.instant(), clock.instant().plusSeconds(60), 1))
            mirror.apply(listOf(block("new")))
            mirror.clear()
            assertEquals(listOf(block("new")), local())
            assertTrue(locked.observeAll().first().isEmpty())
            assertNull(settings.settings.first().focusSession)
        }

    @Test
    fun stopFocusClearsTheSessionAndTheMirror() =
        runTest {
            StartFocusHandler(settings, remote, targets, clock, mirror).handle(RemoteCommand.StartFocus(25))
            StopFocusHandler(settings, remote, targets, mirror).handle(RemoteCommand.StopFocus)
            assertNull(settings.settings.first().focusSession)
            assertNull(remote.focusEnds.last())
        }

    @Test
    fun focusNeedsALinkedPhone() =
        runTest {
            targets.target = null
            assertFailsWith<IllegalStateException> {
                StartFocusHandler(settings, remote, targets, clock, mirror).handle(RemoteCommand.StartFocus(5))
            }
            assertEquals(AppSettings(), settings.settings.first())
        }

    @Test
    fun resettingTheLinkEndsTheParentsRules() {
        val store =
            object : ParentLinkStore {
                override var parentName: String? = "Asha"
                override var parentUid: String? = "parent-1"
                override var deviceId: String? = "device-1"
            }
        var cleared = 0
        val noControl =
            object : MonitoringControl {
                override fun sync() = Unit
            }
        LinkReset(store, noControl, RulesCleaner { cleared++ }).reset(target)
        assertEquals(1, cleared)
        assertNull(store.parentUid)
        // A reset for a device that is no longer the linked one changes nothing.
        LinkReset(store, noControl, RulesCleaner { cleared++ }).reset(DeviceTarget("other", "x"))
        assertEquals(1, cleared)
    }

    @Test
    fun nothingTheParentSendsAppliesAfterTheUnlink() =
        runTest {
            mirror.apply(listOf(block("a")))
            links.parentUid = null
            mirror.clear()
            // A snapshot, a sync and commands that arrive late are all refused.
            mirror.apply(listOf(block("late")))
            remote.blocks.value = listOf(block("late"))
            assertFailsWith<IllegalStateException> { mirror.syncNow(target) }
            assertFailsWith<IllegalStateException> {
                BlockAppHandler(locked, mirror).handle(RemoteCommand.BlockApp("com.example.a"))
            }
            assertTrue(local().isEmpty())
            assertTrue(locked.observeAll().first().isEmpty())
        }

    @Test
    fun rulesOfAnOldParentAreNotAppliedAfterRelinking() =
        runTest {
            val old = target
            links.parentUid = "parent-2"
            links.deviceId = "device-2"
            remote.blocks.value = listOf(block("old"))
            assertFailsWith<IllegalStateException> { mirror.syncNow(old) }
            mirror.apply(listOf(block("old")), old)
            assertTrue(local().isEmpty())
            mirror.apply(listOf(block("new")), DeviceTarget("parent-2", "device-2"))
            assertEquals(listOf(block("new")), local())
        }

    @Test
    fun syncBlocksHandlerPullsTheBlocks() =
        runTest {
            remote.blocks.value = listOf(block("z"))
            val handler: CommandHandler = SyncBlocksHandler(mirror, targets)
            handler.handle(RemoteCommand.SyncBlocks)
            assertEquals(listOf(block("z")), local())
        }
}
