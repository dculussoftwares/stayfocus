package com.dculus.stayfocused.core.data.repository

import app.cash.turbine.test
import com.dculus.stayfocused.core.data.settings.DataStoreSettingsRepository
import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import kotlin.test.assertEquals

/** Runs identically against the DataStore implementation and the in-memory fake. */
abstract class SettingsRepositoryContract {
    protected abstract fun create(): SettingsRepository

    protected open fun close() = Unit

    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        repo = create()
    }

    @After
    fun tearDown() = close()

    @Test
    fun `defaults before anything is stored`() =
        runTest {
            repo.settings.test {
                assertEquals(AppSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each setter updates only its own field`() =
        runTest {
            val consent = Instant.ofEpochMilli(5_000)
            repo.settings.test {
                assertEquals(AppSettings(), awaitItem())
                repo.setOnboardingComplete(true)
                assertEquals(AppSettings(onboardingComplete = true), awaitItem())
                repo.setAccountSkipped(true)
                assertEquals(AppSettings(onboardingComplete = true, accountSkipped = true), awaitItem())
                repo.setAccessibilityConsentAt(consent)
                assertEquals(
                    AppSettings(onboardingComplete = true, accountSkipped = true, accessibilityConsentAt = consent),
                    awaitItem(),
                )
                repo.setAiEnabled(true)
                assertEquals(
                    AppSettings(
                        onboardingComplete = true,
                        accountSkipped = true,
                        accessibilityConsentAt = consent,
                        aiEnabled = true,
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `focus session round trips and clears`() =
        runTest {
            val session = FocusSession(Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(61_000), 1)
            repo.settings.test {
                assertEquals(null, awaitItem().focusSession)
                repo.setFocusSession(session)
                assertEquals(session, awaitItem().focusSession)
                repo.setFocusSession(null)
                assertEquals(null, awaitItem().focusSession)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `consent can be cleared`() =
        runTest {
            repo.setAccessibilityConsentAt(Instant.ofEpochMilli(5_000))
            repo.setAccessibilityConsentAt(null)
            repo.settings.test {
                assertEquals(null, awaitItem().accessibilityConsentAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setting the same value again does not re-emit`() =
        runTest {
            repo.setOnboardingComplete(true)
            repo.settings.test {
                assertEquals(true, awaitItem().onboardingComplete)
                repo.setOnboardingComplete(true)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

class FakeSettingsRepositoryTest : SettingsRepositoryContract() {
    override fun create(): SettingsRepository = FakeSettingsRepository()
}

class DataStoreSettingsRepositoryTest : SettingsRepositoryContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var ds: TempDataStore

    override fun create(): SettingsRepository {
        ds = TempDataStore(folder)
        return DataStoreSettingsRepository(ds.store)
    }

    override fun close() = ds.close()
}
