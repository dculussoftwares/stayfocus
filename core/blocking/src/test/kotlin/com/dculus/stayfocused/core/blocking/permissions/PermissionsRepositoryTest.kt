package com.dculus.stayfocused.core.blocking.permissions

import android.content.Intent
import android.provider.Settings
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.Permissions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeChecks(
    var state: Permissions = Permissions(usage = false, accessibility = false, overlay = false, notifications = false),
    var installer: String? = null,
) : PermissionChecks {
    override fun read() = state

    override fun installerPackage() = installer
}

private class FakeResume : ResumeSignal {
    val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    // Same contract as the Android implementation: one signal on subscription, then one per resume.
    override fun resumes() = signal.onSubscription { emit(Unit) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsRepositoryTest {
    private val checks = FakeChecks()
    private val resume = FakeResume()

    private fun repo(sdk: Int = 34) = DefaultPermissionsRepository(checks, resume, SdkLevel(sdk))

    @Test fun emitsCurrentStateThenRefreshesOnResume() =
        runTest {
            repo().observe().test {
                assertFalse(awaitItem().usage)
                checks.state = checks.state.copy(usage = true)
                resume.signal.emit(Unit)
                assertTrue(awaitItem().usage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun resumeWithoutChangeDoesNotReEmit() =
        runTest {
            repo().observe().test {
                awaitItem()
                resume.signal.emit(Unit)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun batteryIsNotGating() {
        val all = Permissions(usage = true, accessibility = true, overlay = true, notifications = true)
        assertTrue(all.allGatingGranted)
        assertTrue(all.copy(batteryUnrestricted = false).allGatingGranted)
        assertFalse(all.copy(overlay = false, batteryUnrestricted = true).allGatingGranted)
    }

    @Test fun guidanceOnlyForHandInstalledAndroid13PlusWithoutAccessibility() {
        checks.installer = "com.google.android.packageinstaller"
        assertTrue(repo(33).needsRestrictedSettingsGuidance())
        assertFalse(repo(32).needsRestrictedSettingsGuidance())
        checks.installer = "com.android.vending"
        assertFalse(repo(34).needsRestrictedSettingsGuidance())
        checks.installer = "com.sec.android.app.samsungapps"
        assertFalse(repo(34).needsRestrictedSettingsGuidance())
        checks.installer = null // ambiguous (adb or removed installer): err towards showing the guidance
        assertTrue(repo(34).needsRestrictedSettingsGuidance())
        checks.installer = "com.google.android.packageinstaller"
        checks.state = checks.state.copy(accessibility = true)
        assertFalse(repo(34).needsRestrictedSettingsGuidance())
    }

    @Test fun intentsTargetTheRightScreens() {
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, PermissionIntents.usageAccess().action)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, PermissionIntents.accessibility().action)
        val overlay = PermissionIntents.overlay("com.example")
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlay.action)
        assertEquals("package:com.example", overlay.data.toString())
        val battery = PermissionIntents.batteryOptimizationSettings()
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, battery.action)
        assertTrue(battery.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(
            "com.example",
            PermissionIntents.notificationSettings("com.example").getStringExtra(Settings.EXTRA_APP_PACKAGE),
        )
    }

    @Test fun notificationRuntimeRequestOnlyOnAndroid13Plus() {
        assertNull(PermissionIntents.notificationRuntimePermission(32))
        assertEquals("android.permission.POST_NOTIFICATIONS", PermissionIntents.notificationRuntimePermission(33))
    }
}
