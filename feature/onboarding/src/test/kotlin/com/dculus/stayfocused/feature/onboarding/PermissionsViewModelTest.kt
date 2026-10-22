package com.dculus.stayfocused.feature.onboarding

import app.cash.turbine.test
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.testing.FakeAccountProfileRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
            val viewModel = PermissionsViewModel(repo, FakeSettingsRepository(), FakeAccountProfileRepository(), CLOCK)
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
        val viewModel =
            PermissionsViewModel(FakePermissions(), FakeSettingsRepository(), FakeAccountProfileRepository(), CLOCK)
        assertEquals(NotificationStep.RequestRuntime, viewModel.nextNotificationStep(runtimePermissionExists = true))
        assertEquals(NotificationStep.OpenSettings, viewModel.nextNotificationStep(runtimePermissionExists = true))
    }

    @Test
    fun notificationsOpenSettingsWhenThereIsNoRuntimePermission() {
        val viewModel =
            PermissionsViewModel(FakePermissions(), FakeSettingsRepository(), FakeAccountProfileRepository(), CLOCK)
        assertEquals(NotificationStep.OpenSettings, viewModel.nextNotificationStep(runtimePermissionExists = false))
    }

    @Test
    fun agreeingStoresTheConsentTime() =
        runTest {
            val settings = FakeSettingsRepository()
            val viewModel = PermissionsViewModel(FakePermissions(), settings, FakeAccountProfileRepository(), CLOCK)
            assertEquals(null, settings.settings.first().accessibilityConsentAt)
            assertEquals(true, viewModel.recordAccessibilityConsent())
            assertEquals(Instant.parse("2026-01-02T03:04:05Z"), settings.settings.first().accessibilityConsentAt)
        }

    @Test
    fun firstNameIsTrimmedAndBlankMeansNone() =
        runTest {
            val profile = FakeAccountProfileRepository("  Sam ")
            val viewModel = PermissionsViewModel(FakePermissions(), FakeSettingsRepository(), profile, CLOCK)
            viewModel.firstName.test {
                assertEquals("Sam", awaitItem())
                profile.firstName.value = "  "
                assertEquals(null, awaitItem())
            }
        }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)
        val NONE = Permissions(usage = false, accessibility = false, overlay = false, notifications = false)
        val ALL = Permissions(usage = true, accessibility = true, overlay = true, notifications = true)
    }
}
