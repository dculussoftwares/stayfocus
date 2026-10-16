package com.dculus.stayfocused.kids.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.core.blocking.ForegroundAppTracker
import com.dculus.stayfocused.core.sync.ChildStatus
import com.dculus.stayfocused.core.sync.DeviceTarget
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where this phone reports to: the parent's uid and this device's id, written by the linking flow once the parent
 * approved the link. Null until then, and nothing is reported.
 */
interface ReportTargetStore {
    var target: DeviceTarget?
}

@Singleton
internal class SharedPreferencesReportTargetStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ReportTargetStore {
        private val prefs: SharedPreferences = context.getSharedPreferences("kids_link", Context.MODE_PRIVATE)

        override var target: DeviceTarget?
            get() {
                val parent = prefs.getString(KEY_PARENT_UID, null)?.takeIf { it.isNotBlank() }
                val device = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
                return if (parent != null && device != null) DeviceTarget(parent, device) else null
            }
            set(value) {
                prefs
                    .edit()
                    .apply {
                        if (value == null) {
                            remove(KEY_PARENT_UID)
                            remove(KEY_DEVICE_ID)
                        } else {
                            putString(KEY_PARENT_UID, value.parentUid)
                            putString(KEY_DEVICE_ID, value.deviceId)
                        }
                    }.apply()
            }

        private companion object {
            const val KEY_PARENT_UID = "parent_uid"
            const val KEY_DEVICE_ID = "device_id"
        }
    }

@Singleton
internal class AndroidStatusSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tracker: ForegroundAppTracker,
    ) : StatusSource {
        private data class Battery(
            val percent: Int?,
            val charging: Boolean,
        )

        private val battery: Flow<Battery> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context?,
                            intent: Intent?,
                        ) {
                            intent?.let { trySend(it.toBattery()) }
                        }
                    }
                // ACTION_BATTERY_CHANGED is sticky: registering returns the current state.
                val sticky =
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                sticky?.let { trySend(it.toBattery()) }
                awaitClose { context.unregisterReceiver(receiver) }
            }

        override val snapshots: Flow<ChildStatus> =
            combine(battery, tracker.foreground.map { it?.packageName }) { battery, pkg ->
                ChildStatus(
                    battery = battery.percent,
                    charging = battery.charging,
                    currentApp = pkg?.let(::labelOf),
                    online = true,
                )
            }.flowOn(Dispatchers.IO)

        /** The app's label; null when it cannot be resolved, so a package name never reaches the parent. */
        private fun labelOf(pkg: String): String? =
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (
                @Suppress("SwallowedException") e: android.content.pm.PackageManager.NameNotFoundException,
            ) {
                null
            }

        private fun Intent.toBattery(): Battery {
            val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return Battery(
                percent = if (level >= 0 && scale > 0) level * PERCENT / scale else null,
                charging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }

        private companion object {
            const val PERCENT = 100
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportingModule {
    @Binds
    abstract fun targetStore(impl: SharedPreferencesReportTargetStore): ReportTargetStore

    @Binds
    abstract fun statusSource(impl: AndroidStatusSource): StatusSource
}
