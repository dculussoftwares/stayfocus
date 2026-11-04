package com.dculus.vesper.core.blocking.standard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.dculus.vesper.core.blocking.BlockingLog
import com.dculus.vesper.core.blocking.engine.EngineMode
import com.dculus.vesper.core.blocking.engine.EngineModeArbiter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether this app declares [BlockingService]; the phone app does, the Kids app does not. */
fun interface StandardSupport {
    fun declared(): Boolean
}

class AndroidStandardSupport
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : StandardSupport {
        override fun declared(): Boolean =
            try {
                context.packageManager.getServiceInfo(ComponentName(context, BlockingService::class.java), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
    }

/**
 * Starts [BlockingService] when the engine mode is [EngineMode.STANDARD]. Call it from the foreground (app start,
 * `MainActivity.onResume`) or from `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`: Android 12+ refuses a foreground
 * service start from the background otherwise, and Android 15 only allows it there for an app that already shows an
 * overlay window. A refused start is logged and tried again at the next app open.
 */
@Singleton
class StandardEngineStarter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val arbiter: EngineModeArbiter,
    ) {
        fun sync() {
            if (arbiter.refresh() != EngineMode.STANDARD) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, BlockingService::class.java))
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException,
            ) {
                // ForegroundServiceStartNotAllowedException (API 31+) is a RuntimeException.
                BlockingLog.log("Starter", "could not start the blocking service: ${e.message}")
            }
        }
    }
