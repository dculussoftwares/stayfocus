@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.art

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.rememberInfiniteMotion

private const val BRACKET_SIZE = 44f
private const val BRACKET_STROKE = 3f
private const val BRACKET_RADIUS = 22f
private const val LINE_INSET = 16f
private const val LINE_HEIGHT = 2f
private const val GLOW_BLUR = 15f
private const val SCAN_TOP = 0.14f
private const val SCAN_BOTTOM = 0.84f
private const val SCAN_HALF_MILLIS = 1200

/** Where the line rests in the static frame (screenshots, "Remove animations"), as in `19-scan-code.png`. */
internal const val SCAN_STATIC_FRACTION = 0.35f

/**
 * The scanner overlay (prototype lines 391–395 and `scanLine`): a 260 dp square with four white corner brackets
 * (44 dp, 3 dp stroke, 22 dp outer radius) and a 2 dp scan line with a pink glow that moves between 14 % and 84 % of
 * the height and back (2.4 s, ease-in-out). The line rests at [SCAN_STATIC_FRACTION] when animations are off.
 * Decorative: hidden from TalkBack (the screen's "Looking for a code…" says what is happening).
 */
@Composable
fun ScanFrame(
    modifier: Modifier = Modifier,
    bracketColor: Color = Color.White,
) {
    val colors = StayFocusedTheme.colors
    val position by rememberInfiniteMotion(
        from = SCAN_TOP,
        to = SCAN_BOTTOM,
        durationMillis = SCAN_HALF_MILLIS,
        easing = EaseInOutCss,
        repeatMode = RepeatMode.Reverse,
        staticValue = SCAN_STATIC_FRACTION,
    )
    Canvas(modifier.clearAndSetSemantics { }.wrapContentSize().size(260.dp)) {
        val dp = density
        drawBrackets(bracketColor, dp)
        drawScanLine(position, dp, colors.pink, colors.pinkScan)
    }
}

/** One top-left bracket: a 3 dp stroke along the left and top edges joined by a 22 dp outer-radius corner. */
private fun bracketPath(dp: Float): Path {
    val half = BRACKET_STROKE / 2f
    val radius = BRACKET_RADIUS - half
    return Path().apply {
        moveTo(half * dp, BRACKET_SIZE * dp)
        lineTo(half * dp, BRACKET_RADIUS * dp)
        arcTo(
            Rect(half * dp, half * dp, (half + 2 * radius) * dp, (half + 2 * radius) * dp),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false,
        )
        lineTo(BRACKET_SIZE * dp, half * dp)
    }
}

private fun DrawScope.drawBrackets(
    color: Color,
    dp: Float,
) {
    val path = bracketPath(dp)
    val stroke = Stroke(width = BRACKET_STROKE * dp)
    val centre = Offset(size.width / 2f, size.height / 2f)
    for (flipX in listOf(1f, -1f)) {
        for (flipY in listOf(1f, -1f)) {
            scale(flipX, flipY, pivot = centre) { drawPath(path, color, style = stroke) }
        }
    }
}

private fun DrawScope.drawScanLine(
    fraction: Float,
    dp: Float,
    glow: Color,
    core: Color,
) {
    val left = LINE_INSET * dp
    val width = size.width - 2 * left
    val top = fraction * size.height
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = glow.toArgb()
                    maskFilter = BlurMaskFilter(GLOW_BLUR * dp, BlurMaskFilter.Blur.NORMAL)
                }
            }
        canvas.drawRect(left, top, left + width, top + LINE_HEIGHT * dp, paint)
    }
    drawRect(
        brush =
            Brush.horizontalGradient(
                listOf(Color.Transparent, core, Color.Transparent),
                startX = left,
                endX = left + width,
            ),
        topLeft = Offset(left, top),
        size = Size(width, LINE_HEIGHT * dp),
    )
}

@Preview(widthDp = 390, heightDp = 360)
@Composable
internal fun ScanFramePreview() {
    StayFocusedTheme {
        Box(Modifier.background(V5Gradients.scan.adaptive).padding(65.dp)) { ScanFrame() }
    }
}
