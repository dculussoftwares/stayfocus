package com.dculus.stayfocused.core.usage

import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class RecordingRepository(
    private val onBackfill: () -> Unit = {},
) : UsageRepository {
    var backfills = 0

    override fun today(): Flow<DayUsageStats> = emptyFlow()

    override fun requestRefresh() = Unit

    override fun day(date: LocalDate): Flow<DayUsageStats?> = emptyFlow()

    override fun averages(): Flow<UsageAverages> = emptyFlow()

    override fun blockedToday(): Flow<Int> = emptyFlow()

    override suspend fun backfill() {
        backfills++
        onBackfill()
    }
}

@RunWith(RobolectricTestRunner::class)
class UsageCacheWorkerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun factory(repository: UsageRepository) =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                if (workerClassName == UsageCacheWorker::class.java.name) {
                    UsageCacheWorker(appContext, workerParameters, repository)
                } else {
                    null
                }
        }

    private fun runWorker(repository: UsageRepository): ListenableWorker.Result =
        kotlinx.coroutines.runBlocking {
            TestListenableWorkerBuilder<UsageCacheWorker>(context)
                .setWorkerFactory(factory(repository))
                .build()
                .doWork()
        }

    @Test
    fun `worker backfills and succeeds`() {
        val repository = RecordingRepository()
        assertEquals(ListenableWorker.Result.success(), runWorker(repository))
        assertEquals(1, repository.backfills)
    }

    @Test
    fun `worker succeeds when usage access is revoked mid-run`() {
        val repository = RecordingRepository { throw SecurityException("revoked") }
        assertEquals(ListenableWorker.Result.success(), runWorker(repository))
    }

    @Test
    fun `worker lets unexpected failures through`() {
        val repository = RecordingRepository { error("boom") }
        assertFailsWith<IllegalStateException> { runWorker(repository) }
    }

    @Test
    fun `delay until next 00-15 is today when before it and tomorrow when after`() {
        val at = LocalTime.of(0, 15)
        val before = ZonedDateTime.of(2026, 3, 10, 0, 5, 0, 0, ZoneOffset.UTC)
        assertEquals(10L, delayUntilNext(before, at).toMinutes())
        val after = ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(12L * 60 + 15, delayUntilNext(after, at).toMinutes())
        val exactly = ZonedDateTime.of(2026, 3, 10, 0, 15, 0, 0, ZoneOffset.UTC)
        assertEquals(24L * 60, delayUntilNext(exactly, at).toMinutes())
    }

    @Test
    fun `scheduler enqueues a daily job and a one-time backfill that run the worker`() =
        runTest {
            val repository = RecordingRepository()
            val config =
                Configuration
                    .Builder()
                    .setExecutor(SynchronousExecutor())
                    .setTaskExecutor(SynchronousExecutor())
                    .setWorkerFactory(factory(repository))
                    .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
            val workManager = WorkManager.getInstance(context)
            val clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)
            val scheduler = UsageCacheScheduler(workManager, clock).apply { zone = ZoneOffset.UTC }

            scheduler.schedule()
            scheduler.schedule() // idempotent: unique work, KEEP

            val daily = workManager.getWorkInfosForUniqueWork(UsageCacheScheduler.DAILY_WORK_NAME).get()
            assertEquals(1, daily.size)
            val backfill = workManager.getWorkInfosForUniqueWork(UsageCacheScheduler.BACKFILL_WORK_NAME).get()
            assertEquals(1, backfill.size)
            // The one-time backfill ran immediately (synchronous executor, no constraints).
            assertEquals(WorkInfo.State.SUCCEEDED, backfill.single().state)
            assertTrue(repository.backfills >= 1)
        }
}
