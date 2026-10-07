package com.dculus.stayfocused.core.usage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
