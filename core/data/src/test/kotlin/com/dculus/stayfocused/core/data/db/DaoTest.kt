package com.dculus.stayfocused.core.data.db

import androidx.room.Room
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.TimeRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: StayFocusedDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    StayFocusedDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    private fun block(
        id: String,
        apps: Set<String>,
        target: BlockTarget = BlockTarget.ThisPhone,
        enabled: Boolean = true,
        createdAt: Instant = Instant.ofEpochMilli(1_000),
    ) = Block(
        id = id,
        target = target,
        type = BlockType.SCHEDULE,
        name = "Block $id",
        apps = apps,
        limitMins = null,
        period = null,
        useMins = null,
        restMins = null,
        range = TimeRange(LocalTime.of(22, 0), LocalTime.of(7, 0)),
        durationMins = null,
        startedAt = null,
        days = DaysOfWeek.WEEKDAYS,
        enabled = enabled,
        createdAt = createdAt,
        source = BlockSource.MANUAL,
    )

    private suspend fun save(block: Block) = db.blockDao().upsert(block.toEntity(), block.toAppEntities())

    private suspend fun joinRows(): List<BlockAppEntity> =
        db.query("SELECT blockId, pkg FROM block_apps ORDER BY blockId, pkg", null).use { c ->
            buildList { while (c.moveToNext()) add(BlockAppEntity(c.getString(0), c.getString(1))) }
        }

    @Test
    fun `insert then read returns the block with its apps`() =
        runBlocking {
            val b = block("b1", setOf("com.a", "com.b"))
            save(b)
            assertEquals(
                listOf(b),
                db
                    .blockDao()
                    .observeByTarget(TARGET_ME)
                    .first()
                    .map { it.toModel() },
            )
        }

    @Test
    fun `all block fields survive a round trip`() =
        runBlocking {
            val b =
                block("b1", setOf("com.a"), target = BlockTarget.Device("dev-1")).copy(
                    type = BlockType.LIMIT,
                    limitMins = 30,
                    period = LimitPeriod.HOURLY,
                    useMins = 10,
                    restMins = 20,
                    range = null,
                    durationMins = 60,
                    startedAt = Instant.ofEpochMilli(5_000),
                    source = BlockSource.AI,
                )
            save(b)
            assertEquals(
                b,
                db
                    .blockDao()
                    .observe("b1")
                    .first()
                    ?.toModel(),
            )
        }

    @Test
    fun `update replaces the apps and keeps one block`() =
        runBlocking {
            save(block("b1", setOf("com.a", "com.b")))
            save(block("b1", setOf("com.b", "com.c")).copy(name = "Renamed", enabled = false))

            val all = db.blockDao().observeByTarget(TARGET_ME).first()
            assertEquals(1, all.size)
            assertEquals("Renamed", all.single().block.name)
            assertEquals(setOf("com.b", "com.c"), all.single().toModel().apps)
        }

    @Test
    fun `query by target separates this phone from a device`() =
        runBlocking {
            save(block("mine", setOf("com.a")))
            save(block("theirs", setOf("com.a"), target = BlockTarget.Device("dev-1")))

            assertEquals(
                listOf("mine"),
                db
                    .blockDao()
                    .observeByTarget("me")
                    .first()
                    .map { it.block.id },
            )
            assertEquals(
                listOf("theirs"),
                db
                    .blockDao()
                    .observeByTarget("dev-1")
                    .first()
                    .map { it.block.id },
            )
        }

    @Test
    fun `query by pkg returns only enabled blocks containing it`() =
        runBlocking {
            save(block("on", setOf("com.a", "com.b"), createdAt = Instant.ofEpochMilli(1)))
            save(block("off", setOf("com.a"), enabled = false))
            save(block("other", setOf("com.z")))

            val hits = db.blockDao().observeEnabledContaining("com.a").first()
            assertEquals(listOf("on"), hits.map { it.block.id })
            assertEquals(setOf("com.a", "com.b"), hits.single().toModel().apps)
        }

    @Test
    fun `deleting a block cascades to its join rows and cycle state`() =
        runBlocking {
            save(block("b1", setOf("com.a", "com.b")))
            save(block("b2", setOf("com.a")))
            db.cycleStateDao().upsert(CycleStateEntity("b1", "com.a", 10, Instant.EPOCH, null))

            db.blockDao().delete("b1")

            assertEquals(listOf(BlockAppEntity("b2", "com.a")), joinRows())
            assertNull(db.cycleStateDao().observe("b1", "com.a").first())
            assertEquals(
                listOf("b2"),
                db
                    .blockDao()
                    .observeByTarget(TARGET_ME)
                    .first()
                    .map { it.block.id },
            )
        }

    @Test
    fun `locked apps are keyed by pkg and target`() =
        runBlocking {
            val mine = LockedApp("com.a", BlockTarget.ThisPhone, Instant.ofEpochMilli(1))
            val theirs = LockedApp("com.a", BlockTarget.Device("dev-1"), Instant.ofEpochMilli(2))
            db.lockedAppDao().upsert(mine.toEntity())
            db.lockedAppDao().upsert(theirs.toEntity())

            assertEquals(
                listOf(mine, theirs),
                db
                    .lockedAppDao()
                    .observeAll()
                    .first()
                    .map { it.toModel() },
            )
            assertEquals(
                listOf(theirs),
                db
                    .lockedAppDao()
                    .observeByTarget("dev-1")
                    .first()
                    .map { it.toModel() },
            )

            db.lockedAppDao().delete("com.a", "me")
            assertEquals(
                listOf(theirs),
                db
                    .lockedAppDao()
                    .observeAll()
                    .first()
                    .map { it.toModel() },
            )
        }

    @Test
    fun `cycle state upsert overwrites the same key`() =
        runBlocking {
            save(block("b1", setOf("com.a")))
            db.cycleStateDao().upsert(CycleStateEntity("b1", "com.a", 1, Instant.ofEpochMilli(1), null))
            db.cycleStateDao().upsert(
                CycleStateEntity("b1", "com.a", 2, Instant.ofEpochMilli(1), Instant.ofEpochMilli(9)),
            )

            val state = db.cycleStateDao().observe("b1", "com.a").first()
            assertEquals(2L, state?.usedMs)
            assertEquals(Instant.ofEpochMilli(9), state?.lockedUntil)

            db.cycleStateDao().deleteForBlock("b1")
            assertNull(db.cycleStateDao().observe("b1", "com.a").first())
        }

    @Test
    fun `block events are counted within a day window`() =
        runBlocking {
            val dao = db.blockEventDao()
            val from = Instant.ofEpochSecond(1_000)
            val to = Instant.ofEpochSecond(2_000)
            dao.insert(BlockEventEntity(pkg = "com.a", blockId = null, reason = "limit", at = from.minusSeconds(1)))
            dao.insert(BlockEventEntity(pkg = "com.a", blockId = "b1", reason = "limit", at = from))
            dao.insert(BlockEventEntity(pkg = "com.b", blockId = null, reason = "schedule", at = to.minusSeconds(1)))
            dao.insert(BlockEventEntity(pkg = "com.b", blockId = null, reason = "schedule", at = to))

            assertEquals(2, dao.observeCountBetween(from, to).first())
            assertEquals(listOf("com.b", "com.a"), dao.observeBetween(from, to).first().map { it.pkg })
        }

    @Test
    fun `usage is queried by inclusive date range`() =
        runBlocking {
            val dao = db.usageDao()
            val d1 = LocalDate.of(2026, 6, 1)
            val d2 = d1.plusDays(1)
            val d3 = d1.plusDays(2)
            listOf(d1, d2, d3).forEach { d ->
                dao.upsertDay(UsageDayEntity(d, "com.a", 1_000, 1))
                dao.upsertTotals(UsageTotalsEntity(d, 1_000, 1, 2))
            }
            dao.upsertDay(UsageDayEntity(d2, "com.a", 5_000, 3)) // upsert overwrites
            dao.upsertHour(UsageHourEntity(d2, 9, 2_000, 1))
            dao.upsertHour(UsageHourEntity(d2, 8, 1_000, 0))

            val days = dao.observeDays(d1, d2).first()
            assertEquals(listOf(d1, d2), days.map { it.date })
            assertEquals(5_000L, days.last().foregroundMs)
            assertEquals(listOf(d2, d3), dao.observeTotals(d2, d3).first().map { it.date })
            assertEquals(listOf(8, 9), dao.observeHours(d2).first().map { it.hour })
            assertTrue(dao.observeHours(d1).first().isEmpty())
        }
}
