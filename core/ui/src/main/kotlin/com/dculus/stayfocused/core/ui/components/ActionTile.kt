@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Colors
import com.dculus.stayfocused.core.ui.theme.V5Gradient
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.glow

private val ActionTileHeight = 204.dp
private val ActionTileRadius = 36.dp
private val ActionTilePadding = 18.dp
private val PlusCircleSize = 46.dp
private val PlusCircleInset = 14.dp
private val PlusIconSize = 18.dp
private val PlusShadow = 4.dp
private val TileShadow = 12.dp
private const val SHADOW_ALPHA = 0.12f
private const val TILE_SHADOW_ALPHA = 0.35f

/**
 * Home / Blocks action tile (V5_SPEC §4 `ActionTile`): 204 dp tall, radius 36, [title] top-left (20/500, use `\n`
 * for the prototype's two-line titles), a 46 dp white "+" circle top-right and a free [content] slot (a [BoxScope], so
 * it can place art and a bottom label) drawn under the title and the "+".
 *
 * Without a [gradient] it is a `surfaceRaised` card with the soft ink shadow and an ink title; with one it is a
 * [GlowCard]-style gradient with grain and white text. The whole tile is a single button: its TalkBack label is the
 * title plus any text in [content], or [contentDescription] when given. The "+" is decorative;
 * [showPlus] = false hides it (the running break tile).
 */
@Composable
fun ActionTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: V5Gradient? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    showPlus: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = StayFocusedTheme.colors
    val shape = RoundedCornerShape(ActionTileRadius)
    val inkShadow = colors.ink.copy(alpha = TILE_SHADOW_ALPHA)
    val background =
        if (gradient == null) {
            Modifier
                .shadow(TileShadow, shape, ambientColor = inkShadow, spotColor = inkShadow)
                .clip(shape)
                .background(colors.surfaceRaised)
        } else {
            Modifier.glow(gradient, shape)
        }
    val foreground = if (gradient == null) colors.ink else colors.onGradient
    CompositionLocalProvider(LocalContentColor provides foreground) {
        Box(
            modifier
                .height(ActionTileHeight)
                .then(background)
                .cardClickable(shape, onClick, onClickLabel, contentDescription),
        ) {
            content()
            Text(
                text = title,
                style = StayFocusedTheme.type.cardTitle,
                color = foreground,
                modifier = Modifier.align(Alignment.TopStart).padding(ActionTilePadding),
            )
            if (showPlus) PlusCircle(colors, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun PlusCircle(
    colors: V5Colors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(PlusCircleInset)
            .shadow(
                PlusShadow,
                CircleShape,
                ambientColor = colors.ink.copy(alpha = SHADOW_ALPHA),
                spotColor = colors.ink.copy(alpha = SHADOW_ALPHA),
            ).size(PlusCircleSize)
            .background(colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            V5Icons.plus,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(PlusIconSize),
        )
    }
}

@Composable
private fun ActionTilesPair(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile("Block\napps", onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .height(62.dp)
                    .background(StayFocusedTheme.colors.surface, RoundedCornerShape(28.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Active blocks", style = StayFocusedTheme.type.bodyL)
            }
        }
        ActionTile(
            "Take a\nbreak",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            gradient = V5Gradients.brand,
        ) {
            Text(
                "Tap to end early",
                style = StayFocusedTheme.type.label,
                modifier = Modifier.align(Alignment.BottomStart).padding(ActionTilePadding),
            )
        }
    }
}

/** Raised and gradient action tiles on the v5 background. */
@Preview(widthDp = 390, heightDp = 480)
@Composable
internal fun ActionTilePreview() {
    V5PreviewSurface(gradient = false) { ActionTilesPair() }
}

/** The same two tiles on the welcome gradient. */
@Preview(widthDp = 390, heightDp = 480)
@Composable
internal fun ActionTileOnGradientPreview() {
    V5PreviewSurface(gradient = true) { ActionTilesPair() }
}
