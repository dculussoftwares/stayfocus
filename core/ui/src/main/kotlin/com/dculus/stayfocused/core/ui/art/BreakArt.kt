@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.art

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.components.DotMatrixText
import com.dculus.stayfocused.core.ui.components.Numerals
import com.dculus.stayfocused.core.ui.theme.PlusJakartaSansFontFamily
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients

/**
 * The art on the break card and sheet: [lengthMins] as dot-matrix numerals next to "min", over a 23-tick ruler with a
 * lime centre tick. Static (nothing animates).
 *
 * It is decorative by default: hidden from TalkBack. Pass [contentDescription] (e.g. "30 minute break") when it is
 * the only place the length shows.
 */
@Composable
fun BreakArt(
    lengthMins: Int,
    modifier: Modifier = Modifier,
    numerals: Numerals = Numerals.Dots,
    contentDescription: String? = null,
) {
    val colors = StayFocusedTheme.colors
    val ticks = remember { breakTicks() }
    Column(
        modifier.clearAndSetSemantics { contentDescription?.let { this.contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
            DotMatrixText(
                text = lengthMins.toString(),
                contentDescription = lengthMins.toString(),
                dotSize = 4.dp,
                color = Color.White,
                numerals = numerals,
            )
            Text(
                text = stringResource(R.string.v5_unit_min),
                color = Color.White,
                style =
                    TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                modifier = Modifier.offset(y = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth(0.78f).height(34.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            ticks.forEach { tick ->
                Box(
                    Modifier
                        .size(tick.width.dp, tick.height.dp)
                        .background(
                            if (tick.isCentre) colors.lime else Color.White.copy(alpha = tick.alpha),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Preview(widthDp = 300, heightDp = 190)
@Composable
internal fun BreakArtPreview() {
    StayFocusedTheme {
        Column(
            Modifier.background(V5Gradients.breakTile.adaptive).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BreakArt(lengthMins = 30, modifier = Modifier.fillMaxWidth())
            BreakArt(lengthMins = 5, modifier = Modifier.fillMaxWidth(), numerals = Numerals.Thin)
        }
    }
}
