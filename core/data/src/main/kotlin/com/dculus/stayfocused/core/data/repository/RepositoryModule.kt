package com.dculus.stayfocused.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
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
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun settingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

        @Provides
        fun clock(): Clock = Clock.systemUTC()
    }
}
