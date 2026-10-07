package com.dculus.stayfocused.core.usage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/** Caches the missing complete days of the last week (daily at ~00:15 and once on app start). */
@HiltWorker
class UsageCacheWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: UsageRepository,
    ) : CoroutineWorker(context, params) {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun doWork(): Result =
            try {
                repository.backfill()
                Result.success()
            } catch (
                @Suppress("SwallowedException") e: SecurityException,
            ) {
                // Usage access was revoked mid-run; nothing to cache until the user grants it again.
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Usage backfill failed (attempt %d)", runAttemptCount + 1)
                // Transient usage-query or database failure: back off and try again a few times.
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }

        private companion object {
            const val MAX_ATTEMPTS = 3
        }
    }
