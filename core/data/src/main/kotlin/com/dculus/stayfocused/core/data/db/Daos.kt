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
    }

    /** Deletes the block; its apps and cycle state go with it (foreign key cascade). */
    @Query("DELETE FROM blocks WHERE id = :id")
    suspend fun delete(id: String)

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
}
