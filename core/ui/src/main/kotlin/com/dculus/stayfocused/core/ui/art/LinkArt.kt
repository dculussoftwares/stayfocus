@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.art

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.rememberInfiniteMotion

private const val ART_WIDTH = 230f
private const val ART_HEIGHT = 92f
private const val DASH_MILLIS = 1100
private const val BLINK_HALF_MILLIS = 800
private const val DASH_TRAVEL = 16f
private const val BLINK_LOW = 0.25f

private val PhoneFill = Color.White.copy(alpha = 0.18f)
private const val DIM_DOT_ALPHA = 0.4f

/** Left edge of the two phones in the 230 × 92 art space (`linkArt`). */
private const val PHONE_LEFT = 18f
private const val PHONE_RIGHT = 176f

/** Whether the phone's dot at [row], [column] is dimmed: the right phone shows a checkerboard of faint dots. */
internal fun linkDotDimmed(
    rightPhone: Boolean,
    row: Int,
    column: Int,
): Boolean = rightPhone && (row + column) % 2 == 1

/**
 * "Linking two phones" art on the family card and link flow (`linkArt`): two phone outlines with 3 × 3 dots, a dotted
 * arc between them whose dots travel (1.1 s) and a lime dot that blinks (1.6 s). 230 × 92 dp, drawn in white and lime.
 *
 * Both animations stop when animations are off (and in screenshots), leaving a static frame. Decorative: hidden from
 * TalkBack.
 */
@Composable
fun LinkArt(
    modifier: Modifier = Modifier,
    dotColor: Color = StayFocusedTheme.colors.lime,
) {
    val dashProgress by rememberInfiniteMotion(from = 0f, to = 1f, durationMillis = DASH_MILLIS)
    val blink by rememberInfiniteMotion(
        from = 1f,
        to = BLINK_LOW,
        durationMillis = BLINK_HALF_MILLIS,
        easing = EaseInOutCss,
        repeatMode = RepeatMode.Reverse,
        staticValue = 1f,
    )
    Canvas(modifier.clearAndSetSemantics { }.wrapContentSize().size(ART_WIDTH.dp, ART_HEIGHT.dp)) {
        val unit = size.width / ART_WIDTH
        drawPhone(PHONE_LEFT, rightPhone = false, unit)
        drawPhone(PHONE_RIGHT, rightPhone = true, unit)
        drawDashedArc(unit, phase = -DASH_TRAVEL * dashProgress * unit)
        drawCircle(dotColor.copy(alpha = blink), radius = 5f * unit, center = Offset(115f * unit, 23f * unit))
    }
}

private fun DrawScope.drawPhone(
    x: Float,
    rightPhone: Boolean,
    unit: Float,
) {
    val topLeft = Offset(x * unit, 22f * unit)
    val phone = Size(36f * unit, 60f * unit)
    val corner = CornerRadius(10f * unit)
    drawRoundRect(PhoneFill, topLeft, phone, corner)
    drawRoundRect(Color.White, topLeft, phone, corner, style = Stroke(width = 1.5f * unit))
    for (row in 0..2) {
        for (column in 0..2) {
            val alpha = if (linkDotDimmed(rightPhone, row, column)) DIM_DOT_ALPHA else 1f
            val centre = Offset((x + 11f + column * 7f) * unit, (44f + row * 7f) * unit)
            drawCircle(Color.White.copy(alpha = alpha), radius = 1.6f * unit, center = centre)
        }
    }
}

private fun DrawScope.drawDashedArc(
    unit: Float,
    phase: Float,
) {
    val arc =
        Path().apply {
            moveTo(58f * unit, 52f * unit)
            quadraticTo(115f * unit, -6f * unit, 172f * unit, 52f * unit)
        }
    drawPath(
        arc,
        Color.White,
        style =
            Stroke(
                width = 2.2f * unit,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f * unit, 8f * unit), phase),
            ),
    )
}

@Preview(widthDp = 300, heightDp = 160)
@Composable
internal fun LinkArtPreview() {
    StayFocusedTheme {
        Box(Modifier.background(V5Gradients.pinkLink.adaptive).padding(35.dp)) { LinkArt() }
    }
}
