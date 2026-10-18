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
    private val alerts = FakeChildAlertsRepository()
    private val watcher = PermissionWatcher(checks, EngineHealthMonitor(TestClock()), alerts, signedIn)
    private val raised = mutableSetOf<String>()

    @Test
    fun nothingIsRaisedWhileEverythingIsGranted() =
        runTest {
            watcher.check(target, raised)
            assertEquals(emptyList(), alerts.calls)
        }

    @Test
    fun aLostAccessibilityServiceRaisesOneAlertEvenAfterManyChecks() =
        runTest {
            current = granted.copy(accessibility = false)
            repeat(5) { watcher.check(target, raised) }
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.calls)
            assertEquals(listOf(AlertPermission.ACCESSIBILITY), alerts.open)
        }

    @Test
    fun everyLostPermissionGetsItsOwnAlert() =
        runTest {
            current = Permissions(usage = false, accessibility = false, overlay = false, notifications = false)
            watcher.check(target, raised)
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
            current = granted.copy(batteryUnrestricted = false)
            watcher.check(target, raised)
            assertEquals(emptyList(), alerts.calls)
        }

    @Test
    fun aPermissionThatCameBackAndIsLostAgainIsAnotherAlert() =
        runTest {
            current = granted.copy(usage = false)
            watcher.check(target, raised)
            alerts.dismiss(AlertPermission.USAGE)
            current = granted
            watcher.check(target, raised)
            current = granted.copy(usage = false)
            watcher.check(target, raised)
            assertEquals(listOf(AlertPermission.USAGE, AlertPermission.USAGE), alerts.calls)
            assertEquals(listOf(AlertPermission.USAGE), alerts.open)
        }

    @Test
    fun aFailedWriteIsTriedAgainAtTheNextCheck() =
        runTest {
            current = granted.copy(accessibility = false)
            alerts.failing = true
            watcher.check(target, raised)
            assertEquals(emptyList(), alerts.open)
            alerts.failing = false
            watcher.check(target, raised)
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
