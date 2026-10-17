package com.dculus.stayfocused.feature.devices

import app.cash.turbine.test
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TamperAlert
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.UnlockRequest
import com.dculus.stayfocused.core.model.UnlockRequestStatus
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
import java.time.Instant
import kotlin.test.assertEquals

internal fun device(
    id: String = "d1",
    name: String = "Aarav's phone",
    online: Boolean = true,
    alerts: List<TamperAlert> = emptyList(),
    requests: List<UnlockRequest> = emptyList(),
) = LinkedDevice(
    id = id,
    name = name,
    model = "Redmi Note 12 · Kids app",
    battery = 64,
    currentApp = "YouTube",
    online = online,
    lastSeen = Instant.EPOCH,
    alerts = alerts,
    requests = requests,
    focusEndsAt = null,
    todayMins = 147,
)

internal fun alert(
    kind: TamperKind = TamperKind.PERMISSION_LOST,
    permission: String? = "accessibility",
    dismissed: Boolean = false,
) = TamperAlert("a-$kind-$dismissed", kind, permission, Instant.EPOCH, dismissed)

internal fun request(status: UnlockRequestStatus = UnlockRequestStatus.PENDING) =
    UnlockRequest("r-$status", "YouTube", 15, status, Instant.EPOCH)

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsLoadingThenShowsTheLinkedPhonesAsTheyChange() =
        runTest {
            val repository = FakeLinkedDevicesRepository()
            val viewModel = DevicesViewModel(repository)
            viewModel.state.test {
                var item = awaitItem()
                if (item == DevicesUiState.Loading) item = awaitItem()
                assertEquals(DevicesUiState.Loaded(emptyList()), item)
                repository.devices.value = listOf(device())
                assertEquals(DevicesUiState.Loaded(listOf(device())), awaitItem())
                repository.devices.value = listOf(device(online = false))
                assertEquals(DevicesUiState.Loaded(listOf(device(online = false))), awaitItem())
            }
        }

    @Test
    fun badgeCountsUndismissedAlertsAndPendingRequestsOnly() {
        val d =
            device(
                alerts = listOf(alert(), alert(dismissed = true), alert(TamperKind.OFFLINE, null)),
                requests = listOf(request(), request(UnlockRequestStatus.APPROVED)),
            )
        assertEquals(3, d.badgeCount())
        assertEquals(0, device().badgeCount())
    }
}
