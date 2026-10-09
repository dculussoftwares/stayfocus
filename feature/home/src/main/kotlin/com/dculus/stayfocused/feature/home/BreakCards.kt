@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.components.DialPresets
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.RotaryDial
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val BREAK_IDLE_CARD_TAG = "home_break_idle_card"
internal const val BREAK_ACTIVE_CARD_TAG = "home_break_active_card"
internal const val BREAK_END_TAG = "home_break_end"
internal const val BREAK_COUNTDOWN_TAG = "home_break_countdown"
internal const val BREAK_START_TAG = "home_break_start"

private val CardShape = RoundedCornerShape(28.dp)

/** Idle: the lime "Take a break" card that opens the sheet. */
@Composable
internal fun BreakIdleCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    Row(
        modifier
            .testTag(BREAK_IDLE_CARD_TAG)
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.break_card_title),
                style = StayFocusedTheme.type.displayS.copy(fontSize = 24.sp, lineHeight = 24.sp),
                color = colors.onAccent,
            )
            Text(
                stringResource(R.string.break_card_subtitle),
                style = StayFocusedTheme.type.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = colors.onAccent,
                modifier = Modifier.alpha(0.7f),
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .background(colors.onAccent, RoundedCornerShape(18.dp))
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text("→", color = colors.accent, fontSize = 22.sp)
        }
    }
}

/** Active: live countdown, 30-segment progress and "End early". */
@Composable
internal fun BreakActiveCard(
    state: BreakUi.Active,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val ink = colors.onAccent

    // Ink at 25% over the lime card, as an opaque colour so every renderer draws the same pixels.
    val used = lerp(colors.accent, ink, 0.25f)
    val description = stringResource(R.string.break_countdown_description, state.countdown)
    Column(
        modifier
            .testTag(BREAK_ACTIVE_CARD_TAG)
            .fillMaxWidth()
            .background(colors.accent, CardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.break_active_label),
                style = StayFocusedTheme.type.label.copy(fontWeight = FontWeight.Bold),
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .testTag(BREAK_END_TAG)
                    .sizeIn(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onEnd)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.break_end_early),
                    style = StayFocusedTheme.type.body.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                    color = ink,
                    modifier =
                        Modifier
                            .border(BorderStroke(1.5.dp, ink), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            state.countdown,
            style =
                StayFocusedTheme.type.numeric.copy(
                    fontSize = 60.sp,
                    lineHeight = 60.sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = ink,
            modifier =
                Modifier
                    .testTag(BREAK_COUNTDOWN_TAG)
                    .semantics { contentDescription = description },
        )
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val elapsed = state.elapsedSegments
            repeat(BREAK_SEGMENTS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(if (index < elapsed) used else ink, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

/** Body of the "Take a break" sheet: dial, presets, note and the start button. */
@Composable
internal fun BreakSheetContent(
    mins: Int,
    onMinsChange: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    Column(
        // Scrolls on short screens, landscape and large font sizes so the start button stays reachable.
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.break_sheet_title),
                style = StayFocusedTheme.type.displayS,
                color = colors.text,
            )
            Text(
                stringResource(R.string.break_sheet_hint),
                style = StayFocusedTheme.type.label,
                color = colors.secondary,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        RotaryDial(value = mins, onValueChange = onMinsChange, config = DialConfigs.Break)
        DialPresets(DialConfigs.Break, value = mins, onPick = onMinsChange)
        Text(
            stringResource(R.string.break_sheet_note),
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
            color = colors.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = stringResource(R.string.break_sheet_start, durationLabel(mins)),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().testTag(BREAK_START_TAG),
        )
    }
}

@Preview(widthDp = 360, heightDp = 420)
@Composable
internal fun BreakCardsPreview() {
    StayFocusedTheme {
        Column(
            Modifier.background(StayFocusedTheme.colors.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BreakIdleCard(onClick = {})
            BreakActiveCard(BreakUi.Active(remainingMs = 18 * 60_000L + 4_000L, totalMs = 30 * 60_000L), onEnd = {})
        }
    }
}
