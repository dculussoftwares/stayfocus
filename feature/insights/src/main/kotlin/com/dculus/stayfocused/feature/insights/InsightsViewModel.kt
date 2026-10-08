package com.dculus.stayfocused.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal data class InsightsUiState(
    val dayOffset: Int = 0,
    val date: LocalDate = LocalDate.now(),
    val metric: InsightsMetric = InsightsMetric.ScreenTime,
    val loading: Boolean = true,
    /** Null when the day has no data (empty state). */
    val day: InsightsDay? = null,
) {
    val canStepBack: Boolean get() = canStepBack(dayOffset)
    val canStepForward: Boolean get() = canStepForward(dayOffset)
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class InsightsViewModel
    @Inject
    constructor(
        private val usage: UsageRepository,
        installedApps: InstalledAppsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val dayOffset = MutableStateFlow(0)
        private val metric = MutableStateFlow(InsightsMetric.ScreenTime)

        private fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

        private val labels: Flow<Map<String, String>> =
            installedApps
                .observeLaunchableApps()
                .map { apps -> apps.associate { it.pkg to it.label } }
                .onStart { emit(emptyMap()) }

        private val dayStats: Flow<Pair<Int, DayUsageStats?>> =
            dayOffset.flatMapLatest { offset ->
                val source: Flow<DayUsageStats?> =
                    if (offset ==
                        0
                    ) {
                        usage.today()
                    } else {
                        usage.day(today().plusDays(offset.toLong()))
                    }
                source.map { offset to it }
            }

        val state: StateFlow<InsightsUiState> =
            combine(dayStats, usage.averages(), metric, labels) { (offset, stats), averages, metric, names ->
                val date = today().plusDays(offset.toLong())
                val day =
                    if (stats.hasNoData()) {
                        null
                    } else {
                        buildInsightsDay(
                            stats = checkNotNull(stats),
                            averages = averages,
                            metric = metric,
                            currentHour =
                                if (offset ==
                                    0
                                ) {
                                    clock.instant().atZone(ZoneId.systemDefault()).hour
                                } else {
                                    null
                                },
                            labelOf = { pkg -> names[pkg] ?: pkg.substringAfterLast('.') },
                        )
                    }
                InsightsUiState(offset, date, metric, loading = false, day = day)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                InsightsUiState(date = today()),
            )

        fun previousDay() = dayOffset.update { if (canStepBack(it)) it - 1 else it }

        fun nextDay() = dayOffset.update { if (canStepForward(it)) it + 1 else it }

        fun selectMetric(value: InsightsMetric) {
            metric.value = value
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
