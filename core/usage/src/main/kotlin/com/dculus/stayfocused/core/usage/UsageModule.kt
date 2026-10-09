package com.dculus.stayfocused.core.usage

import android.content.Context
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UsageModule {
    @Binds
    abstract fun installedApps(impl: PackageManagerInstalledAppsRepository): InstalledAppsRepository

    @Binds
    abstract fun usageAccess(impl: AppOpsUsageAccess): UsageAccess

    @Binds
    abstract fun usageStats(impl: AndroidUsageStatsDataSource): UsageStatsDataSource

    @Binds
    abstract fun packageUsage(impl: AndroidUsageStatsDataSource): PackageUsageSource

    @Binds
    abstract fun usageCache(impl: RoomUsageCache): UsageCache

    @Binds
    abstract fun usageRepository(impl: DefaultUsageRepository): UsageRepository

    companion object {
        @Provides
        fun workManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)
    }
}
