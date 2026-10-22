package com.dculus.stayfocused.feature.insights

/** User actions on the Insights screen. */
internal sealed interface InsightsEvent {
    data class SelectRange(
        val range: InsightsRange,
    ) : InsightsEvent

    /** v5-extra: M13-04. */
    data object PreviousDay : InsightsEvent

    /** v5-extra: M13-04. */
    data object NextDay : InsightsEvent

    /** v5-extra: M13-04. */
    data class SelectMetric(
        val metric: InsightsMetric,
    ) : InsightsEvent
}
