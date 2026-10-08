package com.dculus.stayfocused.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): StayFocusedDatabase =
        Room
            .databaseBuilder(context, StayFocusedDatabase::class.java, StayFocusedDatabase.NAME)
            .addMigrations(StayFocusedDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun blockDao(db: StayFocusedDatabase): BlockDao = db.blockDao()

    @Provides
    fun lockedAppDao(db: StayFocusedDatabase): LockedAppDao = db.lockedAppDao()

    @Provides
    fun cycleStateDao(db: StayFocusedDatabase): CycleStateDao = db.cycleStateDao()

    @Provides
    fun blockEventDao(db: StayFocusedDatabase): BlockEventDao = db.blockEventDao()

    @Provides
    fun usageDao(db: StayFocusedDatabase): UsageDao = db.usageDao()
}
