package com.dculus.stayfocused.feature.devices

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.sync.CommandSender
import com.dculus.stayfocused.core.sync.CommandType
import com.dculus.stayfocused.core.sync.DeviceApp
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import com.dculus.stayfocused.core.sync.FakeCommandRepository
import com.dculus.stayfocused.core.sync.FakeDeviceUnlinkRepository
import com.dculus.stayfocused.core.sync.FakeRemoteDeviceRepository
import com.dculus.stayfocused.core.sync.FakeRequestDecisionRepository
import com.dculus.stayfocused.core.sync.UnlinkResult
import com.dculus.stayfocused.core.sync.UsageAppEntry
import com.dculus.stayfocused.core.sync.UsageSummary
import com.dculus.stayfocused.core.testing.FakeLinkedDevicesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteDeviceViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:30:00Z"), ZoneOffset.UTC)
    private val devices = FakeLinkedDevicesRepository(listOf(device(id = "kid")))
    private val remote = FakeRemoteDeviceRepository()
    private val commands = FakeCommandRepository()
    private val unlinker = FakeDeviceUnlinkRepository()
    private val decisions = FakeRequestDecisionRepository()
    private val auth = FakeAuthRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModel(
        deviceId: String = "kid",
        signedIn: Boolean = true,
    ): RemoteDeviceViewModel {
        if (signedIn) auth.signInWithEmail("p@example.com", "pw")
        return RemoteDeviceViewModel(
            SavedStateHandle(mapOf("deviceId" to deviceId)),
            devices,
            remote,
            CommandSender(commands),
            unlinker,
            decisions,
            auth,
            clock,
        )
    }

    private suspend fun RemoteDeviceViewModel.ready(): RemoteUiState.Ready {
        var result: RemoteUiState.Ready? = null
        state.test {
            var item = awaitItem()
            while (item !is RemoteUiState.Ready) item = awaitItem()
            result = item
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @Test
    fun showsTheDeviceItsAppsAndTodaysUsageBusiestFirst() =
        runTest {
            remote.apps.value =
                listOf(DeviceApp("a.yt", "YouTube"), DeviceApp("a.ig", "Instagram"), DeviceApp("a.wa", "WhatsApp"))
            remote.usage.value =
                UsageSummary(
                    "2026-10-17",
                    200,
                    listOf(UsageAppEntry("a.ig", "Instagram", 40, 3), UsageAppEntry("a.yt", "YouTube", 80, 2)),
                )
            val ready = viewModel().ready()
            assertEquals("Aarav's phone", ready.device.name)
            assertEquals(listOf("YouTube", "Instagram", "WhatsApp"), ready.apps.map { it.label })
            assertEquals(listOf(1f, 0.5f, 0f), ready.apps.map { it.fraction })
            assertEquals(200, ready.todayMins)
        }

    @Test
    fun lockedAppsComeFromTheLockState() =
        runTest {
            remote.apps.value = listOf(DeviceApp("a.yt", "YouTube"), DeviceApp("a.ig", "Instagram"))
            remote.locked.value = setOf("a.ig")
            val ready = viewModel().ready()
            assertEquals(mapOf("Instagram" to true, "YouTube" to false), ready.apps.associate { it.label to it.locked })
        }

    @Test
    fun theChildsNewestUsageDayCountsAsTodayWithinADayEitherWay() {
        assertTrue(isAroundToday("2026-10-17", "2026-10-17"))
        assertTrue(isAroundToday("2026-10-18", "2026-10-17"))
        assertTrue(isAroundToday("2026-10-16", "2026-10-17"))
        assertEquals(false, isAroundToday("2026-10-10", "2026-10-17"))
        assertEquals(false, isAroundToday("garbage", "2026-10-17"))
    }

    @Test
    fun aPhoneOfflineForADayShowsADerivedOfflineCardThatDismissesLocally() =
        runTest {
            val seen = Instant.parse("2026-10-16T08:15:00Z")
            devices.devices.value =
                listOf(device(id = "kid", online = false).copy(lastSeen = seen, longOffline = true))
            val viewModel = viewModel()
            val card = viewModel.ready().alerts.single()
            assertEquals(DERIVED_OFFLINE_ID, card.id)
            assertEquals(TamperKind.OFFLINE, card.kind)
            assertEquals("08:15", card.time)
            viewModel.dismiss(card)
            assertEquals(emptyList(), viewModel.ready().alerts)
            assertEquals(emptyList(), remote.dismissed, "nothing is written: no document stands behind the card")
        }

    @Test
    fun aPhoneOfflineForLessThanADayHasNoOfflineCard() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", online = false))
            assertEquals(emptyList(), viewModel().ready().alerts)
        }

    @Test
    fun usageFromAnOldDayIsNotShownAsToday() =
        runTest {
            remote.usage.value = UsageSummary("2026-10-01", 300, emptyList())
            devices.devices.value = listOf(device(id = "kid").copy(todayMins = null))
            assertEquals(null, viewModel().ready().todayMins)
        }

    @Test
    fun alertsShowLocalTimeAndRequestsShowTheirAge() =
        runTest {
            devices.devices.value =
                listOf(
                    device(
                        id = "kid",
                        alerts = listOf(alert().copy(createdAt = Instant.parse("2026-10-17T08:42:00Z"))),
                        requests = listOf(request().copy(createdAt = Instant.parse("2026-10-17T10:27:30Z"))),
                    ),
                )
            val ready = viewModel().ready()
            assertEquals("08:42", ready.alerts.single().time)
            assertEquals(2, ready.requests.single().agoMins)
            assertEquals(108, ready.alerts.single().agoMins)
        }

    @Test
    fun dismissedAlertsAreNotShown() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", alerts = listOf(alert(dismissed = true))))
            assertTrue(viewModel().ready().alerts.isEmpty())
        }

    @Test
    fun aPhoneThatIsNotLinkedIsNotFound() =
        runTest {
            val viewModel = viewModel(deviceId = "other")
            viewModel.state.test {
                var item = awaitItem()
                if (item == RemoteUiState.Loading) item = awaitItem()
                assertEquals(RemoteUiState.NotFound, item)
            }
        }

    @Test
    fun signedOutThereIsNothingToShow() =
        runTest {
            val viewModel = viewModel(signedIn = false)
            viewModel.state.test {
                var item = awaitItem()
                if (item == RemoteUiState.Loading) item = awaitItem()
                assertEquals(RemoteUiState.NotFound, item)
            }
        }

    @Test
    fun lockingAnAppSendsBlockAppAndToasts() =
        runTest {
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.messages.test {
                viewModel.setLocked("Instagram", "a.ig", true)
                assertEquals(RemoteEvent.AppLocked("Instagram", "Aarav's phone"), awaitItem())
                viewModel.setLocked("Instagram", "a.ig", false)
                assertEquals(RemoteEvent.AppUnlocked("Instagram", "Aarav's phone"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(CommandType.BLOCK_APP.name, CommandType.UNBLOCK_APP.name),
                commands.sent.map { it.type },
            )
            assertEquals(mapOf("pkg" to "a.ig"), commands.sent.first().payload)
        }

    @Test
    fun focusStartAndStopAreCommands() =
        runTest {
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.startFocus(50)
            viewModel.stopFocus()
            assertEquals(
                listOf(CommandType.START_FOCUS.name, CommandType.STOP_FOCUS.name),
                commands.sent.map { it.type },
            )
            assertEquals(mapOf("mins" to 50), commands.sent.first().payload)
        }

    @Test
    fun lockAllAndUnlockAllSendTheirCommandsWithAnEmptyPayload() =
        runTest {
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.lockAll()
            viewModel.unlockAll()
            assertEquals(
                listOf(CommandType.LOCK_ALL.name, CommandType.UNLOCK_ALL.name),
                commands.sent.map { it.type },
            )
            assertEquals(listOf(emptyMap<String, Any>(), emptyMap()), commands.sent.map { it.payload })
        }

    @Test
    fun remindSendsTheAlertsPermission() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", alerts = listOf(alert())))
            val viewModel = viewModel()
            val card = viewModel.ready().alerts.single()
            viewModel.remind(card)
            assertEquals(CommandType.REMIND_PERMISSION.name, commands.sent.single().type)
            assertEquals(mapOf("permission" to "accessibility"), commands.sent.single().payload)
            assertEquals(TamperKind.PERMISSION_LOST, card.kind)
        }

    @Test
    fun dismissWritesTheAlertAndABlockToggleWritesTheBlock() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", alerts = listOf(alert())))
            val viewModel = viewModel()
            val card = viewModel.ready().alerts.single()
            viewModel.dismiss(card)
            viewModel.setBlockEnabled("b1", false)
            assertEquals(listOf(card.id), remote.dismissed)
            assertEquals(listOf("b1" to false), remote.blockWrites)
        }

    @Test
    fun approvingARequestRecordsTheDecisionAndShowsTheToast() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", requests = listOf(request())))
            val viewModel = viewModel()
            val card = viewModel.ready().requests.single()
            viewModel.messages.test {
                viewModel.decide(card, 30)
                assertEquals(RemoteEvent.RequestApproved("Aarav's phone", 30), awaitItem())
            }
            assertEquals(listOf(card.id to 30), decisions.decisions)
        }

    @Test
    fun denyingARequestRecordsNoAndShowsTheToast() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", requests = listOf(request())))
            val viewModel = viewModel()
            val card = viewModel.ready().requests.single()
            viewModel.messages.test {
                viewModel.decide(card, 0)
                assertEquals(RemoteEvent.RequestDenied, awaitItem())
            }
            assertEquals(listOf(card.id to 0), decisions.decisions)
        }

    @Test
    fun aRefusedDecisionIsReported() =
        runTest {
            devices.devices.value = listOf(device(id = "kid", requests = listOf(request())))
            decisions.failing = true
            val viewModel = viewModel()
            val card = viewModel.ready().requests.single()
            viewModel.messages.test {
                viewModel.decide(card, 15)
                assertEquals(RemoteEvent.RequestApproved("Aarav's phone", 15), awaitItem())
                assertEquals(RemoteEvent.Failed("Aarav's phone"), awaitItem())
            }
        }

    @Test
    fun minutesAreLabelledLikeThePrototype() {
        assertEquals("15m", minutesLabel(15))
        assertEquals("1h", minutesLabel(60))
        assertEquals("1h30", minutesLabel(90))
    }

    @Test
    fun aRefusedWriteIsReported() =
        runTest {
            remote.failing = true
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.messages.test {
                viewModel.setBlockEnabled("b1", true)
                assertEquals(RemoteEvent.Failed("Aarav's phone"), awaitItem())
            }
        }

    @Test
    fun aRejectedCommandIsReported() =
        runTest {
            commands.rejectSends = true
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.messages.test {
                viewModel.stopFocus()
                assertEquals(RemoteEvent.FocusStopped("Aarav's phone"), awaitItem())
                assertEquals(RemoteEvent.Failed("Aarav's phone"), awaitItem())
            }
        }

    @Test
    fun aRejectedLockAllIsReportedLikeAnyCommand() =
        runTest {
            commands.rejectSends = true
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.messages.test {
                viewModel.lockAll()
                assertEquals(RemoteEvent.AllLocked("Aarav's phone"), awaitItem())
                assertEquals(RemoteEvent.Failed("Aarav's phone"), awaitItem())
                commands.rejectSends = true
                viewModel.unlockAll()
                assertEquals(RemoteEvent.AllUnlocked, awaitItem())
                assertEquals(RemoteEvent.Failed("Aarav's phone"), awaitItem())
            }
        }

    @Test
    fun unlinkCallsTheFunctionOnceAndReportsTheOutcome() =
        runTest {
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.messages.test {
                viewModel.unlink()
                assertEquals(RemoteEvent.Unlinked("Aarav's phone"), awaitItem())
                unlinker.result = UnlinkResult.Network
                viewModel.unlink()
                assertEquals(RemoteEvent.UnlinkFailed("Aarav's phone"), awaitItem())
            }
            assertEquals(listOf<Pair<String, String?>>("kid" to null, "kid" to null), unlinker.calls)
        }

    @Test
    fun unlinkIsIgnoredWhileOneIsInFlight() =
        runTest {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            unlinker.gate = gate
            val viewModel = viewModel()
            viewModel.ready()
            viewModel.unlink()
            viewModel.unlink()
            assertEquals(1, unlinker.calls.size)
            gate.complete(Unit)
        }
}
