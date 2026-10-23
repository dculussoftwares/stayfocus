package com.dculus.stayfocused.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The v5 prototype's inline 24 px SVG icons as tintable [ImageVector]s (no icon font): stroke icons with round caps at
 * the widths the prototype draws (1.8 – 3), and the filled sparkles. Paths are copied verbatim from
 * `design/Stay Focused v5.dc.html`; its rounded-rect and circle elements are written as the equivalent path.
 *
 * The vectors are drawn in black; tint them with `Icon(tint = …)` (the prototype uses ink or white). Use them at
 * 18 – 24 dp.
 */
object V5Icons {
    private const val VIEWPORT = 24f
    private const val STROKE_DEFAULT = 2f
    private const val STROKE_LIGHT = 1.8f
    private const val STROKE_MEDIUM = 2.2f
    private const val STROKE_BOLD = 2.6f
    private const val STROKE_HEAVY = 3f

    private const val PLUS = "M12 5v14M5 12h14"
    private const val CHECK = "M5 12l5 5 9-10"
    private const val SPARKLE = "M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8z"
    private const val SPARKLE_SMALL = "M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"
    private const val LOCK_BODY = "M8 11h8a3 3 0 0 1 3 3v4a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-4a3 3 0 0 1 3-3z"

    private fun stroke(
        name: String,
        width: Float,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(name, VIEWPORT.dp, VIEWPORT.dp, VIEWPORT, VIEWPORT)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round,
                    )
                }
            }.build()

    private fun filled(
        name: String,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(name, VIEWPORT.dp, VIEWPORT.dp, VIEWPORT, VIEWPORT)
            .apply {
                paths.forEach { d ->
                    addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
                }
            }.build()

    /** Arrow pointing left (back buttons). */
    val back: ImageVector by lazy { stroke("back", STROKE_DEFAULT, "M19 12H5M11 6l-6 6 6 6") }

    /** Arrow pointing right (continue). */
    val arrow: ImageVector by lazy { stroke("arrow", STROKE_DEFAULT, "M5 12h14M13 6l6 6-6 6") }

    val close: ImageVector by lazy { stroke("close", STROKE_DEFAULT, "M6 6l12 12M18 6L6 18") }

    /** Plus at the common 2 px weight. */
    val plus: ImageVector by lazy { stroke("plus", STROKE_DEFAULT, PLUS) }

    /** Plus at 2.2 px (the sheet's add button). */
    val plusMedium: ImageVector by lazy { stroke("plusMedium", STROKE_MEDIUM, PLUS) }

    /** Plus at 2.6 px (on gradients and filled buttons). */
    val plusBold: ImageVector by lazy { stroke("plusBold", STROKE_BOLD, PLUS) }

    val bell: ImageVector by lazy {
        stroke("bell", STROKE_LIGHT, "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 21h4")
    }

    /** Circle with a slash: "Blocked". */
    val block: ImageVector by lazy {
        stroke("block", STROKE_DEFAULT, "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M5.6 5.6l12.8 12.8")
    }

    val lock: ImageVector by lazy { stroke("lock", STROKE_LIGHT, LOCK_BODY, "M8 11V8a4 4 0 0 1 8 0v3") }

    val timer: ImageVector by lazy {
        stroke("timer", STROKE_LIGHT, "M12 5a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M12 9v4l3 2M9 2h6")
    }

    /** Tick at 2.6 px. */
    val check: ImageVector by lazy { stroke("check", STROKE_BOLD, CHECK) }

    /** Tick at 3 px (selected rows). */
    val checkBold: ImageVector by lazy { stroke("checkBold", STROKE_HEAVY, CHECK) }

    /** Four-point sparkle, filled. */
    val sparkle: ImageVector by lazy { filled("sparkle", SPARKLE) }

    /** The sparkle with a small second one at its lower right (the AI draft card). */
    val sparkleTwin: ImageVector by lazy { filled("sparkleTwin", SPARKLE, SPARKLE_SMALL) }
}
