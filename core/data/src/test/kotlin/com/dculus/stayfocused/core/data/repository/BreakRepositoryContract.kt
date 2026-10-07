package com.dculus.stayfocused.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.dculus.stayfocused.core.data.settings.DataStoreBreakRepository
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.testing.FakeBreakRepository
import com.dculus.stayfocused.core.testing.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Runs identically against the DataStore implementation and the in-memory fake. */
abstract class BreakRepositoryContract {
    protected abstract fun create(clock: Clock): BreakRepository

    protected open fun close() = Unit

    private val clock = TestClock()
    private lateinit var repo: BreakRepository

    @Before
    fun setUp() {
        repo = create(clock)
    }

    @After
    fun tearDown() = close()

    @Test
    fun `start stores a session of the given length and end clears it`() =
        runTest {
            repo.observe().test {
                assertNull(awaitItem())
                repo.start(15)
                assertEquals(
                    BreakSession(clock.instant(), clock.instant().plus(Duration.ofMinutes(15)), 15),
                    awaitItem(),
                )
                repo.end()
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `start replaces the current break`() =
        runTest {
            repo.start(15)
            clock.advance(Duration.ofMinutes(1))
            repo.start(30)
            repo.observe().test {
                assertEquals(
                    BreakSession(clock.instant(), clock.instant().plus(Duration.ofMinutes(30)), 30),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an elapsed break stays until it is ended`() =
        runTest {
            repo.start(5)
            clock.advance(Duration.ofMinutes(10))
            repo.observe().test {
                assertEquals(5, awaitItem()?.lengthMins)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `end without a break is a no-op`() =
        runTest {
            repo.end()
            repo.observe().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `start rejects a non positive length`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { repo.start(0) }
        }
}

class FakeBreakRepositoryTest : BreakRepositoryContract() {
    override fun create(clock: Clock): BreakRepository = FakeBreakRepository(clock)
}

/** A real Preferences DataStore on a temp file. */
internal class TempDataStore(
    folder: TemporaryFolder,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }

    fun close() = scope.cancel()
}

class DataStoreBreakRepositoryTest : BreakRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var ds: TempDataStore

    override fun create(clock: Clock): BreakRepository {
        ds = TempDataStore(folder)
        return DataStoreBreakRepository(ds.store, clock)
    }

    override fun close() = ds.close()
}
