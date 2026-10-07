@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private const val TOGGLE_MILLIS = 150
private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val KnobSize = 20.dp
private val KnobInset = 4.dp

/**
 * Switch: lime track with an ink knob when on, `track` with a `tertiary` knob when off (150 ms).
 * Exposes [Role.Switch]; pass a [modifier] with `semantics { contentDescription }` or place it
 * next to a label that merges semantics.
 */
@Composable
fun SfToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = StayFocusedTheme.colors
    val spec = tween<androidx.compose.ui.graphics.Color>(TOGGLE_MILLIS)
    val trackColor by animateColorAsState(if (checked) c.accent else c.track, spec, label = "track")
    val knobColor by animateColorAsState(if (checked) c.onAccent else c.tertiary, spec, label = "knob")
    val knobX by animateDpAsState(
        if (checked) TrackWidth - KnobSize - KnobInset else KnobInset,
        tween(TOGGLE_MILLIS),
        label = "knobX",
    )
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(
                    if (onCheckedChange != null) {
                        Modifier.toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                    } else {
                        Modifier.semantics {
                            role = Role.Switch
                            toggleableState = ToggleableState(checked)
                        }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(TrackWidth)
                .size(TrackWidth, TrackHeight)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobX)
                    .size(KnobSize)
                    .clip(CircleShape)
                    .background(knobColor),
            )
        }
    }
}

@Preview(widthDp = 160, heightDp = 64)
@Composable
internal fun SfTogglePreview() {
    StayFocusedTheme {
        Row(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SfToggle(checked = true, onCheckedChange = {})
            SfToggle(checked = false, onCheckedChange = {})
        }
    }
}
