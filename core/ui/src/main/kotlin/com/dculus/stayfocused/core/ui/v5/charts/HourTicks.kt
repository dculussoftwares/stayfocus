@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.charts

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val AXIS_HOURS = listOf(6, 12, 18, 23)

/**
 * Short hour label for [hour] (0..23) in [locale]: "6 AM" in English, "06" in 24-hour locales or when
 * [is24Hour] is set (V5_SPEC §4 "locale-aware time format").
 */
internal fun hourLabel(
    hour: Int,
    locale: Locale,
    is24Hour: Boolean,
): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "H" else "ha")
    val utc = TimeZone.getTimeZone("UTC")
    val time =
        Calendar
            .getInstance(utc, locale)
            .apply {
                clear()
                set(Calendar.HOUR_OF_DAY, hour)
            }.time
    return SimpleDateFormat(pattern, locale).apply { timeZone = utc }.format(time)
}

/**
 * Child's usage between 06:00 and 23:59: three 2 dp ticks per hour (54 in all), each `max(3, v / 25 × 34)` dp tall
 * (capped at 34 dp) and pink when that hour has any usage, hairline otherwise, with axis labels 6 AM / 12 PM / 6 PM /
 * 11 PM below. [hourlyMins] holds minutes per hour of day (index 0..23; hours 0–5 are not drawn, missing hours are 0).
 * [contentDescription] is the caller's TalkBack summary (e.g. "Screen time by hour, peak 19 min at 9 PM").
 */
@Composable
fun HourTicks(
    hourlyMins: List<Int>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = V5Theme.colors
    val minutes = remember(hourlyMins) { hourTickMinutes(hourlyMins) }
    val heights =
        remember(minutes) { minutes.flatMap { m -> List(HOUR_TICKS_PER_HOUR) { hourTickHeightDp(m).dp } } }
    val tickColors =
        remember(minutes, colors) {
            minutes.flatMap { m -> List(HOUR_TICKS_PER_HOUR) { if (m > 0) colors.pink else colors.hairline } }
        }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val labels =
        remember(configuration) {
            val locale = ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.getDefault()
            val is24Hour = DateFormat.is24HourFormat(context)
            AXIS_HOURS.map { hourLabel(it, locale, is24Hour) }
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(HOUR_TICKS_MAX_DP.dp)) { drawTicks(heights, tickColors) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = V5Theme.type.body.copy(fontSize = 12.sp), color = colors.textSecondary) }
        }
    }
}
