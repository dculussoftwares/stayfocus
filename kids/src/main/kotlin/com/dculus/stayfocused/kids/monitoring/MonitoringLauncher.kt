package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/** Starts or stops monitoring to match the link state; call it after (un)linking and on app start. */
interface MonitoringControl {
    fun sync()
}

/** Starts or stops [MonitoringService] to match the link state. */
class MonitoringLauncher
    @Inject
    internal constructor(
        @ApplicationContext private val context: Context,
        private val linkStore: ParentLinkStore,
    ) : MonitoringControl {
        override fun sync() {
            val intent = Intent(context, MonitoringService::class.java)
            if (shouldMonitor(
                    linkStore.parentName,
                    MonitoringService.noticeVisible(context),
                )
            ) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: RuntimeException,
                ) {
                    // Android 12+ refuses a foreground start from a background process start; the boot receiver
                    // and the next app open try again.
                    Timber.w(e, "Could not start the monitoring service")
                }
            } else {
                context.stopService(intent)
            }
        }
    }
