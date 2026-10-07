package com.dculus.stayfocused.core.data.repository

import app.cash.turbine.test
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.testing.FakeBlockRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Runs identically against the Room implementation and the in-memory fake. */
@RunWith(RobolectricTestRunner::class)
abstract class BlockRepositoryContract {
    protected abstract fun create(): BlockRepository

    protected open fun close() = Unit

    private lateinit var repo: BlockRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @After
    fun tearDown() = close()

    private fun block(
        id: String,
        apps: Set<String> = setOf("com.a"),
        target: BlockTarget = BlockTarget.ThisPhone,
        createdAt: Long = 1_000,
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
        enabled = true,
        createdAt = Instant.ofEpochMilli(createdAt),
        source = BlockSource.MANUAL,
    )

    @Test
    fun `observeByTarget emits as blocks are added and removed`() =
        runTest {
            val one = block("b1", createdAt = 1)
            val two = block("b2", createdAt = 2)
            repo.observeByTarget(BlockTarget.ThisPhone).test {
                assertEquals(emptyList(), awaitItem())
                repo.upsert(one)
                assertEquals(listOf(one), awaitItem())
                repo.upsert(two)
                assertEquals(listOf(one, two), awaitItem())
                repo.delete("b1")
                assertEquals(listOf(two), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeByTarget separates this phone from a device`() =
        runTest {
            val mine = block("mine")
            val theirs = block("theirs", target = BlockTarget.Device("dev-1"))
            repo.upsert(mine)
            repo.upsert(theirs)
            repo.observeByTarget(BlockTarget.ThisPhone).test {
                assertEquals(listOf(mine), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeByTarget(BlockTarget.Device("dev-1")).test {
                assertEquals(listOf(theirs), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeByTarget is ordered by creation time`() =
        runTest {
            val late = block("late", createdAt = 9)
            val early = block("early", createdAt = 1)
            repo.upsert(late)
            repo.upsert(early)
            repo.observeByTarget(BlockTarget.ThisPhone).test {
                assertEquals(listOf(early, late), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `get returns the block or null`() =
        runTest {
            val b = block("b1", apps = setOf("com.a", "com.b"))
            repo.upsert(b)
            assertEquals(b, repo.get("b1"))
            assertNull(repo.get("missing"))
        }

    @Test
    fun `upsert replaces the block and its apps`() =
        runTest {
            repo.upsert(block("b1", apps = setOf("com.a", "com.b")))
            val updated = block("b1", apps = setOf("com.b", "com.c")).copy(name = "Renamed")
            repo.upsert(updated)
            assertEquals(updated, repo.get("b1"))
            repo.observeByTarget(BlockTarget.ThisPhone).test {
                assertEquals(listOf(updated), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe emits changes of one block and null once deleted`() =
        runTest {
            val b = block("b1")
            repo.upsert(b)
            repo.observe("b1").test {
                assertEquals(b, awaitItem())
                repo.setEnabled("b1", false)
                assertEquals(b.copy(enabled = false), awaitItem())
                repo.delete("b1")
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setEnabled on a missing block does nothing`() =
        runTest {
            repo.setEnabled("missing", false)
            assertNull(repo.get("missing"))
        }

    @Test
    fun `delete of a missing block does nothing`() =
        runTest {
            val b = block("b1")
            repo.upsert(b)
            repo.delete("missing")
            assertEquals(b, repo.get("b1"))
        }
}

class FakeBlockRepositoryTest : BlockRepositoryContract() {
    override fun create(): BlockRepository = FakeBlockRepository()
}
