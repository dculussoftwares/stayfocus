@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

@Composable
private fun GradientSurface(
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = StayFocusedTheme.colors
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(c.panelGradientStart, c.panelGradientEnd)))
                .border(1.dp, c.hairline08, shape)
                .padding(StayFocusedTheme.spacing.gap16),
        content = content,
    )
}

/** Card surface: gradient `panelGradientStart` to `panelGradientEnd`, r20, hairline border. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = GradientSurface(StayFocusedTheme.shapes.card, modifier, content)

/** The larger variant of [Panel] used for hero areas (r28). */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = GradientSurface(StayFocusedTheme.shapes.hero, modifier, content)

@Preview(widthDp = 360, heightDp = 300)
@Composable
internal fun PanelsPreview() {
    StayFocusedTheme {
        Box(Modifier.background(StayFocusedTheme.colors.background).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.fillMaxWidth()) { MonoLabel("PANEL") }
                HeroCard(Modifier.fillMaxWidth()) { MonoLabel("HERO CARD") }
            }
        }
    }
}
