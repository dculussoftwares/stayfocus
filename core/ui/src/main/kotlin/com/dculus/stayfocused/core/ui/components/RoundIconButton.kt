@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

private const val DARK_FILL_ALPHA = 0.14f
private const val DISABLED_ALPHA = 0.4f

/**
 * 46 dp circle with an 18 dp stroke [icon] (back, bell, close …). White with an ink icon by default; [dark] is the
 * white .14 glass variant for the scan screen. [showBadge] adds the red 9 dp dot with a 2 dp white border (bell);
 * [badgeDescription] is announced as the button's state while the badge is shown. The touch target is 48 dp.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    showBadge: Boolean = false,
    badgeDescription: String? = null,
    enabled: Boolean = true,
) {
    val c = StayFocusedTheme.colors
    Box(
        modifier =
            modifier
                .sizeIn(
                    minWidth = StayFocusedTheme.spacing.minTouchTarget,
                    minHeight = StayFocusedTheme.spacing.minTouchTarget,
                ).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                    if (showBadge && badgeDescription != null) stateDescription = badgeDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(StayFocusedTheme.spacing.roundButtonSize)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .clip(CircleShape)
                    .background(if (dark) c.onGradient.copy(alpha = DARK_FILL_ALPHA) else c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (dark) c.onGradient else c.ink,
                modifier = Modifier.size(18.dp),
            )
            if (showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 9.dp)
                        .size(13.dp)
                        .background(c.red, CircleShape)
                        .border(2.dp, c.surface, CircleShape),
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 160)
@Composable
internal fun RoundIconButtonPreview() {
    V5PreviewSurface(gradient = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton(V5Icons.back, "Back", onClick = {})
            RoundIconButton(V5Icons.bell, "Alerts", onClick = {}, showBadge = true, badgeDescription = "New alert")
            RoundIconButton(V5Icons.plus, "New block", onClick = {}, enabled = false)
        }
        Box(
            Modifier
                .background(V5Gradients.scan.adaptive)
                .size(width = 354.dp, height = 60.dp),
        ) {
            RoundIconButton(
                V5Icons.back,
                "Back",
                onClick = {},
                dark = true,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}
