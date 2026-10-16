package com.dculus.stayfocused.kids.monitoring

import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.sync.ChildStatus
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.FakeChildStatusRepository
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageStatsDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChildReporterTest {
    private val target = DeviceTarget("parent", "device")
    private val repo = FakeChildStatusRepository()
    private val snapshots = MutableStateFlow(status(battery = 80))
    private val apps = MutableStateFlow(listOf(AppInfo("a.b", "Alpha")))
    private var signedIn = true

    private fun status(
        battery: Int,
        app: String? = null,
    ) = ChildStatus(battery, charging = false, currentApp = app, online = true)

    private val usage =
        object : UsageStatsDataSource {
            var apps = listOf(AppUsageStat("a.b", 5 * 60_000L, opens = 3, firstAfterUnlock = 1))

            override suspend fun dayUsage(date: LocalDate) =
                DayUsageStats.empty(date).copy(totalMillis = apps.sumOf { it.millis }, apps = apps)
        }

    private fun reporter() =
        ChildReporter(
            repository = repo,
            deviceAuth =
                object : DeviceAuth {
                    override val uid: Flow<String?> = flowOf("child")

                    override suspend fun ensureSignedIn() = if (signedIn) "child" else null
                },
            source =
                object : StatusSource {
                    override val snapshots: Flow<ChildStatus> = this@ChildReporterTest.snapshots
                },
            usage = usage,
            installedApps =
                object : InstalledAppsRepository {
                    override fun observeLaunchableApps() = apps
                },
            clock = Clock.fixed(Instant.parse("2026-03-04T10:00:00Z"), ZoneOffset.UTC),
        )

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun quietPhoneWritesOncePerHeartbeat() =
        runTest {
            val job = launch { reporter().reportStatus(target) }
            advance(5_000)
            assertEquals(1, repo.statuses.size)
            advance(90_000 * 10)
            // One initial write plus one per 90 s, never more.
            assertEquals(11, repo.statuses.size)
            job.cancel()
        }

    @Test
    fun changesAreDebouncedIntoOneWrite() =
        runTest {
            val job = launch { reporter().reportStatus(target) }
            advance(5_000)
            repo.statuses.clear()
            snapshots.value = status(79)
            advance(1_000)
            snapshots.value = status(78, app = "Alpha")
            advance(1_000)
            snapshots.value = status(77, app = "Beta")
            advance(4_999)
            assertTrue(repo.statuses.isEmpty())
            advance(1)
            assertEquals(listOf(status(77, app = "Beta")), repo.statuses)
            job.cancel()
        }

    @Test
    fun saysOfflineWhenStopped() =
        runTest {
            val job = launch { reporter().reportStatus(target) }
            advance(5_000)
            job.cancel()
            runCurrent()
            assertEquals(false, repo.statuses.last().online)
        }

    @Test
    fun aFailedWriteDoesNotStopTheReporter() =
        runTest {
            repo.failing = true
            val job = launch { reporter().reportStatus(target) }
            advance(5_000)
            repo.failing = false
            advance(90_000)
            assertEquals(1, repo.statuses.size)
            job.cancel()
        }

    @Test
    fun constantAppSwitchingDoesNotStarveTheHeartbeat() =
        runTest {
            val job = launch { reporter().reportStatus(target) }
            advance(5_000)
            repo.statuses.clear()
            // An app switch every 2 s for 3 minutes: never quiet for 5 s.
            repeat(90) { i ->
                snapshots.value = status(70, app = "App$i")
                advance(2_000)
            }
            assertTrue(repo.statuses.isNotEmpty())
            assertTrue(repo.statuses.size <= 2)
            job.cancel()
        }

    @Test
    fun keepsTryingToSignInInsteadOfGivingUp() =
        runTest {
            signedIn = false
            val job = launch { reporter().run(target) }
            advance(10 * 60_000L)
            assertTrue(repo.statuses.isEmpty() && repo.usages.isEmpty() && repo.appSyncs.isEmpty())
            signedIn = true
            advance(60_000 + 5_000)
            assertTrue(repo.statuses.isNotEmpty())
            job.cancel()
        }

    @Test
    fun usageClosesYesterdayThenTodayAndOnlyRewritesChanges() =
        runTest {
            val job = launch { reporter().reportUsage(target) }
            runCurrent()
            assertEquals(listOf("2026-03-03", "2026-03-04"), repo.usages.map { it.date })
            val today = repo.usages.last()
            assertEquals(5, today.totalMins)
            assertEquals("Alpha", today.apps.single().label)
            assertEquals(3, today.apps.single().opens)
            advance(15 * 60_000L)
            assertEquals(2, repo.usages.size)
            usage.apps = listOf(AppUsageStat("a.b", 9 * 60_000L, opens = 4, firstAfterUnlock = 1))
            advance(15 * 60_000L)
            assertEquals(listOf("2026-03-03", "2026-03-04", "2026-03-04"), repo.usages.map { it.date })
            job.cancel()
        }

    @Test
    fun aFailedYesterdayUploadIsRetriedOnTheNextTick() =
        runTest {
            repo.failing = true
            val job = launch { reporter().reportUsage(target) }
            runCurrent()
            assertTrue(repo.usages.isEmpty())
            repo.failing = false
            advance(15 * 60_000L)
            assertEquals(listOf("2026-03-03", "2026-03-04"), repo.usages.map { it.date })
            job.cancel()
        }

    @Test
    fun appsAreSyncedOnStartAndOnPackageChanges() =
        runTest {
            val job = launch { reporter().reportApps(target) }
            advance(5_000)
            assertEquals(listOf(mapOf("a.b" to "Alpha")), repo.appSyncs)
            apps.value = listOf(AppInfo("a.b", "Alpha"), AppInfo("c.d", "Gamma"))
            advance(5_000)
            assertEquals(mapOf("a.b" to "Alpha", "c.d" to "Gamma"), repo.appSyncs.last())
            assertEquals(2, repo.appSyncs.size)
            job.cancel()
        }

    @Test
    fun aFailedAppSyncIsRetried() =
        runTest {
            repo.failing = true
            val job = launch { reporter().reportApps(target) }
            advance(5_000)
            repo.failing = false
            advance(60_000)
            assertEquals(1, repo.appSyncs.size)
            job.cancel()
        }
}
