package com.dculus.stayfocused.feature.insights

/** User actions on the Insights screen. */
internal sealed interface InsightsEvent {
    data object PreviousDay : InsightsEvent

    data object NextDay : InsightsEvent

    data class SelectMetric(
        val metric: InsightsMetric,
    ) : InsightsEvent
}
