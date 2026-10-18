package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionChecks
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AlertPermission
import com.dculus.stayfocused.core.sync.ChildAlertsRepository
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import timber.log.Timber
import javax.inject.Inject

/** The wire names of the permissions that [permissions] reports as off. Battery optimisation is informational. */
internal fun lostPermissions(permissions: Permissions): List<String> =
    buildList {
        if (!permissions.accessibility) add(AlertPermission.ACCESSIBILITY)
        if (!permissions.usage) add(AlertPermission.USAGE)
        if (!permissions.overlay) add(AlertPermission.OVERLAY)
        if (!permissions.notifications) add(AlertPermission.NOTIFICATIONS)
    }

/**
 * Tells the parent when this phone loses a permission blocking depends on. It checks on every heartbeat and straight
 * away when the accessibility service binds or unbinds, and raises one alert per permission each time it goes from
 * granted to lost; the repository also keeps a single open alert per permission across restarts. A failed write is
 * tried again at the next check.
 */
class PermissionWatcher
    @Inject
    internal constructor(
        private val checks: PermissionChecks,
        private val health: EngineHealthMonitor,
        private val alerts: ChildAlertsRepository,
        private val deviceAuth: DeviceAuth,
    ) {
        /** Runs until cancelled. */
        suspend fun run(target: DeviceTarget) {
            while (deviceAuth.ensureSignedIn() == null) {
                Timber.w("Not watching permissions yet: no anonymous sign-in")
                delay(ReportCadence.RETRY_MILLIS)
            }
            val raised = mutableSetOf<String>()
            val triggers: Flow<Unit> =
                merge(
                    flow {
                        while (true) {
                            emit(Unit)
                            delay(ReportCadence.HEARTBEAT_MILLIS)
                        }
                    },
                    // The service connecting or being switched off is the moment the user changed something.
                    health.health
                        .map { it.serviceBound }
                        .distinctUntilChanged()
                        .map { },
                )
            triggers.collect { check(target, raised) }
        }

        /** One check; [raised] holds the permissions already announced while they stay off. */
        @Suppress("TooGenericExceptionCaught")
        internal suspend fun check(
            target: DeviceTarget,
            raised: MutableSet<String>,
        ) {
            val lost = lostPermissions(checks.read())
            // A permission that came back may be lost again later: that is a new alert.
            raised.retainAll(lost.toSet())
            for (permission in lost) {
                if (permission in raised) continue
                try {
                    alerts.raisePermissionLost(target, permission)
                    raised += permission
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Reporting a lost permission failed; will retry")
                }
            }
        }
    }
