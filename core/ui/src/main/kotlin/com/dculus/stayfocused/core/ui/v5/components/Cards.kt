@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradient
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.glow

/** Radius of a [V5Card] and [GlassTile] unless the design says otherwise (V5_SPEC §3: cards 28–36). */
private val DefaultCardRadius = 28.dp

/** Radius of a [GlowCard]: the prototype's gradient cards are r36. */
private val DefaultGlowRadius = 36.dp
private val DefaultCardPadding = 18.dp
private val GlassTilePadding = 14.dp
private const val CARD_SHADOW_ALPHA = 0.35f
private val CardShadowElevation = 12.dp
private const val GLASS_FILL_ALPHA = 0.18f
private const val GLASS_BORDER_ALPHA = 0.3f
private const val GLASS_BLUR_RADIUS = 16f

/**
 * Makes a card tappable: a ripple clipped to [shape] (so it never spills past the rounded corners) and, when
 * tappable, a single TalkBack node (button) whose label merges every child's text, or [contentDescription] if given.
 * A non-clickable card is left alone so its children stay individually reachable.
 */
internal fun Modifier.cardClickable(
    shape: Shape,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    contentDescription: String?,
): Modifier =
    if (onClick == null) {
        this
    } else {
        clip(shape)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
    }

/**
 * White card with a soft ink shadow (V5_SPEC §4 `V5Card`): `surface` by default, `surfaceRaised` for the Home tiles.
 *
 * Pass [onClick] to make the whole card one button with a ripple clipped to its shape. Its TalkBack label is the merged
 * text of its content, or [contentDescription] when given; [onClickLabel] names the action ("Open block").
 */
@Composable
fun V5Card(
    modifier: Modifier = Modifier,
    radius: Dp = DefaultCardRadius,
    containerColor: Color = V5Theme.colors.surface,
    padding: Dp = DefaultCardPadding,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val shadow = V5Theme.colors.ink.copy(alpha = CARD_SHADOW_ALPHA)
    CompositionLocalProvider(LocalContentColor provides V5Theme.colors.ink) {
        Column(
            modifier =
                modifier
                    .shadow(CardShadowElevation, shape, ambientColor = shadow, spotColor = shadow)
                    .clip(shape)
                    .background(containerColor)
                    .cardClickable(shape, onClick, onClickLabel, contentDescription)
                    .padding(padding),
            content = content,
        )
    }
}

/**
 * Gradient card with grain (V5_SPEC §4 `GlowCard`): paints [gradient] and the grain, clips its children to the
 * rounded shape and draws them in white. Same click and TalkBack behaviour as [V5Card].
 */
@Composable
fun GlowCard(
    gradient: V5Gradient,
    modifier: Modifier = Modifier,
    radius: Dp = DefaultGlowRadius,
    padding: Dp = DefaultCardPadding,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    CompositionLocalProvider(LocalContentColor provides V5Theme.colors.onGradient) {
        Column(
            modifier =
                modifier
                    .glow(gradient, shape)
                    .cardClickable(shape, onClick, onClickLabel, contentDescription)
                    .padding(padding),
            content = content,
        )
    }
}

/** Whether [GlassTile] draws its blurred layer: `RenderEffect` blur needs API 31. Below it the fill alone is used. */
internal fun glassBlurSupported(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= Build.VERSION_CODES.S

/**
 * Frosted tile for use on a gradient (V5_SPEC §4 `GlassTile`): white .18 fill, 1 dp white .3 border, white content.
 * On API 31+ the fill layer carries a 16 dp blur; below that it is the plain fill (the spec accepts this).
 * Compose cannot sample the pixels behind a composable, so the blur softens the tile's own layer, not the backdrop;
 * the frosted look comes from the translucent fill over the gradient.
 *
 * [blur] only exists so tests can force the fallback; leave it at its default.
 */
@Composable
fun GlassTile(
    modifier: Modifier = Modifier,
    radius: Dp = DefaultCardRadius,
    padding: Dp = GlassTilePadding,
    blur: Boolean = glassBlurSupported(),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val white = V5Theme.colors.onGradient
    CompositionLocalProvider(LocalContentColor provides white) {
        Box(
            modifier
                .clip(shape)
                .cardClickable(shape, onClick, onClickLabel, contentDescription),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (blur) Modifier.graphicsLayer { renderEffect = glassBlurEffect() } else Modifier)
                    .background(white.copy(alpha = GLASS_FILL_ALPHA)),
            )
            Box(Modifier.matchParentSize().border(1.dp, white.copy(alpha = GLASS_BORDER_ALPHA), shape))
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
        }
    }
}

private fun glassBlurEffect() = BlurEffect(GLASS_BLUR_RADIUS, GLASS_BLUR_RADIUS, TileMode.Clamp)

@Preview(widthDp = 390, heightDp = 420)
@Composable
internal fun V5CardPreview() {
    V5PreviewSurface(gradient = false) {
        V5Card(Modifier.fillMaxWidth()) {
            Text("Active blocks", style = V5Theme.type.bodyL)
            Text("2 of 3 on", style = V5Theme.type.body, color = V5Theme.colors.textSecondary)
        }
        V5Card(
            Modifier.fillMaxWidth(),
            radius = 36.dp,
            containerColor = V5Theme.colors.surfaceRaised,
            onClick = {},
        ) {
            Text("Raised, tappable", style = V5Theme.type.bodyL)
            Text("Ripple stays inside the corners", style = V5Theme.type.body, color = V5Theme.colors.textSecondary)
        }
        GlowCard(V5Gradients.violet, Modifier.fillMaxWidth(), onClick = {}) {
            Text("Blocked", style = V5Theme.type.bodyL)
            Text("12 times today", style = V5Theme.type.body)
        }
    }
}

@Preview(widthDp = 390, heightDp = 520)
@Composable
internal fun GlowCardPreview() {
    V5PreviewSurface(gradient = true) {
        GlowCard(V5Gradients.violet, Modifier.fillMaxWidth()) {
            Text("Insights", style = V5Theme.type.bodyL)
            Text("2h 33m on screen", style = V5Theme.type.body)
        }
        GlowCard(V5Gradients.pinkCard, Modifier.fillMaxWidth(), radius = 28.dp) {
            Text("Maya", style = V5Theme.type.cardTitle)
            Text("Online", style = V5Theme.type.body)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTile(Modifier.fillMaxWidth()) {
                Text("Blocked", style = V5Theme.type.label)
                Text("12", style = V5Theme.type.rule)
            }
            GlassTile(Modifier.fillMaxWidth(), onClick = {}) {
                Text("Focus", style = V5Theme.type.label)
                Text("1h 20m", style = V5Theme.type.rule)
            }
        }
    }
}

/** The same tile with the blur switched off: what API 26–30 devices get. */
@Preview(widthDp = 390, heightDp = 200)
@Composable
internal fun GlassTileNoBlurPreview() {
    V5PreviewSurface(gradient = true) {
        GlassTile(Modifier.fillMaxWidth(), blur = false) {
            Text("Blocked", style = V5Theme.type.label)
            Text("12", style = V5Theme.type.rule)
        }
        GlassTile(Modifier.fillMaxWidth(), blur = true) {
            Text("Blocked", style = V5Theme.type.label)
            Text("12", style = V5Theme.type.rule)
        }
    }
}
