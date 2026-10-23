@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.GoalNote
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.ui.v5.charts.DotGrid
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.GlassTile
import com.dculus.stayfocused.core.ui.v5.components.GlowCard
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

internal const val HOME_AVATAR_TAG = "home_avatar"
internal const val HOME_BELL_TAG = "home_bell"
internal const val HOME_HERO_TAG = "home_hero"
internal const val HOME_BLOCKED_TILE_TAG = "home_blocked_tile"
internal const val HOME_MOST_USED_TILE_TAG = "home_most_used_tile"
internal const val HOME_ALLOW_USAGE_TAG = "home_allow_usage"

/** Hero dot grid: 6 rows, a column is full at 20 min of use in the hour. */
private const val DOT_GRID_MAX_MINS = 20

/** Avatar, date, greeting and the bell (hidden while no phone is linked). */
@Composable
internal fun HomeHeader(
    header: HeaderUi,
    showBell: Boolean,
    bellBadge: Boolean,
    onAvatar: () -> Unit,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = V5Theme.colors
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarDescription = stringResource(R.string.home_avatar_description)
        Box(
            Modifier
                .testTag(HOME_AVATAR_TAG)
                .sizeIn(minWidth = V5Theme.spacing.minTouchTarget, minHeight = V5Theme.spacing.minTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(46.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                val initial = header.initial
                if (initial != null) {
                    Text(
                        initial,
                        style = V5Theme.type.bodyL.copy(fontSize = 17.sp),
                        color = colors.ink,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                } else {
                    PersonGlyph(colors.ink)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                header.dateLabel,
                style = V5Theme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                header.firstName?.let { stringResource(R.string.home_greeting_named, it) }
                    ?: stringResource(R.string.home_greeting),
                style = V5Theme.type.cardTitle.copy(fontSize = 21.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showBell) {
            RoundIconButton(
                icon = V5Icons.bell,
                contentDescription = stringResource(R.string.home_bell_description),
                onClick = onBell,
                modifier = Modifier.testTag(HOME_BELL_TAG),
                showBadge = bellBadge,
                badgeDescription = stringResource(R.string.home_bell_badge_description),
            )
        }
    }
}

/** Head and shoulders, for an avatar without a name (signed out). */
@Composable
private fun PersonGlyph(color: Color) {
    Canvas(Modifier.size(22.dp).clearAndSetSemantics { }) {
        val w = size.width
        drawCircle(color, radius = w * 0.2f, center = Offset(w / 2, w * 0.32f))
        drawArc(
            color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.12f, w * 0.58f),
            size = Size(w * 0.76f, w * 0.7f),
        )
    }
}

/** The violet hero card: today's screen time, goal note, hour dots and the two glass tiles. */
@Composable
internal fun HomeHero(
    gauge: GaugeUi,
    onOpenInsights: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlowCard(
        V5Gradients.violet,
        modifier.fillMaxWidth().testTag(HOME_HERO_TAG),
        radius = 38.dp,
        padding = 14.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.home_hero_title),
                style = V5Theme.type.label,
                color = V5Theme.colors.onGradient,
            )
            when (gauge) {
                is GaugeUi.Ready -> HeroReady(gauge, onOpenInsights)
                GaugeUi.NoAccess -> HeroNoAccess(onAllowUsageAccess)
            }
        }
    }
}

@Composable
private fun HeroReady(
    gauge: GaugeUi.Ready,
    onOpenInsights: () -> Unit,
) {
    val white = V5Theme.colors.onGradient
    val note = goalNoteText(gauge)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            DotMatrixText(
                text = clockMins(gauge.totalMins),
                contentDescription = formatMinutes(gauge.totalMins),
                dotSize = 8.dp,
                color = white,
            )
        }
        Text(note, style = V5Theme.type.bodyL.copy(fontSize = 15.sp), color = white, textAlign = TextAlign.Center)
        DotGrid(
            values = gauge.hourlyMins,
            max = DOT_GRID_MAX_MINS,
            color = white,
            contentDescription = stringResource(R.string.home_dots_description),
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp),
            highlightIndex = gauge.currentHour,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BlockedTile(gauge.blocked, onOpenInsights, Modifier.weight(1f))
            MostUsedTile(gauge.mostUsed, onOpenInsights, Modifier.weight(1f))
        }
    }
}

@Composable
private fun goalNoteText(gauge: GaugeUi.Ready): String {
    val goal = v5Duration(gauge.goalMins)
    return when (val note = gauge.goalNote) {
        is GoalNote.Under -> stringResource(R.string.home_goal_under, note.mins, goal)

        is GoalNote.Over -> stringResource(R.string.home_goal_over, note.mins, goal)

        // Prototype: today <= goal reads "0 min under your goal".
        GoalNote.OnGoal -> stringResource(R.string.home_goal_under, 0, goal)
    }
}

@Composable
private fun BlockedTile(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val white = V5Theme.colors.onGradient
    GlassTile(
        modifier.testTag(HOME_BLOCKED_TILE_TAG),
        onClick = onClick,
        contentDescription = stringResource(R.string.home_blocked_description, count),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TileHeader(stringResource(R.string.home_blocked_label)) {
                Icon(V5Icons.block, contentDescription = null, tint = white, modifier = Modifier.size(15.dp))
            }
            TileValue(count.toString(), stringResource(R.string.home_blocked_unit))
            Text(
                stringResource(R.string.home_blocked_caption),
                style = V5Theme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = white,
            )
        }
    }
}

@Composable
private fun MostUsedTile(
    mostUsed: MostUsedUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val white = V5Theme.colors.onGradient
    GlassTile(
        modifier.testTag(HOME_MOST_USED_TILE_TAG),
        onClick = onClick,
        contentDescription =
            mostUsed?.let { stringResource(R.string.home_most_used_description, it.label, it.mins) }
                ?: stringResource(R.string.home_most_used_label),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TileHeader(stringResource(R.string.home_most_used_label)) {
                Text(
                    mostUsed
                        ?.label
                        ?.firstOrNull()
                        ?.uppercase()
                        .orEmpty(),
                    style = V5Theme.type.label.copy(fontWeight = FontWeight.Bold),
                    color = white,
                )
            }
            TileValue((mostUsed?.mins ?: 0).toString(), stringResource(R.string.home_most_used_unit))
            Text(
                mostUsed?.label.orEmpty(),
                style = V5Theme.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TileHeader(
    label: String,
    icon: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).background(Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(label, style = V5Theme.type.label, color = V5Theme.colors.onGradient, maxLines = 1)
    }
}

@Composable
private fun TileValue(
    value: String,
    unit: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        DotMatrixText(value, contentDescription = value, dotSize = 5.dp, color = V5Theme.colors.onGradient)
        Text(unit, style = V5Theme.type.label, color = V5Theme.colors.onGradient)
    }
}

/** Usage access is off: nothing to measure. Keeps the shipped prompt (copy unchanged). */
@Composable
private fun HeroNoAccess(onAllowUsageAccess: () -> Unit) {
    val white = V5Theme.colors.onGradient
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.home_usage_off_title),
            style = V5Theme.type.cardTitle,
            color = white,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.home_usage_off_body),
            style = V5Theme.type.body,
            color = white,
            textAlign = TextAlign.Center,
        )
        PillButton(
            text = stringResource(R.string.home_usage_allow),
            onClick = onAllowUsageAccess,
            variant = PillButtonVariant.White,
            size = PillButtonSize.Compact,
            modifier = Modifier.testTag(HOME_ALLOW_USAGE_TAG),
        )
    }
}
