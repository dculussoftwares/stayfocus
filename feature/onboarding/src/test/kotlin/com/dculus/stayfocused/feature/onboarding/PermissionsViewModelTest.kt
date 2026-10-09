package com.dculus.stayfocused.feature.onboarding

import app.cash.turbine.test
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {
    private class FakePermissions(
        initial: Permissions = NONE,
    ) : PermissionsRepository {
        val state = MutableStateFlow(initial)

        override fun observe(): Flow<Permissions> = state

        override fun current(): Permissions = state.value

        override fun needsRestrictedSettingsGuidance(): Boolean = false
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun rowsFollowTheRepositoryLive() =
        runTest {
            val repo = FakePermissions()
            val viewModel = PermissionsViewModel(repo)
            viewModel.permissions.test {
                assertEquals(0, awaitItem().grantedCount())
                repo.state.value = NONE.copy(usage = true)
                assertEquals(1, awaitItem().grantedCount())
                repo.state.value = NONE.copy(usage = true, overlay = true, notifications = true)
                assertEquals(3, awaitItem().grantedCount())
            }
        }

    @Test
    fun batteryDoesNotCountTowardsTheFour() {
        assertEquals(0, NONE.copy(batteryUnrestricted = true).grantedCount())
        assertEquals(4, ALL.grantedCount())
    }

    @Test
    fun notificationsAskOnceThenOpenSettings() {
        val viewModel = PermissionsViewModel(FakePermissions())
        assertEquals(NotificationStep.RequestRuntime, viewModel.nextNotificationStep(runtimePermissionExists = true))
        assertEquals(NotificationStep.OpenSettings, viewModel.nextNotificationStep(runtimePermissionExists = true))
    }

    @Test
    fun notificationsOpenSettingsWhenThereIsNoRuntimePermission() {
        val viewModel = PermissionsViewModel(FakePermissions())
        assertEquals(NotificationStep.OpenSettings, viewModel.nextNotificationStep(runtimePermissionExists = false))
    }

    private companion object {
        val NONE = Permissions(usage = false, accessibility = false, overlay = false, notifications = false)
        val ALL = Permissions(usage = true, accessibility = true, overlay = true, notifications = true)
    }
}
