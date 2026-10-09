@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import java.time.LocalTime

const val TIME_PICKER_TAG = "wizard_time_picker"
const val TIME_PICKER_CONFIRM_TAG = "wizard_time_picker_confirm"

/**
 * Themed custom time-range picker: pick the start, then the end (24 h dials). A range whose start equals its end
 * never blocks anything, so that choice is rejected with a hint instead of being saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePickerDialog(
    initial: TimeRange,
    onDismiss: () -> Unit,
    onConfirm: (TimeRange) -> Unit,
) {
    val c = StayFocusedTheme.colors
    val startState = rememberTimePickerState(initial.start.hour, initial.start.minute, is24Hour = true)
    val endState = rememberTimePickerState(initial.end.hour, initial.end.minute, is24Hour = true)
    var editingEnd by remember { mutableStateOf(false) }
    val start = LocalTime.of(startState.hour, startState.minute)
    val end = LocalTime.of(endState.hour, endState.minute)
    val same = editingEnd && start == end
    val shape = RoundedCornerShape(28.dp)
    val colors =
        TimePickerDefaults.colors(
            clockDialColor = c.panel,
            clockDialSelectedContentColor = c.onAccent,
            clockDialUnselectedContentColor = c.text,
            selectorColor = c.accent,
            containerColor = c.background,
            periodSelectorBorderColor = c.hairline14,
            timeSelectorSelectedContainerColor = c.accent.copy(alpha = 0.16f),
            timeSelectorUnselectedContainerColor = c.panel,
            timeSelectorSelectedContentColor = c.accent,
            timeSelectorUnselectedContentColor = c.text,
        )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .clip(shape)
                    .background(c.background)
                    .border(1.dp, c.hairline10, shape)
                    .padding(20.dp)
                    .testTag(TIME_PICKER_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(
                    if (editingEnd) R.string.block_wizard_picker_end else R.string.block_wizard_picker_start,
                ),
                style = StayFocusedTheme.type.label,
                color = c.secondary,
            )
            TimePicker(state = if (editingEnd) endState else startState, colors = colors)
            if (same) {
                Text(
                    stringResource(R.string.block_wizard_picker_same_time),
                    style = StayFocusedTheme.type.bodyS,
                    color = c.warning,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton(
                    text = stringResource(R.string.block_wizard_picker_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text =
                        stringResource(
                            if (editingEnd) R.string.block_wizard_picker_set else R.string.block_wizard_picker_next,
                        ),
                    onClick = {
                        if (!editingEnd) {
                            editingEnd = true
                        } else if (start != end) {
                            onConfirm(TimeRange(start, end))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag(TIME_PICKER_CONFIRM_TAG),
                    enabled = !same,
                )
            }
        }
    }
}
