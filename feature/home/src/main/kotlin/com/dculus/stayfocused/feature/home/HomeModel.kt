package com.dculus.stayfocused.feature.home

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.LedColumn
import com.dculus.stayfocused.core.model.LinkedDevice
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows of the mini LED chart on the gauge card. */
internal const val HOME_LED_ROWS = 5

private const val NOON_HOUR = 12
private const val EVENING_HOUR = 18

internal enum class Greeting {
    MORNING,
    AFTERNOON,
    EVENING,
    ;

    companion object {
        fun forHour(hour: Int): Greeting =
            when {
                hour < NOON_HOUR -> MORNING
                hour < EVENING_HOUR -> AFTERNOON
                else -> EVENING
            }
    }
}

/** Top of Home: the mono date line, the greeting and the avatar. [firstName] is null without an account. */
internal data class HeaderUi(
    val dateLabel: String,
    val greeting: Greeting,
    val firstName: String?,
) {
    /** Letter on the avatar, or null (no account) for the generic person glyph. */
    val initial: String? get() = firstName?.firstOrNull()?.uppercase()
}

private val DateLineFormat = DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm")

/** "MON 06 OCT · 10:42" (prototype header line). */
internal fun headerFor(
    now: ZonedDateTime,
    firstName: String?,
    locale: Locale = Locale.getDefault(),
): HeaderUi =
    HeaderUi(
        dateLabel = DateLineFormat.withLocale(locale).format(now).uppercase(locale),
        greeting = Greeting.forHour(now.hour),
        firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
    )

/** The gauge card. */
internal sealed interface GaugeUi {
    /** Usage access is off: nothing can be measured. */
    data object NoAccess : GaugeUi

    /**
     * Today's numbers. [avgMins] is null until a complete day of history exists. [levels] are the 24 hourly LED
     * columns of the mini chart.
     */
    data class Ready(
        val totalMins: Int,
        val avgMins: Int?,
        val levels: List<LedColumn>,
        val launches: Int,
        val unlocks: Int,
        val blocked: Int,
    ) : GaugeUi {
        /** Minutes above (+) or below (-) the average, or null without one. */
        val deltaMins: Int? get() = avgMins?.let { totalMins - it }
    }
}

/** One "Active blocks" row. [appLabels] are the display names of the block's apps. */
internal data class HomeBlockUi(
    val block: Block,
    val appLabels: List<String>,
)

/** Everything on Home except the break card. */
internal data class HomeUiState(
    val header: HeaderUi,
    val gauge: GaugeUi,
    val blocks: List<HomeBlockUi>,
    val devices: List<LinkedDevice>,
)
