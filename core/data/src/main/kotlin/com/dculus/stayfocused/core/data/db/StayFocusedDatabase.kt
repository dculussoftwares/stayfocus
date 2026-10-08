package com.dculus.stayfocused.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        /**
         * Adds `usage_day.firstAfterUnlock` and empties the usage cache, so every past day is re-aggregated
         * from system events (the old rows have no first-after-unlock data).
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE usage_day ADD COLUMN firstAfterUnlock INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("DELETE FROM usage_day")
                    db.execSQL("DELETE FROM usage_hour")
                    db.execSQL("DELETE FROM usage_totals")
                }
            }
    }
}
