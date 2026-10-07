package com.dculus.stayfocused.core.usage

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.Lazy
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Time from [now] to the next [at] (local time); a full day when it is exactly [at]. */
internal fun delayUntilNext(
    now: ZonedDateTime,
    at: LocalTime,
): Duration {
    var next = now.with(at)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return Duration.between(now, next)
}

/** Schedules the daily cache job (~00:15 local) and a backfill for days missed while the app wasn't run. */
@Singleton
class UsageCacheScheduler
    @Inject
    internal constructor(
        private val workManager: Lazy<WorkManager>,
        private val clock: Clock,
    ) {
        internal var zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }

        /** Idempotent; call on every app start. */
        fun schedule() {
            // Lazy: WorkManager initialises on demand and needs the Application's field injection to be finished.
            val workManager = workManager.get()
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            val initialDelay = delayUntilNext(clock.instant().atZone(zoneProvider()), DAILY_AT)
            workManager.enqueueUniquePeriodicWork(
                DAILY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UsageCacheWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setInitialDelay(initialDelay)
                    .build(),
            )
            workManager.enqueueUniqueWork(
                BACKFILL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UsageCacheWorker>().build(),
            )
        }

        companion object {
            const val DAILY_WORK_NAME = "usage-cache-daily"
            const val BACKFILL_WORK_NAME = "usage-cache-backfill"
            val DAILY_AT: LocalTime = LocalTime.of(0, 15)
        }
    }
