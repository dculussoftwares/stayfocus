package com.dculus.stayfocused.core.usage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Caches the missing complete days of the last week (daily at ~00:15 and once on app start). */
@HiltWorker
class UsageCacheWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val repository: UsageRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            try {
                repository.backfill()
                Result.success()
            } catch (
                @Suppress("SwallowedException") e: SecurityException,
            ) {
                // Usage access was revoked mid-run; nothing to cache until the user grants it again.
                Result.success()
            }
    }
