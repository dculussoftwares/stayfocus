package com.dculus.stayfocused.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dculus.stayfocused.core.data.settings.DataStoreBreakRepository
import com.dculus.stayfocused.core.data.settings.DataStoreSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * One DataStore per process, as DataStore requires: instrumented tests rebuild the Hilt component for every test, and a
 * second DataStore on the same file would crash. The file is `datastore/settings.preferences_pb`.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    abstract fun blocks(impl: RoomBlockRepository): BlockRepository

    @Binds
    abstract fun lockedApps(impl: RoomLockedAppsRepository): LockedAppsRepository

    @Binds
    abstract fun breaks(impl: DataStoreBreakRepository): BreakRepository

    @Binds
    abstract fun linkedDevices(impl: EmptyLinkedDevicesRepository): LinkedDevicesRepository

    @Binds
    abstract fun accountProfile(impl: NoAccountProfileRepository): AccountProfileRepository

    @Binds
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun settingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore

        @Provides
        fun clock(): Clock = SystemZoneClock
    }
}
