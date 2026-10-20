@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.R
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme

private const val PINK_PILL_ALPHA = 0.22f

/** Shared pill: [height] tall, radius height / 2, 12 dp side padding, [content] centred in a row. */
@Composable
private fun TagPill(
    height: Dp,
    background: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .background(background)
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun WhiteDot() {
    Box(Modifier.size(6.dp).background(V5Theme.colors.onGradient, CircleShape))
}

@Composable
private fun TagText(
    text: String,
    color: Color,
    style: TextStyle,
) {
    Text(text = text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Red pill with a white dot naming the phone a block applies to (30 dp, 13/600). With [onClick] it is a button
 * (e.g. "tap the red tag to switch phone") with a 48 dp touch target.
 */
@Composable
fun TargetTag(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickable =
        if (onClick != null) {
            Modifier
                .sizeIn(minWidth = V5Theme.spacing.minTouchTarget, minHeight = V5Theme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
        } else {
            Modifier
        }
    Box(modifier = modifier.then(clickable), contentAlignment = Alignment.CenterStart) {
        TagPill(height = 30.dp, background = V5Theme.colors.red) {
            WhiteDot()
            TagText(name, V5Theme.colors.onGradient, V5Theme.type.label.copy(fontSize = 13.sp))
        }
    }
}

/** What a [StatusTag] says. */
enum class StatusTagKind {
    /** Red, white dot: a child's protection is off. */
    ProtectionOff,

    /** Orange: a child asked for something. */
    Request,

    /** Orange with a sparkle: the block was drafted by AI. */
    AiDraft,
}

/** Status pill, 12/700 white on red or orange: 28 dp ([StatusTagKind.AiDraft]: 26 dp). Informational, not clickable. */
@Composable
fun StatusTag(
    kind: StatusTagKind,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
    val style = V5Theme.type.tag
    when (kind) {
        StatusTagKind.ProtectionOff -> {
            TagPill(28.dp, c.red, modifier) {
                WhiteDot()
                TagText(stringResource(R.string.v5_tag_protection_off), c.onGradient, style)
            }
        }

        StatusTagKind.Request -> {
            TagPill(28.dp, c.orange, modifier) {
                TagText(stringResource(R.string.v5_tag_request), c.onGradient, style)
            }
        }

        StatusTagKind.AiDraft -> {
            TagPill(26.dp, c.orange, modifier, gap = 5.dp) {
                Icon(
                    ControlIcons.sparkle,
                    contentDescription = null,
                    tint = c.onGradient,
                    modifier = Modifier.size(11.dp),
                )
                TagText(stringResource(R.string.v5_tag_ai_draft), c.onGradient, style)
            }
        }
    }
}

/**
 * "Online" / "Offline" pill (30 dp, 13/600) with a dot that pulses while online. [onPink] is the white .22 variant for
 * the pink child card; otherwise it is green on `onlineBackground`. Offline is a quiet `track` pill.
 */
@Composable
fun OnlinePill(
    modifier: Modifier = Modifier,
    online: Boolean = true,
    onPink: Boolean = false,
) {
    val c = V5Theme.colors
    val background =
        when {
            onPink -> c.onGradient.copy(alpha = PINK_PILL_ALPHA)
            online -> c.onlineBackground
            else -> c.track
        }
    val content =
        when {
            onPink -> c.onGradient
            online -> c.onlineText
            else -> c.textSecondary
        }
    val dot =
        if (online) {
            c.online
        } else if (onPink) {
            c.onGradient.copy(alpha = 0.6f)
        } else {
            c.hairline
        }
    TagPill(30.dp, background, modifier, gap = 7.dp) {
        V5PulseDot(dot, pulse = online)
        TagText(
            stringResource(if (online) R.string.v5_online else R.string.v5_offline),
            content,
            V5Theme.type.label.copy(fontSize = 13.sp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 460)
@Composable
internal fun TagsPreview() {
    V5PreviewSurface(gradient = false) {
        TargetTag("Aarav's phone")
        TargetTag("Aarav's phone", onClick = {})
        StatusTag(StatusTagKind.ProtectionOff)
        StatusTag(StatusTagKind.Request)
        StatusTag(StatusTagKind.AiDraft)
        OnlinePill()
        OnlinePill(online = false)
        Box(Modifier.background(V5Gradients.pinkCard.adaptive).padding(12.dp)) {
            OnlinePill(onPink = true)
        }
    }
}
