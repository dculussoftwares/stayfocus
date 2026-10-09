package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** One tile of the wizard's app grid. [todayMins] is the time spent in the app today. */
data class TargetApp(
    val pkg: String,
    val label: String,
    val todayMins: Int,
)

/** The apps a block can cover on a target. */
interface TargetAppsProvider {
    fun apps(target: BlockTarget): Flow<List<TargetApp>>
}

/**
 * This phone: the launchable apps (M3-02 catalog) with today's usage, A to Z so tiles never move while
 * the usage refreshes. A child phone has no app list until device linking (M8-03) syncs one.
 */
class DefaultTargetAppsProvider
    @Inject
    constructor(
        private val installedApps: InstalledAppsRepository,
        private val usage: UsageRepository,
    ) : TargetAppsProvider {
        override fun apps(target: BlockTarget): Flow<List<TargetApp>> =
            when (target) {
                BlockTarget.ThisPhone -> {
                    combine(installedApps.observeLaunchableApps(), usage.today()) { installed, today ->
                        val minsByPkg = today.apps.associate { it.pkg to it.mins }
                        installed
                            .map { TargetApp(it.pkg, it.label, minsByPkg[it.pkg] ?: 0) }
                            .sortedBy { it.label.lowercase() }
                    }
                }

                is BlockTarget.Device -> {
                    flowOf(emptyList())
                }
            }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BlockModule {
    @Binds
    abstract fun targetApps(impl: DefaultTargetAppsProvider): TargetAppsProvider
}
