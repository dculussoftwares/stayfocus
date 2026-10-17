package com.dculus.stayfocused.core.blocking.engine

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dculus.stayfocused.core.model.TemporaryAllowance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemporaryAllowancesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = app.getSharedPreferences("test_allowances", Context.MODE_PRIVATE)
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun store(at: Clock = clock) = SharedPreferencesTemporaryAllowances(prefs, at)

    private fun at(minutes: Long) = now.plusSeconds(minutes * 60)

    @Test fun grantAddsAnAllowanceUntilTheEnd() =
        runTest {
            val store = store()
            store.grant("com.instagram.android", at(15))
            assertEquals(listOf(TemporaryAllowance("com.instagram.android", at(15))), store.observe().first())
        }

    @Test fun aGrantSurvivesANewProcess() =
        runTest {
            store().grant("a", at(30))
            assertEquals(listOf(TemporaryAllowance("a", at(30))), store().observe().first())
        }

    @Test fun aLongerGrantReplacesAndAShorterOneKeepsTheLonger() =
        runTest {
            val store = store()
            store.grant("a", at(15))
            store.grant("a", at(60))
            assertEquals(listOf(TemporaryAllowance("a", at(60))), store.observe().first())
            store.grant("a", at(30))
            assertEquals(listOf(TemporaryAllowance("a", at(60))), store.observe().first())
        }

    @Test fun grantsForDifferentAppsAreIndependent() =
        runTest {
            val store = store()
            store.grant("a", at(15))
            store.grant("b", at(30))
            assertEquals(
                setOf("a", "b"),
                store
                    .observe()
                    .first()
                    .map { it.pkg }
                    .toSet(),
            )
        }

    @Test fun anEndedOrPastGrantIsDropped() =
        runTest {
            val store = store()
            store.grant("old", at(5))
            store.grant("past", at(-1))
            val later = Clock.fixed(at(10), ZoneOffset.UTC)
            assertTrue(store(later).observe().first().isEmpty())
            assertTrue(store.observe().first().none { it.pkg == "past" })
        }

    @Test fun unreadableStorageStartsOver() =
        runTest {
            prefs.edit().putString("allowances", "not json").commit()
            assertTrue(store().observe().first().isEmpty())
        }
}
