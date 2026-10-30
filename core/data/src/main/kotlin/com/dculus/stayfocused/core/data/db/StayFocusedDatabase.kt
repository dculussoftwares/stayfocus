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
    version = 4,
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
         * Adds `usage_day.firstAfterUnlock`. Existing cached days are kept (their per-app unlock counts read as 0
         * until the day leaves the 7-day window); days cached from now on carry the real value.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE usage_day ADD COLUMN firstAfterUnlock INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** Adds `blocks.cycleScope`; every existing "use, then lock" block keeps its per-app windows. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE blocks ADD COLUMN cycleScope TEXT NOT NULL DEFAULT 'PER_APP'")
                }
            }

        /**
         * Adds `usage_day.lastUsedAt` and `lastSessionMs`; days cached earlier show no "last used" until they expire.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE usage_day ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE usage_day ADD COLUMN lastSessionMs INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}
