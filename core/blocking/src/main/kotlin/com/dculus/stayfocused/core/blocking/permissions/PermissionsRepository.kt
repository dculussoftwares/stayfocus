package com.dculus.stayfocused.core.blocking.permissions

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.core.blocking.AccessibilityStatus
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.usage.UsageAccess
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Live state of the permissions Stay Focused needs. */
interface PermissionsRepository {
    /** Emits the current state, then again whenever the app returns to the foreground and something changed. */
    fun observe(): Flow<Permissions>

    /** One-off read, e.g. after a runtime permission dialog closes. */
    fun current(): Permissions

    /** Whether to show the "Allow restricted settings" guidance before opening Accessibility. */
    fun needsRestrictedSettingsGuidance(): Boolean
}

/** Reads the system state of each permission. */
interface PermissionChecks {
    fun read(): Permissions

    fun installerPackage(): String?
}

/**
 * Emits once as soon as it is listening (so the first read can never miss a resume that races with start-up),
 * then each time an activity of the app resumes (the user came back, e.g. from a settings screen).
 */
fun interface ResumeSignal {
    fun resumes(): Flow<Unit>
}

/** The running API level, injectable so tests can pick one. */
data class SdkLevel(
    val value: Int,
)

class DefaultPermissionsRepository
    @Inject
    constructor(
        private val checks: PermissionChecks,
        private val resume: ResumeSignal,
        private val sdk: SdkLevel,
    ) : PermissionsRepository {
        override fun observe(): Flow<Permissions> =
            resume
                .resumes()
                .map { checks.read() }
                .distinctUntilChanged()

        override fun current(): Permissions = checks.read()

        override fun needsRestrictedSettingsGuidance(): Boolean =
            RestrictedSettings.needsGuidance(sdk.value, checks.installerPackage(), checks.read().accessibility)
    }

class AndroidPermissionChecks
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val usage: UsageAccess,
    ) : PermissionChecks {
        override fun read(): Permissions =
            Permissions(
                usage = usage.isGranted(),
                accessibility = AccessibilityStatus.isEnabled(context),
                overlay = Settings.canDrawOverlays(context),
                notifications = notificationsGranted(),
                batteryUnrestricted = batteryUnrestricted(),
            )

        private fun notificationsGranted(): Boolean {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val runtimeOk =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, PermissionIntents.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            return runtimeOk && manager.areNotificationsEnabled()
        }

        private fun batteryUnrestricted(): Boolean {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return power.isIgnoringBatteryOptimizations(context.packageName)
        }

        override fun installerPackage(): String? =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
            }.getOrNull()
    }

@Singleton
class ActivityResumeSignal
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ResumeSignal {
        override fun resumes(): Flow<Unit> =
            callbackFlow {
                val app = context.applicationContext as Application
                val callbacks =
                    object : Application.ActivityLifecycleCallbacks {
                        override fun onActivityResumed(activity: Activity) {
                            trySend(Unit)
                        }

                        override fun onActivityCreated(
                            activity: Activity,
                            savedInstanceState: Bundle?,
                        ) = Unit

                        override fun onActivityStarted(activity: Activity) = Unit

                        override fun onActivityPaused(activity: Activity) = Unit

                        override fun onActivityStopped(activity: Activity) = Unit

                        override fun onActivitySaveInstanceState(
                            activity: Activity,
                            outState: Bundle,
                        ) = Unit

                        override fun onActivityDestroyed(activity: Activity) = Unit
                    }
                app.registerActivityLifecycleCallbacks(callbacks)
                // Registered first, so a resume after this point is queued behind this initial signal.
                trySend(Unit)
                awaitClose { app.unregisterActivityLifecycleCallbacks(callbacks) }
            }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PermissionsModule {
    @Binds
    abstract fun repository(impl: DefaultPermissionsRepository): PermissionsRepository

    @Binds
    abstract fun checks(impl: AndroidPermissionChecks): PermissionChecks

    @Binds
    abstract fun resume(impl: ActivityResumeSignal): ResumeSignal

    companion object {
        @Provides
        fun sdk(): SdkLevel = SdkLevel(Build.VERSION.SDK_INT)
    }
}
