package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionChecks
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AlertPermission
import com.dculus.stayfocused.core.sync.ChildAlertsRepository
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The wire names of the permissions that [permissions] reports as off. Battery optimisation is informational. */
internal fun lostPermissions(permissions: Permissions): List<String> =
    buildList {
        if (!permissions.accessibility) add(AlertPermission.ACCESSIBILITY)
        if (!permissions.usage) add(AlertPermission.USAGE)
        if (!permissions.overlay) add(AlertPermission.OVERLAY)
        if (!permissions.notifications) add(AlertPermission.NOTIFICATIONS)
    }

/** Which permissions this phone has had granted at some point: only a granted one can be "lost". */
interface PermissionHistory {
    fun everGranted(): Set<String>

    fun markGranted(permissions: Collection<String>)
}

@Singleton
internal class SharedPreferencesPermissionHistory
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PermissionHistory {
        private val prefs = context.getSharedPreferences("kids_permissions", Context.MODE_PRIVATE)

        @Synchronized
        override fun everGranted(): Set<String> = prefs.getStringSet(KEY, null).orEmpty().toSet()

        @Synchronized
        override fun markGranted(permissions: Collection<String>) {
            val known = everGranted()
            if (!known.containsAll(permissions)) prefs.edit().putStringSet(KEY, known + permissions).apply()
        }

        private companion object {
            const val KEY = "ever_granted"
        }
    }

/** What one run of the watcher remembers between checks. */
internal class WatchState {
    /** Permissions already announced while they stay off. */
    val raised = mutableSetOf<String>()

    /** Permissions this run saw granted and not yet announced as lost: their next loss is a new one. */
    val sawGranted = mutableSetOf<String>()
}

/**
 * Tells the parent when this phone loses a permission blocking depends on. It checks on every heartbeat and straight
 * away when the accessibility service binds or unbinds. A permission that was never granted is not "lost" and raises
 * nothing. A loss the watcher saw happen is always announced; a permission only found off (after a restart) is
 * announced unless an alert for it is still open. A failed write is tried again at the next check.
 */
class PermissionWatcher
    @Inject
    internal constructor(
        private val checks: PermissionChecks,
        private val health: EngineHealthMonitor,
        private val alerts: ChildAlertsRepository,
        private val deviceAuth: DeviceAuth,
        private val history: PermissionHistory,
    ) {
        /** Runs until cancelled. */
        suspend fun run(target: DeviceTarget) {
            while (deviceAuth.ensureSignedIn() == null) {
                Timber.w("Not watching permissions yet: no anonymous sign-in")
                delay(ReportCadence.RETRY_MILLIS)
            }
            val state = WatchState()
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
            triggers.collect { check(target, state) }
        }

        @Suppress("TooGenericExceptionCaught")
        internal suspend fun check(
            target: DeviceTarget,
            state: WatchState,
        ) {
            val lost = lostPermissions(checks.read())
            val granted = ALL_PERMISSIONS - lost.toSet()
            history.markGranted(granted)
            state.sawGranted += granted
            val tampered = lost.filter { it in history.everGranted() }
            // A permission that came back may be lost again later: that is a new alert.
            state.raised.retainAll(tampered.toSet())
            for (permission in tampered) {
                if (permission in state.raised) continue
                try {
                    alerts.raisePermissionLost(target, permission, newLoss = permission in state.sawGranted)
                    state.raised += permission
                    state.sawGranted -= permission
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Reporting a lost permission failed; will retry")
                }
            }
        }

        private companion object {
            val ALL_PERMISSIONS =
                setOf(
                    AlertPermission.ACCESSIBILITY,
                    AlertPermission.USAGE,
                    AlertPermission.OVERLAY,
                    AlertPermission.NOTIFICATIONS,
                )
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionHistoryModule {
    @Binds
    abstract fun history(impl: SharedPreferencesPermissionHistory): PermissionHistory
}
