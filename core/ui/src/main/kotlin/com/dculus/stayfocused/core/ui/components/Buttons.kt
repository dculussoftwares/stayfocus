@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.theme.StayFocusedColors
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

private const val DISABLED_ALPHA = 0.4f

private class ButtonStyle(
    val container: Color,
    val content: Color,
    val border: Color? = null,
)

/** Shared implementation: h56, r16, optional leading icon, loading state (spinner + mono label). */
@Composable
private fun SfButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    loadingLabel: String?,
    leadingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    style: (colors: StayFocusedColors, pressed: Boolean) -> ButtonStyle,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val s = style(StayFocusedTheme.colors, pressed)
    val shape = StayFocusedTheme.shapes.button
    // Lime buttons get an off-white ring; every other variant gets the lime ring.
    val colors = StayFocusedTheme.colors
    val focusRing =
        if (s.container == colors.accent ||
            s.container == colors.accentHover
        ) {
            colors.text
        } else {
            colors.accent
        }
    val loadingDescription = stringResource(R.string.sf_loading)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(shape)
                .background(s.container)
                .then(
                    when {
                        focused -> Modifier.border(2.dp, focusRing, shape)
                        s.border != null -> Modifier.border(1.dp, s.border, shape)
                        else -> Modifier
                    },
                ).clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { if (loading) contentDescription = loadingDescription }
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = s.content, strokeWidth = 2.dp)
            Text((loadingLabel ?: text).uppercase(), style = StayFocusedTheme.type.label, color = s.content)
        } else {
            leadingIcon?.invoke()
            Text(
                text = text,
                style = StayFocusedTheme.type.bodyL.copy(fontWeight = FontWeight.ExtraBold),
                color = s.content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Lime call-to-action: h56, r16, ink label; pressed = `accentHover`. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) = SfButton(text, onClick, enabled, loading, loadingLabel, leadingIcon, modifier) { c, pressed ->
    ButtonStyle(if (pressed) c.accentHover else c.accent, c.onAccent)
}

/** Off-white button (the "+ New block" style). */
@Composable
fun WhiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) = SfButton(text, onClick, enabled, loading, loadingLabel, leadingIcon, modifier) { c, pressed ->
    ButtonStyle(if (pressed) c.text.copy(alpha = 0.85f) else c.text, c.onAccent)
}

/** Panel-coloured button with a hairline border. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) = SfButton(text, onClick, enabled, loading, loadingLabel, leadingIcon, modifier) { c, pressed ->
    ButtonStyle(if (pressed) c.track else c.panel, c.text, c.hairline14)
}

/** Transparent button; only the label shows. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) = SfButton(text, onClick, enabled, loading, loadingLabel, leadingIcon, modifier) { c, pressed ->
    ButtonStyle(if (pressed) c.hairline08 else c.background.copy(alpha = 0f), c.secondary)
}

/** The small ink tile (r8, 24 dp) used as a leading icon, e.g. the lime "+" in [WhiteButton]. */
@Composable
fun IconTile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(StayFocusedTheme.colors.onAccent),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Preview(widthDp = 360, heightDp = 560)
@Composable
internal fun ButtonsPreview() {
    StayFocusedTheme {
        Column(
            modifier = Modifier.background(StayFocusedTheme.colors.background).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val plus: @Composable () -> Unit = {
                IconTile { Text("+", color = StayFocusedTheme.colors.accent, style = StayFocusedTheme.type.bodyL) }
            }
            PrimaryButton("Continue", onClick = {})
            PrimaryButton("Continue", onClick = {}, enabled = false)
            PrimaryButton("Continue", onClick = {}, loading = true, loadingLabel = "Signing in")
            WhiteButton("New block", onClick = {}, leadingIcon = plus)
            OutlineButton("Continue with Google", onClick = {})
            GhostButton("Skip", onClick = {})
        }
    }
}
