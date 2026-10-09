@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.ui.components.IconTile
import com.dculus.stayfocused.core.ui.components.LedBarChart
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.ScreenTimeGauge
import com.dculus.stayfocused.core.ui.components.SfToggle
import com.dculus.stayfocused.core.ui.components.TypeChip
import com.dculus.stayfocused.core.ui.components.WhiteButton
import com.dculus.stayfocused.core.ui.components.blockDetail
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private val GaugeCardTop = Color(0xFF171915)
private val GaugeCardBottom = Color(0xFF121410)
private val GaugeCardShape = RoundedCornerShape(28.dp)
private const val GAUGE_MAX_MINS = 240

@Composable
private fun GaugeCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(GaugeCardShape)
            .background(Brush.verticalGradient(listOf(GaugeCardTop, GaugeCardBottom)))
            .border(1.dp, StayFocusedTheme.colors.hairline08, GaugeCardShape)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** The screen-time card: tap it for Insights, or allow usage access when that is off. */
@Composable
internal fun GaugeCard(
    gauge: GaugeUi,
    onOpenInsights: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (gauge) {
        GaugeUi.NoAccess -> GaugeNoAccessCard(onAllowUsageAccess, modifier)
        is GaugeUi.Ready -> GaugeReadyCard(gauge, onOpenInsights, modifier)
    }
}

@Composable
private fun GaugeReadyCard(
    gauge: GaugeUi.Ready,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    val description = stringResource(R.string.home_gauge_description, formatMinutes(gauge.totalMins))
    GaugeCardSurface(
        modifier
            .testTag(HOME_GAUGE_CARD_TAG)
            .clickable(role = Role.Button, onClick = onOpenInsights)
            .semantics { contentDescription = description },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GaugeCaption(stringResource(R.string.home_screen_time), c.secondary, FontWeight.SemiBold, 0.1f)
            GaugeCaption(stringResource(R.string.home_insights_link), c.accent, FontWeight.SemiBold, 0.06f)
        }
        GaugeWithReadout(gauge.totalMins, gauge.avgMins) {
            Text(
                formatMinutes(gauge.totalMins),
                style = StayFocusedTheme.type.title.copy(fontSize = 30.sp, lineHeight = 30.sp),
                color = c.text,
                maxLines = 1,
            )
            Text(
                deltaLine(gauge),
                style = StayFocusedTheme.type.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                color = c.secondary,
                textAlign = TextAlign.Center,
            )
        }
        LedBarChart(
            levels = gauge.levels,
            rows = HOME_LED_ROWS,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            cellHeight = 5.dp,
            cellRadius = 1.5.dp,
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(c.hairline08),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Stat(gauge.launches, R.string.home_stat_launches, c.text, Modifier.weight(1f))
            Stat(gauge.unlocks, R.string.home_stat_unlocks, c.text, Modifier.weight(1f))
            Stat(gauge.blocked, R.string.home_stat_blocked, c.accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun deltaLine(gauge: GaugeUi.Ready): String {
    val avg = gauge.avgMins ?: return stringResource(R.string.home_gauge_no_avg)
    val delta = gauge.deltaMins ?: 0
    return when {
        delta > 0 -> stringResource(R.string.home_gauge_over, formatMinutes(delta), formatMinutes(avg))
        delta < 0 -> stringResource(R.string.home_gauge_under, formatMinutes(-delta), formatMinutes(avg))
        else -> stringResource(R.string.home_gauge_on_avg, formatMinutes(avg))
    }
}

@Composable
private fun GaugeCaption(
    text: String,
    color: Color,
    weight: FontWeight,
    spacing: Float,
) {
    Text(
        text,
        style =
            StayFocusedTheme.type.label.copy(
                fontSize = 10.5.sp,
                fontWeight = weight,
                letterSpacing = spacing.em,
            ),
        color = color,
    )
}

/** The gauge with its "0h" and "4h" scale labels; [readout] fills the centre. */
@Composable
private fun GaugeWithReadout(
    valueMins: Int,
    avgMins: Int?,
    readout: @Composable () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Box(Modifier.size(300.dp, 176.dp)) {
        ScreenTimeGauge(valueMins = valueMins, avgMins = avgMins, maxMins = GAUGE_MAX_MINS) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                readout()
            }
        }
        val scale = StayFocusedTheme.type.labelS.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(
            stringResource(R.string.home_gauge_min),
            style = scale,
            color = c.tertiary,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp).clearAndSetSemantics { },
        )
        Text(
            stringResource(R.string.home_gauge_max),
            style = scale,
            color = c.tertiary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp).clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Stat(
    value: Int,
    label: Int,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value.toString(),
            style = StayFocusedTheme.type.numeric.copy(fontSize = 20.sp, lineHeight = 20.sp),
            color = valueColor,
        )
        Text(
            stringResource(label),
            style = StayFocusedTheme.type.labelS.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08f.em),
            color = c.secondary,
        )
    }
}

@Composable
private fun GaugeNoAccessCard(
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StayFocusedTheme.colors
    GaugeCardSurface(modifier.testTag(HOME_GAUGE_CARD_TAG)) {
        Row(Modifier.fillMaxWidth()) {
            GaugeCaption(stringResource(R.string.home_screen_time), c.secondary, FontWeight.SemiBold, 0.1f)
        }
        GaugeWithReadout(valueMins = 0, avgMins = null) {
            Text(
                stringResource(R.string.home_usage_off_title),
                style = StayFocusedTheme.type.title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = c.text,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.home_usage_off_body),
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
            color = c.secondary,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.home_usage_allow),
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().testTag(HOME_ALLOW_USAGE_TAG),
        )
    }
}
