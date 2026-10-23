package com.dculus.stayfocused.core.ui.charts

/** The prototype's sample data (`ME_H`, `KID_H`, `WEEK`) used by the previews and screenshot tests. */
internal object ChartSamples {
    val meHourly = listOf(0, 0, 0, 0, 0, 0, 0, 4, 9, 6, 3, 7, 15, 9, 5, 4, 11, 18, 13, 6, 14, 19, 8, 2)
    val kidHourly = listOf(0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 12, 20, 25, 10, 8, 18, 9, 0, 0)
    val week = listOf(182, 164, 201, 140, 175, 220, 153)
    val dayAxis = listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM")
    val weekAxis = listOf("M", "T", "W", "T", "F", "S", "S")
    const val ME_HIGHLIGHT_HOUR = 21
}
