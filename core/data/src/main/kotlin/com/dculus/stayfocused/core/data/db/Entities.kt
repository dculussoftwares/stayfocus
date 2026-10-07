package com.dculus.stayfocused.core.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Target value for blocks on this phone; anything else is a linked child's deviceId. */
const val TARGET_ME = "me"

@Entity(tableName = "blocks", indices = [Index("target")])
data class BlockEntity(
    @PrimaryKey val id: String,
    /** [TARGET_ME] or a deviceId. */
    val target: String,
    val type: BlockType,
    val name: String,
    val limitMins: Int?,
    val period: LimitPeriod?,
    val useMins: Int?,
    val restMins: Int?,
    val rangeStart: LocalTime?,
    val rangeEnd: LocalTime?,
    val durationMins: Int?,
    val startedAt: Instant?,
    val days: DaysOfWeek,
    val enabled: Boolean,
    val createdAt: Instant,
    val source: BlockSource,
)

@Entity(
    tableName = "block_apps",
    primaryKeys = ["blockId", "pkg"],
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pkg")],
)
data class BlockAppEntity(
    val blockId: String,
    val pkg: String,
)

@Entity(tableName = "locked_apps", primaryKeys = ["pkg", "target"], indices = [Index("target")])
data class LockedAppEntity(
    val pkg: String,
    val target: String,
    val since: Instant,
)

@Entity(
    tableName = "cycle_state",
    primaryKeys = ["blockId", "pkg"],
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CycleStateEntity(
    val blockId: String,
    val pkg: String,
    val usedMs: Long,
    val windowStartedAt: Instant,
    val lockedUntil: Instant?,
)

@Entity(tableName = "block_events", indices = [Index("at")])
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String,
    val blockId: String?,
    val reason: String,
    val at: Instant,
)

@Entity(tableName = "usage_day", primaryKeys = ["date", "pkg"])
data class UsageDayEntity(
    val date: LocalDate,
    val pkg: String,
    val foregroundMs: Long,
    val opens: Int,
)

@Entity(tableName = "usage_hour", primaryKeys = ["date", "hour"])
data class UsageHourEntity(
    val date: LocalDate,
    val hour: Int,
    val foregroundMs: Long,
    val unlocks: Int,
)

@Entity(tableName = "usage_totals")
data class UsageTotalsEntity(
    @PrimaryKey val date: LocalDate,
    val totalMs: Long,
    val opens: Int,
    val unlocks: Int,
)

/** A block with its app join rows, as loaded by the DAOs. */
data class BlockWithApps(
    @Embedded val block: BlockEntity,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val apps: List<BlockAppEntity>,
)
