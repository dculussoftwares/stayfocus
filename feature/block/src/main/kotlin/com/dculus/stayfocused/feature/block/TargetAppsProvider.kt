package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.sync.ParentDeviceBlocks
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.Collator
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
 * the usage refreshes. A child phone: the apps it reported to Firestore.
 */
class DefaultTargetAppsProvider
    @Inject
    constructor(
        private val installedApps: InstalledAppsRepository,
        private val usage: UsageRepository,
        private val deviceBlocks: ParentDeviceBlocks,
    ) : TargetAppsProvider {
        override fun apps(target: BlockTarget): Flow<List<TargetApp>> =
            when (target) {
                BlockTarget.ThisPhone -> {
                    val collator = Collator.getInstance()
                    combine(installedApps.observeLaunchableApps(), usage.today()) { installed, today ->
                        val minsByPkg = today.apps.associate { it.pkg to it.mins }
                        installed
                            .map { TargetApp(it.pkg, it.label, minsByPkg[it.pkg] ?: 0) }
                            .sortedWith(compareBy(collator) { it.label })
                    }
                }

                is BlockTarget.Device -> {
                    // The apps the child phone reported (`devices/{id}/apps`); its usage is not mirrored per app.
                    val collator = Collator.getInstance()
                    deviceBlocks.observeApps(target.deviceId).map { apps ->
                        apps.map { TargetApp(it.pkg, it.label, 0) }.sortedWith(compareBy(collator) { it.label })
                    }
                }
            }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BlockModule {
    @Binds
    abstract fun targetApps(impl: DefaultTargetAppsProvider): TargetAppsProvider
}
