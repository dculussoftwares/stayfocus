package com.dculus.vesper.core.data.db

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
    ): VesperDatabase =
        Room
            .databaseBuilder(context, VesperDatabase::class.java, VesperDatabase.NAME)
            .addMigrations(
                VesperDatabase.MIGRATION_1_2,
                VesperDatabase.MIGRATION_2_3,
                VesperDatabase.MIGRATION_3_4,
            ).build()

    @Provides
    fun blockDao(db: VesperDatabase): BlockDao = db.blockDao()

    @Provides
    fun lockedAppDao(db: VesperDatabase): LockedAppDao = db.lockedAppDao()

    @Provides
    fun cycleStateDao(db: VesperDatabase): CycleStateDao = db.cycleStateDao()

    @Provides
    fun blockEventDao(db: VesperDatabase): BlockEventDao = db.blockEventDao()

    @Provides
    fun usageDao(db: VesperDatabase): UsageDao = db.usageDao()
}
