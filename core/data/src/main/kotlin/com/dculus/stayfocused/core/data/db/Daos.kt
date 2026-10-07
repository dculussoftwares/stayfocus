package com.dculus.stayfocused.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface BlockDao {
    @Transaction
    @Query("SELECT * FROM blocks WHERE target = :target ORDER BY createdAt")
    fun observeByTarget(target: String): Flow<List<BlockWithApps>>

    @Transaction
    @Query("SELECT * FROM blocks WHERE id = :id")
    fun observe(id: String): Flow<BlockWithApps?>

    @Transaction
    @Query(
        "SELECT * FROM blocks WHERE enabled = 1 AND id IN (SELECT blockId FROM block_apps WHERE pkg = :pkg) " +
            "ORDER BY createdAt",
    )
    fun observeEnabledContaining(pkg: String): Flow<List<BlockWithApps>>

    /** Upserts (not REPLACE, which would cascade-delete the join rows) the block and replaces its apps. */
    @Transaction
    suspend fun upsert(
        block: BlockEntity,
        apps: List<BlockAppEntity>,
    ) {
        upsertBlock(block)
        deleteApps(block.id)
        insertApps(apps)
        // Cycle state of apps dropped from the block must not resurface if they are re-added later.
        deleteCycleStateOfRemovedApps(block.id)
    }

    /** Deletes the block; its apps and cycle state go with it (foreign key cascade). */
    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun delete(id: String)

    /** Subquery, not a bound list: Android SQLite caps bind parameters at 999. */
    @Query(
        "DELETE FROM cycle_state WHERE blockId = :blockId " +
            "AND pkg NOT IN (SELECT pkg FROM block_apps WHERE blockId = :blockId)",
    )
    suspend fun deleteCycleStateOfRemovedApps(blockId: String)

    @Query("UPDATE blocks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    )

    @Upsert
    suspend fun upsertBlock(block: BlockEntity)

    @Query("DELETE FROM block_apps WHERE blockId = :blockId")
    suspend fun deleteApps(blockId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<BlockAppEntity>)
}

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps ORDER BY since")
    fun observeAll(): Flow<List<LockedAppEntity>>

    @Query("SELECT * FROM locked_apps WHERE target = :target ORDER BY since")
    fun observeByTarget(target: String): Flow<List<LockedAppEntity>>

    @Upsert
    suspend fun upsert(app: LockedAppEntity)

    /** Keeps the existing row (and its `since`) when the app is already locked for the target. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: LockedAppEntity)

    @Query("DELETE FROM locked_apps WHERE pkg = :pkg AND target = :target")
    suspend fun delete(
        pkg: String,
        target: String,
    )
}

@Dao
interface CycleStateDao {
    @Query("SELECT * FROM cycle_state WHERE blockId = :blockId AND pkg = :pkg")
    fun observe(
        blockId: String,
        pkg: String,
    ): Flow<CycleStateEntity?>

    @Upsert
    suspend fun upsert(state: CycleStateEntity)

    @Query("DELETE FROM cycle_state WHERE blockId = :blockId")
    suspend fun deleteForBlock(blockId: String)
}

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity): Long

    /** Events in `[from, to)`; callers pass the local day's bounds for "today's count". */
    @Query("SELECT COUNT(*) FROM block_events WHERE at >= :from AND at < :to")
    fun observeCountBetween(
        from: Instant,
        to: Instant,
    ): Flow<Int>

    @Query("SELECT * FROM block_events WHERE at >= :from AND at < :to ORDER BY at DESC")
    fun observeBetween(
        from: Instant,
        to: Instant,
    ): Flow<List<BlockEventEntity>>
}

@Suppress("TooManyFunctions")
@Dao
interface UsageDao {
    @Upsert
    suspend fun upsertDay(row: UsageDayEntity)

    @Upsert
    suspend fun upsertHour(row: UsageHourEntity)

    @Upsert
    suspend fun upsertTotals(row: UsageTotalsEntity)

    /** Inclusive on both ends. */
    @Query("SELECT * FROM usage_day WHERE date BETWEEN :from AND :to ORDER BY date, pkg")
    fun observeDays(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<UsageDayEntity>>

    @Query("SELECT * FROM usage_hour WHERE date = :date ORDER BY hour")
    fun observeHours(date: LocalDate): Flow<List<UsageHourEntity>>

    /** Inclusive on both ends. */
    @Query("SELECT * FROM usage_totals WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeTotals(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<UsageTotalsEntity>>

    /** Replaces everything cached for [totals]`.date` atomically, so a re-cached day never keeps stale rows. */
    @Transaction
    suspend fun replaceDay(
        totals: UsageTotalsEntity,
        days: List<UsageDayEntity>,
        hours: List<UsageHourEntity>,
    ) {
        deleteDay(totals.date)
        upsertTotals(totals)
        days.forEach { upsertDay(it) }
        hours.forEach { upsertHour(it) }
    }

    /** Drops every cached day strictly before [date]. */
    @Transaction
    suspend fun deleteBefore(date: LocalDate) {
        deleteTotalsBefore(date)
        deleteDaysBefore(date)
        deleteHoursBefore(date)
    }

    @Transaction
    suspend fun deleteDay(date: LocalDate) {
        deleteTotalsOn(date)
        deleteDaysOn(date)
        deleteHoursOn(date)
    }

    @Query("DELETE FROM usage_totals WHERE date = :date")
    suspend fun deleteTotalsOn(date: LocalDate)

    @Query("DELETE FROM usage_day WHERE date = :date")
    suspend fun deleteDaysOn(date: LocalDate)

    @Query("DELETE FROM usage_hour WHERE date = :date")
    suspend fun deleteHoursOn(date: LocalDate)

    @Query("DELETE FROM usage_totals WHERE date < :date")
    suspend fun deleteTotalsBefore(date: LocalDate)

    @Query("DELETE FROM usage_day WHERE date < :date")
    suspend fun deleteDaysBefore(date: LocalDate)

    @Query("DELETE FROM usage_hour WHERE date < :date")
    suspend fun deleteHoursBefore(date: LocalDate)
}
