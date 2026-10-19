@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

private const val TOGGLE_MILLIS = 150
private const val DISABLED_ALPHA = 0.4f
private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val KnobSize = 26.dp
private val TrackPadding = 3.dp

/**
 * 52 × 32 switch (ink track when on, hairline when off, white 26 dp knob that slides in 150 ms). Exposes [Role.Switch]
 * and an "On" / "Off" state description; the touch target is at least 48 dp. Pass `null` for [onCheckedChange] to show
 * the state without handling taps (e.g. inside a row that is itself toggleable).
 */
@Composable
fun V5Toggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = V5Theme.colors
    val track by animateColorAsState(if (checked) c.ink else c.hairline, tween(TOGGLE_MILLIS), label = "toggleTrack")
    val knobX by animateDpAsState(
        if (checked) TrackWidth - KnobSize - TrackPadding else TrackPadding,
        tween(TOGGLE_MILLIS),
        label = "toggleKnob",
    )
    val state = stringResource(if (checked) R.string.v5_toggle_on else R.string.v5_toggle_off)
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = V5Theme.spacing.minTouchTarget, minHeight = V5Theme.spacing.minTouchTarget)
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
                ).semantics { stateDescription = state },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(TrackWidth, TrackHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(CircleShape)
                .background(track),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobX)
                    .shadow(
                        3.dp,
                        CircleShape,
                        ambientColor = c.ink.copy(alpha = 0.2f),
                        spotColor = c.ink.copy(alpha = 0.2f),
                    ).size(KnobSize)
                    .background(c.surface, CircleShape),
            )
        }
    }
}

@Preview(widthDp = 260, heightDp = 100)
@Composable
internal fun V5TogglePreview() {
    V5PreviewSurface(gradient = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            V5Toggle(checked = true, onCheckedChange = {})
            V5Toggle(checked = false, onCheckedChange = {})
            V5Toggle(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
