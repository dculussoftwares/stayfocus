package com.dculus.stayfocused.core.ui.v5.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few prototype SVGs these controls need (stroke icons, tintable). The full icon set is M11-08; these are
 * internal so they don't collide with it.
 */
internal object ControlIcons {
    private const val STROKE = 2f

    private fun stroke(
        name: String,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                paths.forEach { d ->
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = STROKE,
                        strokeLineCap = StrokeCap.Round,
                    )
                }
            }.build()

    val back: ImageVector by lazy { stroke("back", "M19 12H5M11 6l-6 6 6 6") }
    val plus: ImageVector by lazy { stroke("plus", "M12 5v14M5 12h14") }
    val bell: ImageVector by lazy { stroke("bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 21h4") }

    /** Four-point sparkle, filled (the "AI draft" tag). */
    val sparkle: ImageVector by lazy {
        ImageVector
            .Builder("sparkle", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData =
                    PathParser()
                        .parsePathString(
                            "M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8z",
                        ).toNodes(),
                fill = SolidColor(Color.Black),
            ).build()
    }
}
