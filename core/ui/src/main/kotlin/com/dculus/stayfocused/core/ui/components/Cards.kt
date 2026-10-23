@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

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
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradient
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.glow

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
private const val GLASS_BLUR_RADIUS = 16

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
    containerColor: Color = StayFocusedTheme.colors.surface,
    padding: Dp = DefaultCardPadding,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val shadow = StayFocusedTheme.colors.ink.copy(alpha = CARD_SHADOW_ALPHA)
    CompositionLocalProvider(LocalContentColor provides StayFocusedTheme.colors.ink) {
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
    CompositionLocalProvider(LocalContentColor provides StayFocusedTheme.colors.onGradient) {
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
    val white = StayFocusedTheme.colors.onGradient
    CompositionLocalProvider(LocalContentColor provides white) {
        Box(
            modifier
                .clip(shape)
                .cardClickable(shape, onClick, onClickLabel, contentDescription),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (blur) Modifier.glassBlur() else Modifier)
                    .background(white.copy(alpha = GLASS_FILL_ALPHA)),
            )
            Box(Modifier.matchParentSize().border(1.dp, white.copy(alpha = GLASS_BORDER_ALPHA), shape))
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
        }
    }
}

/** 16 dp blur (the effect takes pixels, so it is converted with the screen density). */
private fun Modifier.glassBlur(): Modifier =
    graphicsLayer {
        val radius = GLASS_BLUR_RADIUS.dp.toPx()
        renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
    }

private val PreviewBodyColor: Color
    @Composable get() = StayFocusedTheme.colors.textSecondary

@Preview(widthDp = 390, heightDp = 640)
@Composable
internal fun V5CardPreview() {
    V5PreviewSurface(gradient = false) {
        V5Card(Modifier.fillMaxWidth()) {
            Text("Active blocks", style = StayFocusedTheme.type.bodyL)
            Text("2 of 3 on", style = StayFocusedTheme.type.body, color = PreviewBodyColor)
        }
        V5Card(
            Modifier.fillMaxWidth(),
            radius = 36.dp,
            containerColor = StayFocusedTheme.colors.surfaceRaised,
            onClick = {},
        ) {
            Text("Raised, tappable", style = StayFocusedTheme.type.bodyL)
            Text("Ripple stays inside the corners", style = StayFocusedTheme.type.body, color = PreviewBodyColor)
        }
        GlowCard(V5Gradients.violet, Modifier.fillMaxWidth(), onClick = {}) {
            Text("Blocked", style = StayFocusedTheme.type.bodyL)
            Text("12 times today", style = StayFocusedTheme.type.body)
        }
        GlassTile(Modifier.fillMaxWidth()) {
            Text("Glass on white", style = StayFocusedTheme.type.label, color = StayFocusedTheme.colors.ink)
            Text("barely there", style = StayFocusedTheme.type.body, color = PreviewBodyColor)
        }
    }
}

@Preview(widthDp = 390, heightDp = 640)
@Composable
internal fun GlowCardPreview() {
    V5PreviewSurface(gradient = true) {
        V5Card(Modifier.fillMaxWidth()) {
            Text("White card on gradient", style = StayFocusedTheme.type.bodyL)
            Text("2 of 3 on", style = StayFocusedTheme.type.body, color = PreviewBodyColor)
        }
        GlowCard(V5Gradients.violet, Modifier.fillMaxWidth()) {
            Text("Insights", style = StayFocusedTheme.type.bodyL)
            Text("2h 33m on screen", style = StayFocusedTheme.type.body)
        }
        GlowCard(V5Gradients.pinkCard, Modifier.fillMaxWidth(), radius = 28.dp) {
            Text("Maya", style = StayFocusedTheme.type.cardTitle)
            Text("Online", style = StayFocusedTheme.type.body)
        }
        GlassTile(Modifier.fillMaxWidth()) {
            Text("Blocked", style = StayFocusedTheme.type.label)
            Text("12", style = StayFocusedTheme.type.rule)
        }
        GlassTile(Modifier.fillMaxWidth(), onClick = {}) {
            Text("Focus", style = StayFocusedTheme.type.label)
            Text("1h 20m", style = StayFocusedTheme.type.rule)
        }
    }
}

/**
 * A glass tile with the automatic blur choice (blurred on API 31+, plain below) above one with the blur forced off.
 * Rendered at API 26 and API 34 so both paths of [glassBlurSupported] have a baseline.
 */
@Preview(widthDp = 390, heightDp = 200)
@Composable
internal fun GlassTilePreview() {
    V5PreviewSurface(gradient = true) {
        GlassTile(Modifier.fillMaxWidth()) {
            Text("Automatic", style = StayFocusedTheme.type.label)
            Text("12", style = StayFocusedTheme.type.rule)
        }
        GlassTile(Modifier.fillMaxWidth(), blur = false) {
            Text("No blur", style = StayFocusedTheme.type.label)
            Text("12", style = StayFocusedTheme.type.rule)
        }
    }
}
