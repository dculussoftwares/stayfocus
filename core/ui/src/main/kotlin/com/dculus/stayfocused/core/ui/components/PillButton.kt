@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.art.Spinner
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

/** Look of a [PillButton] (V5_SPEC §4). */
enum class PillButtonVariant {
    /** Ink fill, white label. */
    Primary,

    /** White fill, ink label. */
    White,

    /** White .2 fill with a white .4 border, white label; for use on a gradient. */
    Glass,

    /** `track` fill, ink label. */
    Secondary,

    /** No container, 15/600 label (ink unless `textColor` is set), optional underline. */
    Text,
}

/** Visual height of a [PillButton]; the touch target is never below 48 dp. */
enum class PillButtonSize(
    val height: Dp,
    val textSize: TextUnit,
) {
    /** 62 dp, 17 sp: main call to action. */
    Large(62.dp, 17.sp),

    /** 60 dp, 16 sp: sheet and sign-in buttons. */
    Sheet(60.dp, 16.sp),

    /** 50 dp, 15 sp: text links and secondary actions. */
    Medium(50.dp, 15.sp),

    /** 46 dp, 14 sp: in-card actions. */
    Compact(46.dp, 14.sp),

    /** 44 dp, 14 sp: smallest. */
    Small(44.dp, 14.sp),
}

private const val DISABLED_ALPHA = 0.4f
private const val PRESSED_ALPHA = 0.85f
private const val GLASS_FILL_ALPHA = 0.2f
private const val GLASS_BORDER_ALPHA = 0.4f

private class PillColors(
    val content: Color,
    val container: Color?,
    val border: Color? = null,
)

@Composable
private fun pillColors(
    variant: PillButtonVariant,
    textColor: Color,
): PillColors {
    val c = StayFocusedTheme.colors
    return when (variant) {
        PillButtonVariant.Primary -> {
            PillColors(c.onGradient, c.ink)
        }

        PillButtonVariant.White -> {
            PillColors(c.ink, c.surface)
        }

        PillButtonVariant.Glass -> {
            PillColors(
                c.onGradient,
                c.onGradient.copy(alpha = GLASS_FILL_ALPHA),
                border = c.onGradient.copy(alpha = GLASS_BORDER_ALPHA),
            )
        }

        PillButtonVariant.Secondary -> {
            PillColors(c.ink, c.track)
        }

        PillButtonVariant.Text -> {
            PillColors(if (textColor == Color.Unspecified) c.ink else textColor, null)
        }
    }
}

/**
 * Full-pill button (radius = height / 2) that fills the width it is given.
 *
 * While [loading] it shows a spinner and [loadingLabel] (or [text]), ignores taps and announces "Loading".
 * [enabled] = false
 * dims it to .4. The pill is [PillButtonSize.height] tall; the clickable area is at least 48 dp.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillButtonVariant = PillButtonVariant.Primary,
    size: PillButtonSize = PillButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingLabel: String? = null,
    underline: Boolean = false,
    textColor: Color = Color.Unspecified,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = pillColors(variant, textColor)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val loadingDescription = stringResource(R.string.sf_loading)
    val shape = StayFocusedTheme.shapes.pill
    val label = if (loading) loadingLabel ?: text else text
    Box(
        modifier =
            modifier
                .sizeIn(minHeight = StayFocusedTheme.spacing.minTouchTarget)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) { if (loading) stateDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(size.height)
                    .alpha(
                        when {
                            !enabled -> DISABLED_ALPHA
                            pressed -> PRESSED_ALPHA
                            else -> 1f
                        },
                    ).clip(shape)
                    .then(
                        when {
                            colors.container != null -> Modifier.background(colors.container)
                            else -> Modifier
                        },
                    ).then(if (colors.border != null) Modifier.border(1.dp, colors.border, shape) else Modifier)
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                Spinner(color = colors.content)
            } else {
                leadingIcon?.invoke()
            }
            Text(
                text = label,
                style = pillTextStyle(variant, size, underline),
                color = colors.content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun pillTextStyle(
    variant: PillButtonVariant,
    size: PillButtonSize,
    underline: Boolean,
): TextStyle {
    val base = StayFocusedTheme.type.bodyL.copy(fontSize = size.textSize, lineHeight = TextUnit.Unspecified)
    return if (variant == PillButtonVariant.Text &&
        underline
    ) {
        base.copy(textDecoration = TextDecoration.Underline)
    } else {
        base
    }
}

@Preview(widthDp = 390, heightDp = 760)
@Composable
internal fun PillButtonLightPreview() {
    V5PreviewSurface(gradient = false) {
        val plus: @Composable () -> Unit = {
            Icon(
                V5Icons.plus,
                contentDescription = null,
                tint = StayFocusedTheme.colors.onGradient,
                modifier = Modifier.size(18.dp),
            )
        }
        PillButton("Continue", onClick = {})
        PillButton("Continue", onClick = {}, enabled = false)
        PillButton("Signing in", onClick = {}, loading = true)
        PillButton("Continue with Google", onClick = {}, variant = PillButtonVariant.White, size = PillButtonSize.Sheet)
        PillButton("Send reminder", onClick = {}, size = PillButtonSize.Compact)
        Column(
            Modifier.background(StayFocusedTheme.colors.surface, StayFocusedTheme.shapes.card).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton("Dismiss", onClick = {}, variant = PillButtonVariant.Secondary, size = PillButtonSize.Compact)
            PillButton(
                "Dismiss",
                onClick = {},
                variant = PillButtonVariant.Secondary,
                size = PillButtonSize.Small,
                enabled = false,
            )
        }
        PillButton("Take a break", onClick = {}, variant = PillButtonVariant.Primary, leadingIcon = plus)
        PillButton("Build my block", onClick = {
        }, variant = PillButtonVariant.Primary, loading = true, loadingLabel = "Building")
        PillButton("Not now", onClick = {}, variant = PillButtonVariant.Text, size = PillButtonSize.Medium)
    }
}

@Preview(widthDp = 390, heightDp = 480)
@Composable
internal fun PillButtonGradientPreview() {
    V5PreviewSurface(gradient = true) {
        PillButton("Get started", onClick = {}, variant = PillButtonVariant.White)
        PillButton("Keep going", onClick = {}, variant = PillButtonVariant.Glass, size = PillButtonSize.Sheet)
        PillButton(
            "Keep going",
            onClick = {},
            variant = PillButtonVariant.Glass,
            size = PillButtonSize.Sheet,
            enabled = false,
        )
        PillButton(
            "This is my child's phone",
            onClick = {},
            variant = PillButtonVariant.Text,
            size = PillButtonSize.Medium,
            underline = true,
            textColor = StayFocusedTheme.colors.onGradient,
        )
    }
}
