@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.RulerScales
import com.dculus.stayfocused.core.model.v5BigValue
import com.dculus.stayfocused.core.model.v5Duration
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.TickRuler
import com.dculus.stayfocused.core.ui.components.TickRulerColors
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val BREAK_START_TAG = "home_break_start"

/** Preset lengths of the break sheet (prototype `breakPresets`: 15 min / 30 min / 1h / 1h 30m). */
internal val BREAK_PRESETS = listOf(15, 30, 60, 90)

/**
 * Body of the v5 "Take a break" sheet (`06-take-a-break-sheet.png`): title and subtitle, the big value with its unit,
 * the break-coloured [TickRuler] (5 to 120 min, step 5), four presets and the solid ink start button.
 * Put it inside a `V5BottomSheet`.
 */
@Composable
internal fun BreakSheetContent(
    mins: Int,
    onMinsChange: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val big = v5BigValue(mins)
    val length = v5Duration(mins)
    Column(
        // Scrolls on short screens, landscape and large font sizes so the start button stays reachable.
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.break_sheet_title), style = type.heading, color = colors.ink)
            Text(stringResource(R.string.break_sheet_subtitle), style = type.body, color = colors.textSecondary)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics { contentDescription = length },
        ) {
            Text(big.value, style = type.displayXL.copy(fontSize = 88.sp), color = colors.ink)
            Text(
                big.unit,
                style = type.rule.copy(fontSize = 24.sp),
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        TickRuler(
            value = mins,
            onValueChange = onMinsChange,
            scale = RulerScales.Break,
            colors = TickRulerColors.breakSheet(),
            contentDescription = stringResource(R.string.break_sheet_ruler),
            valueDescription = length,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BREAK_PRESETS.forEach { preset ->
                PresetChip(
                    label = v5Duration(preset),
                    selected = preset == mins,
                    onClick = { onMinsChange(preset) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PillButton(
            text = stringResource(R.string.break_sheet_start, length),
            onClick = onStart,
            variant = PillButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth().testTag(BREAK_START_TAG),
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    Box(
        modifier
            // 48 dp (prototype 46): the minimum touch target.
            .heightIn(min = 48.dp)
            .clip(StayFocusedTheme.shapes.pill)
            .background(if (selected) colors.ink else colors.surface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = StayFocusedTheme.type.bodyL.copy(fontSize = 15.sp),
            color = if (selected) colors.onGradient else colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
