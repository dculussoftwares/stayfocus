@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.model.RulerScales
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

/** Wizard step 3 rule card (12-new-block-3-use-then-lock.png): the "Use for" ruler at 10 min. */
@Preview(widthDp = 390, heightDp = 330)
@Composable
internal fun TickRulerWizardPreview() {
    StayFocusedTheme {
        Column(
            Modifier.background(SolidColor(StayFocusedTheme.colors.background)).padding(14.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(36.dp))
                        .background(V5Gradients.typeCycle.adaptive)
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                var minutes by remember { mutableIntStateOf(10) }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "$minutes",
                        style = StayFocusedTheme.type.displayXL,
                        color = StayFocusedTheme.colors.onGradient,
                    )
                    Text(
                        "min",
                        style = StayFocusedTheme.type.rule.copy(fontSize = 24.sp, fontWeight = FontWeight.Light),
                        color = StayFocusedTheme.colors.onGradient,
                    )
                }
                Text(
                    "open before it locks",
                    style = StayFocusedTheme.type.bodyL.copy(fontSize = 15.sp),
                    color = StayFocusedTheme.colors.onGradient,
                )
                TickRuler(
                    value = minutes,
                    onValueChange = { minutes = it },
                    scale = RulerScales.Use,
                    colors = TickRulerColors.onGradient(),
                    contentDescription = "Minutes open before it locks",
                    valueDescription = "$minutes minutes",
                )
                RulerLabels(RulerScales.Use.min, RulerScales.Use.max)
            }
        }
    }
}

/** Break sheet (06-take-a-break-sheet.png): the ruler at 30 min, passed orange, ahead grey, current ink. */
@Preview(widthDp = 390, heightDp = 300)
@Composable
internal fun TickRulerBreakSheetPreview() {
    StayFocusedTheme {
        Column(
            modifier =
                Modifier
                    .background(SolidColor(StayFocusedTheme.colors.surfaceRaised))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "30",
                    style = StayFocusedTheme.type.displayXL.copy(fontSize = 88.sp),
                    color = StayFocusedTheme.colors.ink,
                )
                Text(
                    "min",
                    style = StayFocusedTheme.type.rule.copy(fontSize = 24.sp, fontWeight = FontWeight.Light),
                    color = StayFocusedTheme.colors.textSecondary,
                )
            }
            TickRuler(
                value = 30,
                onValueChange = {},
                scale = RulerScales.Break,
                colors = TickRulerColors.breakSheet(),
                contentDescription = "Break length",
                valueDescription = "30 minutes",
            )
        }
    }
}

/** Both ends and a mid-range value, to eyeball the edge ticks. */
@Preview(widthDp = 390, heightDp = 360)
@Composable
internal fun TickRulerEdgesPreview() {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(V5Gradients.typeNow.adaptive).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            listOf(RulerScales.Now.min, 255, RulerScales.Now.max).forEach { v ->
                TickRuler(
                    value = v,
                    onValueChange = {},
                    scale = RulerScales.Now,
                    colors = TickRulerColors.onGradient(),
                    contentDescription = "Block length",
                    valueDescription = "$v minutes",
                )
            }
            RulerLabels(RulerScales.Now.min, RulerScales.Now.max)
        }
    }
}
