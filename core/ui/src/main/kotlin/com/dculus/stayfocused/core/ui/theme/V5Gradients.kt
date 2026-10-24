// Gradient stops and radii are literal v5 spec values (docs/design/V5_SPEC.md §3).
@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.theme

import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

@Immutable
data class V5GradientStop(
    val position: Float,
    val color: Color,
)

/**
 * One CSS `radial-gradient(rx ry at cx cy, stops)`: all values are fractions (0..1) of the drawn box, so
 * `80% 70% at 25% 20%` is `rx = .8, ry = .7, cx = .25, cy = .2`. The shape is an ellipse.
 */
@Immutable
data class V5RadialLayer(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val stops: List<V5GradientStop>,
)

/** What a [V5Gradient] paints under its radial layers. */
sealed interface V5GradientBase {
    @Immutable
    data class Solid(
        val color: Color,
    ) : V5GradientBase
}

/**
 * A CSS stack of radial layers over a base. [layers] follow CSS order: the first one is drawn on top.
 * Supported depth: two layers over a solid base.
 */
@Immutable
class V5Gradient(
    val base: V5GradientBase,
    val layers: List<V5RadialLayer> = emptyList(),
) {
    init {
        require(layers.size <= 2) { "At most 2 radial layers over the solid base" }
    }

    /** Brush that follows whatever size it is drawn at. */
    val adaptive: Brush =
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shaderFor(size)
        }

    /** Brush fixed to [size] (the size the gradient will be drawn at). */
    fun brush(size: Size): Brush = ShaderBrush(shaderFor(size))

    private fun shaderFor(size: Size): Shader {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        // The solid base is folded into the bottom layer, so two layers need a single ComposeShader (no nesting).
        val solid = (base as V5GradientBase.Solid).color
        val bottomLayer = layers.lastOrNull()
        val bottom: Shader =
            bottomLayer?.toShader(w, h, over = solid)
                ?: linear(h, listOf(V5GradientStop(0f, solid), V5GradientStop(1f, solid)))
        val above = if (bottomLayer != null) layers.dropLast(1) else layers
        return above.foldRight(bottom) { layer, acc ->
            ComposeShader(acc, layer.toShader(w, h, over = null), PorterDuff.Mode.SRC_OVER)
        }
    }
}

private fun linear(
    height: Float,
    stops: List<V5GradientStop>,
): Shader =
    LinearGradient(
        0f,
        0f,
        0f,
        height,
        IntArray(stops.size) { stops[it].color.toArgb() },
        FloatArray(stops.size) { stops[it].position },
        Shader.TileMode.CLAMP,
    )

private fun V5RadialLayer.toShader(
    width: Float,
    height: Float,
    over: Color?,
): Shader {
    val centerX = cx * width
    val centerY = cy * height
    val radiusX = (rx * width).coerceAtLeast(1f)
    val radiusY = (ry * height).coerceAtLeast(1f)
    val shader =
        RadialGradient(
            centerX,
            centerY,
            radiusX,
            IntArray(stops.size) {
                (
                    over?.let { base ->
                        stops[it].color.compositeOver(base)
                    } ?: stops[it].color
                ).toArgb()
            },
            FloatArray(stops.size) { stops[it].position },
            Shader.TileMode.CLAMP,
        )
    shader.setLocalMatrix(Matrix().apply { setScale(1f, radiusY / radiusX, centerX, centerY) })
    return shader
}

private fun fade(
    color: Color,
    end: Float,
) = listOf(V5GradientStop(0f, color), V5GradientStop(end, color.copy(alpha = 0f)))

private fun glow(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    color: Color,
    end: Float = 0.7f,
) = V5RadialLayer(cx, cy, rx, ry, fade(color, end))

private fun solid(
    color: Color,
    vararg layers: V5RadialLayer,
) = V5Gradient(V5GradientBase.Solid(color), layers.toList())

/** The named gradients of V5_SPEC §3, built from [V5Colors] defaults. Use `Modifier.glow` to draw one with grain. */
object V5Gradients {
    private val c = V5Colors()

    /**
     * v6 `BrandGradient`: `radial-gradient(80% 70% at 75% 15%, #9CCB4A 0%, transparent 70%), #2F9A3C`. The only
     * gradient on cards; text on it is always white.
     */
    val brand = solid(c.green, glow(0.75f, 0.15f, 0.8f, 0.7f, c.greenGlow))

    val welcome =
        solid(
            c.green,
            glow(0.8f, 0.75f, 0.7f, 0.5f, c.greenLight),
            glow(0.1f, 0.2f, 0.9f, 0.7f, c.greenFresh),
        )
    val blocked =
        solid(
            c.green,
            glow(0.5f, 0.4f, 0.6f, 0.4f, c.greenFresh, end = 0.75f),
            glow(0.5f, 1f, 0.8f, 0.5f, c.greenGlow),
        )

    /** Kids "Show this to your parent". */
    val kidsQr = solid(c.green, glow(0.5f, 0.45f, 0.7f, 0.45f, c.greenGlow, end = 0.75f))

    val scan = solid(c.scanBackground, glow(0.5f, 0.48f, 0.6f, 0.4f, c.green.copy(alpha = 0.4f)))

    val all: Map<String, V5Gradient> =
        mapOf(
            "brand" to brand,
            "welcome" to welcome,
            "blocked" to blocked,
            "kidsQr" to kidsQr,
            "scan" to scan,
        )
}
