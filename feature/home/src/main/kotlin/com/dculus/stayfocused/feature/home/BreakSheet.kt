@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.DialConfigs
import com.dculus.stayfocused.core.model.durationLabel
import com.dculus.stayfocused.core.ui.components.DialPresets
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.RotaryDial
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val BREAK_START_TAG = "home_break_start"

/**
 * Body of the "Take a break" sheet: dial, presets, note and the start button. This is the shipped (v2) sheet; M12-08
 * replaces it with the v5 ruler sheet.
 */
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
