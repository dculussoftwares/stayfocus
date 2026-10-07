package com.dculus.stayfocused.core.data.repository

import app.cash.turbine.test
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.testing.FakeLockedAppsRepository
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import kotlin.test.assertEquals

/** Runs identically against the Room implementation and the in-memory fake. */
@RunWith(RobolectricTestRunner::class)
abstract class LockedAppsRepositoryContract {
    protected abstract fun create(clock: Clock): LockedAppsRepository

    protected open fun close() = Unit

    private val clock = TestClock()
    private lateinit var repo: LockedAppsRepository

    @Before
    fun setUp() {
        repo = create(clock)
    }

    @After
    fun tearDown() = close()

    @Test
    fun `lock stamps the current time and unlock removes the app`() =
        runTest {
            repo.observeAll().test {
                assertEquals(emptyList(), awaitItem())
                repo.lock("com.a", BlockTarget.ThisPhone)
                assertEquals(listOf(LockedApp("com.a", BlockTarget.ThisPhone, clock.instant())), awaitItem())
                repo.unlock("com.a", BlockTarget.ThisPhone)
                assertEquals(emptyList(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `locking again keeps the original since`() =
        runTest {
            repo.lock("com.a", BlockTarget.ThisPhone)
            val first = clock.instant()
            clock.advance(Duration.ofMinutes(5))
            repo.lock("com.a", BlockTarget.ThisPhone)
            repo.observeAll().test {
                assertEquals(listOf(LockedApp("com.a", BlockTarget.ThisPhone, first)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeAll is oldest first and observeByTarget filters`() =
        runTest {
            val device = BlockTarget.Device("dev-1")
            repo.lock("com.a", BlockTarget.ThisPhone)
            val t1 = clock.instant()
            clock.advance(Duration.ofMinutes(1))
            repo.lock("com.a", device)
            val t2 = clock.instant()
            val mine = LockedApp("com.a", BlockTarget.ThisPhone, t1)
            val theirs = LockedApp("com.a", device, t2)

            repo.observeAll().test {
                assertEquals(listOf(mine, theirs), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeByTarget(device).test {
                assertEquals(listOf(theirs), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unlock only affects the given target`() =
        runTest {
            val device = BlockTarget.Device("dev-1")
            repo.lock("com.a", BlockTarget.ThisPhone)
            repo.lock("com.a", device)
            repo.unlock("com.a", device)
            repo.observeAll().test {
                assertEquals(listOf("com.a"), awaitItem().map { it.pkg })
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeByTarget(BlockTarget.ThisPhone).test {
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

class FakeLockedAppsRepositoryTest : LockedAppsRepositoryContract() {
    override fun create(clock: Clock): LockedAppsRepository = FakeLockedAppsRepository(clock)
}
