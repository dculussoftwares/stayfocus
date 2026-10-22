package com.dculus.stayfocused.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

internal data class InsightsUiState(
    val range: InsightsRange = InsightsRange.Day,
    val dayOffset: Int = 0,
    val date: LocalDate,
    val metric: InsightsMetric = InsightsMetric.ScreenTime,
    val loading: Boolean = true,
    /** Null when the day (or the whole week) has no data (empty state). */
    val day: InsightsDay? = null,
    /** The "Unlocks" and "Blocked" tiles always show today. */
    val unlocksToday: Int = 0,
    val blockedToday: Int = 0,
) {
    val canStepBack: Boolean get() = canStepBack(dayOffset)
    val canStepForward: Boolean get() = canStepForward(dayOffset)
}

/** The usage a screen state is built from: one entry for a day, [WEEK_DAYS] (oldest first, today last) for a week. */
private data class Source(
    val range: InsightsRange,
    val offset: Int,
    val date: LocalDate,
    val days: List<DayUsageStats?>,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class InsightsViewModel
    @Inject
    constructor(
        private val usage: UsageRepository,
        installedApps: InstalledAppsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val range = MutableStateFlow(InsightsRange.Day)
        private val dayOffset = MutableStateFlow(0)
        private val metric = MutableStateFlow(InsightsMetric.ScreenTime)

        private fun today(): LocalDate = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

        private val labels: Flow<Map<String, String>> =
            installedApps
                .observeLaunchableApps()
                .map { apps -> apps.associate { it.pkg to it.label } }
                .onStart { emit(emptyMap()) }

        /** The local date, re-read every minute so a screen left open follows midnight. */
        private val localDate: Flow<LocalDate> =
            flow {
                while (true) {
                    emit(today())
                    delay(DATE_POLL_MILLIS)
                }
            }.distinctUntilChanged()

        private val source: Flow<Source> =
            combine(range, dayOffset, localDate, ::Triple).flatMapLatest { (range, offset, now) ->
                when (range) {
                    InsightsRange.Day -> {
                        val date = now.plusDays(offset.toLong())
                        val one: Flow<DayUsageStats?> = if (offset == 0) usage.today() else usage.day(date)
                        one.map { Source(range, offset, date, listOf(it)) }
                    }

                    InsightsRange.Week -> {
                        val past = (WEEK_DAYS - 1 downTo 1).map { usage.day(now.minusDays(it.toLong())) }
                        combine(past + usage.today()) { it.toList() }.map { Source(range, 0, now, it) }
                    }
                }
            }

        init {
            // Fills the cache of past days (no-op without usage access, skips days already cached).
            viewModelScope.launch {
                try {
                    usage.backfill()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    // Best effort: a failed backfill must not crash the screen; the cache worker retries later.
                    Timber.w(e, "Insights backfill failed")
                }
            }
        }

        val state: StateFlow<InsightsUiState> =
            combine(
                source,
                metric,
                labels,
                usage.today().map { it.unlocks }.onStart { emit(0) },
                usage.blockedToday().onStart { emit(0) },
            ) { src, metric, names, unlocks, blocked ->
                val labelOf = { pkg: String -> names[pkg] ?: pkg.substringAfterLast('.') }
                val day =
                    when {
                        src.days.all { it.hasNoData() } -> {
                            null
                        }

                        src.range == InsightsRange.Day -> {
                            buildDay(
                                stats = checkNotNull(src.days.single()),
                                metric = metric,
                                currentHour = if (src.offset == 0) now().hour else null,
                                labelOf = labelOf,
                            )
                        }

                        else -> {
                            buildWeek(src.days, metric, weekAxisLabels(src.date), labelOf)
                        }
                    }
                InsightsUiState(src.range, src.offset, src.date, metric, loading = false, day, unlocks, blocked)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                InsightsUiState(date = today()),
            )

        private fun now() = clock.instant().atZone(ZoneId.systemDefault())

        fun selectRange(value: InsightsRange) {
            range.value = value
        }

        fun previousDay() = dayOffset.update { if (canStepBack(it)) it - 1 else it }

        fun nextDay() = dayOffset.update { if (canStepForward(it)) it + 1 else it }

        fun selectMetric(value: InsightsMetric) {
            metric.value = value
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val DATE_POLL_MILLIS = 60_000L
        }
    }
