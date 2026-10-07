package com.dculus.stayfocused.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        BlockEntity::class,
        BlockAppEntity::class,
        LockedAppEntity::class,
        CycleStateEntity::class,
        BlockEventEntity::class,
        UsageDayEntity::class,
        UsageHourEntity::class,
        UsageTotalsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StayFocusedDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao

    abstract fun lockedAppDao(): LockedAppDao

    abstract fun cycleStateDao(): CycleStateDao

    abstract fun blockEventDao(): BlockEventDao

    abstract fun usageDao(): UsageDao

    companion object {
        const val NAME = "stayfocused.db"
    }
}
