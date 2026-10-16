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
}

@Singleton
internal class SharedPreferencesParentLinkStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ParentLinkStore {
        private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

        override var parentName: String?
            get() = prefs.getString(KEY_PARENT_NAME, null)?.takeIf { it.isNotBlank() }
            set(value) {
                prefs
                    .edit()
                    .apply { if (value.isNullOrBlank()) remove(KEY_PARENT_NAME) else putString(KEY_PARENT_NAME, value) }
                    .apply()
            }

        private companion object {
            const val FILE = "kids_link"
            const val KEY_PARENT_NAME = "parent_name"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ParentLinkModule {
    @Binds
    abstract fun bind(impl: SharedPreferencesParentLinkStore): ParentLinkStore
}

/**
 * Monitoring runs exactly while a parent is linked AND the user can see the "Managed by" notification (Android 13+
 * lets them turn notifications off); nothing is ever monitored silently.
 */
internal fun shouldMonitor(
    parentName: String?,
    notificationsEnabled: Boolean = true,
): Boolean = !parentName.isNullOrBlank() && notificationsEnabled
