package com.dculus.stayfocused.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

private const val STATUS_GLOW_ALPHA = 0.275f

/** 8 dp status dot with an optional soft halo. Decorative: hidden from TalkBack. */
@Composable
internal fun StatusDot(
    color: Color,
    glow: Boolean,
) {
    Box(
        Modifier
            .clearAndSetSemantics { }
            .size(8.dp)
            .drawBehind { if (glow) drawCircle(color.copy(alpha = STATUS_GLOW_ALPHA), radius = size.minDimension) }
            .background(color, CircleShape),
    )
}
