package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionChecks
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AlertPermission
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.FakeChildAlertsRepository
import com.dculus.stayfocused.core.sync.RemoteCommand
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PermissionWatcherTest {
    private val target = DeviceTarget("parent-1", "device-1")
    private val granted = Permissions(usage = true, accessibility = true, overlay = true, notifications = true)
    private val none = Permissions(usage = false, accessibility = false, overlay = false, notifications = false)
    private var current = granted
    private val checks =
        object : PermissionChecks {
            override fun read() = current

            override fun installerPackage(): String? = null
        }
    private val signedIn =
        object : DeviceAuth {
            override val uid = flowOf<String?>("child")

            override suspend fun ensureSignedIn() = "child"
        }
    private val history =
        object : PermissionHistory {
            val granted = mutableSetOf<String>()

            override fun everGranted(): Set<String> = granted.toSet()

            override fun markGranted(permissions: Collection<String>) {
                granted += permissions
            }
        }
    private val alerts = FakeChildAlertsRepository()

    private fun watcher() = PermissionWatcher(checks, EngineHealthMonitor(TestClock()), alerts, signedIn, history)

    private val watcher = watcher()
    private var state = WatchState()

    private suspend fun check() = watcher.check(target, state)

    @Test
    fun nothingIsRaisedWhileEverythingIsGranted() =
        runTest {
            check()
            assertEquals(emptyList(), alerts.calls)
        }

    @Test
    fun aPermissionThatWasNeverGrantedIsNotLost() =
        runTest {
            current = none
            check()
            assertEquals(emptyList(), alerts.calls)
        }

    @Test
    fun aLostAccessibilityServiceRaisesOneAlertEvenAfterManyChecks() =
        runTest {
            check()
            current = granted.copy(accessibility = false)
            repeat(5) { check() }
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.calls)
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.open)
        }

    @Test
    fun everyLostPermissionGetsItsOwnAlert() =
        runTest {
            check()
            current = none
            check()
            assertEquals(
                listOf(
                    AlertPermission.ACCESSIBILITY,
                    AlertPermission.USAGE,
                    AlertPermission.OVERLAY,
                    AlertPermission.NOTIFICATIONS,
                ),
                alerts.open,
            )
        }

    @Test
    fun theBatteryExemptionIsNotATamperAlert() =
        runTest {
            check()
            current = granted.copy(batteryUnrestricted = false)
            check()
            assertEquals(emptyList(), alerts.calls)
        }

    @Test
    fun aSecondLossIsAnotherAlertEvenIfTheFirstWasNotDismissed() =
        runTest {
            check()
            current = granted.copy(usage = false)
            check()
            current = granted
            check()
            current = granted.copy(usage = false)
            check()
            assertEquals(listOf(AlertPermission.USAGE, AlertPermission.USAGE), alerts.open)
        }

    @Test
    fun aPermissionFoundOffAfterARestartDoesNotDuplicateAnOpenAlert() =
        runTest {
            check()
            current = granted.copy(accessibility = false)
            check()
            // The service restarts: a new run knows nothing but the history on disk.
            state = WatchState()
            check()
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.open)
            alerts.dismiss(AlertPermission.ACCESSIBILITY)
            state = WatchState()
            check()
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.open)
        }

    @Test
    fun aFailedWriteIsTriedAgainAtTheNextCheck() =
        runTest {
            check()
            current = granted.copy(accessibility = false)
            alerts.failing = true
            check()
            assertEquals(emptyList(), alerts.open)
            alerts.failing = false
            check()
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.open)
        }
}

class RemindPermissionHandlerTest {
    private val reminded = mutableListOf<Pair<String, String?>>()
    private val reminder =
        object : PermissionReminder {
            override fun remind(
                permission: String,
                parentName: String?,
            ) {
                reminded += permission to parentName
            }
        }
    private val links =
        object : ParentLinkStore {
            override var parentName: String? = "Asha"
            override var parentUid: String? = "parent-1"
            override var deviceId: String? = "device-1"
        }

    @Test
    fun remindsTheChildAboutTheNamedPermission() =
        runTest {
            RemindPermissionHandler(reminder, links).handle(RemoteCommand.RemindPermission("accessibility"))
            assertEquals(listOf<Pair<String, String?>>("accessibility" to "Asha"), reminded)
        }
}
