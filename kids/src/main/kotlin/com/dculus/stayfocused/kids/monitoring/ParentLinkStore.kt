package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the monitoring service needs to know about the link: the parent's display name, or null while this phone is
 * not linked. The linking flow (consent screen) writes it and calls [MonitoringLauncher.sync]; the service and the
 * boot receiver only read it. Plain preferences (not DataStore) because the boot receiver needs a synchronous read.
 */
interface ParentLinkStore {
    var parentName: String?

    /** The parent's account id and this phone's device id under it (`users/{parentUid}/devices/{deviceId}`). */
    var parentUid: String?
    var deviceId: String?
}

@Singleton
internal class SharedPreferencesParentLinkStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ParentLinkStore {
        private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        override var parentName: String?
            get() = read(KEY_PARENT_NAME)
            set(value) = write(KEY_PARENT_NAME, value)

        override var parentUid: String?
            get() = read(KEY_PARENT_UID)
            set(value) = write(KEY_PARENT_UID, value)

        override var deviceId: String?
            get() = read(KEY_DEVICE_ID)
            set(value) = write(KEY_DEVICE_ID, value)

        private fun read(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

        private fun write(
            key: String,
            value: String?,
        ) {
            prefs.edit().apply { if (value.isNullOrBlank()) remove(key) else putString(key, value) }.apply()
        }

        private companion object {
            const val FILE = "kids_link"
            const val KEY_PARENT_NAME = "parent_name"
            const val KEY_PARENT_UID = "parent_uid"
            const val KEY_DEVICE_ID = "device_id"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentLinkModule {
    @Binds
    abstract fun bind(impl: SharedPreferencesParentLinkStore): ParentLinkStore

    @Binds
    abstract fun bindControl(impl: MonitoringLauncher): MonitoringControl
}

/**
 * Monitoring runs exactly while a parent is linked AND the user can see the "Managed by" notification (Android 13+
 * lets them turn notifications off); nothing is ever monitored silently.
 */
internal fun shouldMonitor(
    parentName: String?,
    notificationsEnabled: Boolean = true,
): Boolean = !parentName.isNullOrBlank() && notificationsEnabled
