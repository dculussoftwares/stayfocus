package com.dculus.stayfocused.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Harness for future migrations: create the DB at an old version from its exported schema, then
 * `runMigrationsAndValidate(name, newVersion, true, MIGRATION_x_y)`. For now it proves v1 opens and validates.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StayFocusedDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `schema v1 can be created and validated`() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 1, true).close()
    }

    @Test
    fun `v1 to v2 adds firstAfterUnlock and keeps cached usage`() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO usage_day (date, pkg, foregroundMs, opens) VALUES (20000, 'a', 1, 1)")
            db.execSQL("INSERT INTO usage_totals (date, totalMs, opens, unlocks) VALUES (20000, 5, 1, 2)")
            db.execSQL("INSERT INTO usage_hour (date, hour, foregroundMs, unlocks) VALUES (20000, 9, 5, 2)")
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, StayFocusedDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT opens, firstAfterUnlock FROM usage_day").use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(0, it.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM usage_totals").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM usage_hour").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
