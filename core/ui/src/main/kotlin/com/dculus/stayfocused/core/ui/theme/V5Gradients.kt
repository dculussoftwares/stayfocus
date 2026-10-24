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

    /** CSS `linear-gradient(180deg, ...)`, top to bottom. */
    @Immutable
    data class Vertical(
        val stops: List<V5GradientStop>,
    ) : V5GradientBase
}

/**
 * A CSS stack of radial layers over a base. [layers] follow CSS order: the first one is drawn on top.
 * Supported depth: two layers over a solid base, one over a vertical gradient.
 */
@Immutable
class V5Gradient(
    val base: V5GradientBase,
    val layers: List<V5RadialLayer> = emptyList(),
) {
    init {
        val maxLayers = if (base is V5GradientBase.Solid) 2 else 1
        require(layers.size <= maxLayers) { "At most $maxLayers radial layers over this base" }
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
        // A solid base is folded into the bottom layer, so two layers need a single ComposeShader (no nesting).
        val solid = (base as? V5GradientBase.Solid)?.color
        val bottomLayer = if (solid != null) layers.lastOrNull() else null
        val bottom: Shader =
            when {
                bottomLayer != null -> bottomLayer.toShader(w, h, over = solid)
                solid != null -> linear(h, listOf(V5GradientStop(0f, solid), V5GradientStop(1f, solid)))
                else -> linear(h, (base as V5GradientBase.Vertical).stops)
            }
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

    /** Time-limit type tile and the wizard step-3 card. */
    val orange = solid(c.orange, glow(0.25f, 0.2f, 0.8f, 0.7f, c.orangeGlow))

    /** Home "Unlocks" tile. */
    val orangeCard = solid(c.orange, glow(0.85f, 0.2f, 0.6f, 0.7f, c.orangeGlow))

    /** Home "Focus" tile. */
    val orangeTile = solid(c.orange, glow(0.3f, 0.2f, 0.7f, 0.6f, c.orangeGlow))
    val orangeButton = solid(c.orange, glow(0.2f, 0f, 0.7f, 1.2f, c.orangeGlow))
    val breakTile =
        V5Gradient(
            V5GradientBase.Solid(c.orange),
            listOf(
                V5RadialLayer(
                    0.5f,
                    0.6f,
                    0.5f,
                    0.42f,
                    listOf(
                        V5GradientStop(0f, c.orange),
                        V5GradientStop(0.45f, c.orangeGlowDeep),
                        V5GradientStop(1f, c.orangeSoft),
                    ),
                ),
            ),
        )
    val violet = solid(c.violet, glow(0.75f, 0.15f, 0.8f, 0.7f, c.violetGlow))
    val pinkCard =
        solid(
            c.pink,
            V5RadialLayer(
                0.9f,
                0.4f,
                0.7f,
                0.9f,
                listOf(
                    V5GradientStop(0f, c.pink),
                    V5GradientStop(0.55f, c.pinkMid),
                    V5GradientStop(1f, c.pinkSoft),
                ),
            ),
        )
    val pinkLink =
        solid(
            c.pink,
            V5RadialLayer(
                0.5f,
                0.58f,
                0.5f,
                0.45f,
                listOf(
                    V5GradientStop(0f, c.pink),
                    V5GradientStop(0.5f, c.pinkGlow),
                    V5GradientStop(1f, c.pinkRose),
                ),
            ),
        )
    val pinkHero =
        V5Gradient(
            V5GradientBase.Vertical(
                listOf(
                    V5GradientStop(0f, c.pinkTint),
                    V5GradientStop(0.6f, c.pink),
                    V5GradientStop(1f, c.pinkDeep),
                ),
            ),
            listOf(glow(0.5f, 0f, 0.7f, 0.5f, Color.White.copy(alpha = 0.3f))),
        )
    val kidsQr = solid(c.green, glow(0.5f, 0.45f, 0.7f, 0.45f, c.greenGlow, end = 0.75f))
    val scan = solid(c.scanBackground, glow(0.5f, 0.48f, 0.6f, 0.4f, c.green.copy(alpha = 0.4f)))

    /** Wizard type tiles (`TYPES[].bg`). The time-limit tile is [orange]. */
    val typeCycle = solid(c.green, glow(0.75f, 0.15f, 0.8f, 0.7f, c.greenGlow))
    val typeSchedule = solid(c.pink, glow(0.25f, 0.15f, 0.8f, 0.7f, c.pinkGlowLight))
    val typeNow = solid(c.ink, glow(0.75f, 0.15f, 0.8f, 0.7f, c.inkGlow))

    val all: Map<String, V5Gradient> =
        mapOf(
            "brand" to brand,
            "welcome" to welcome,
            "blocked" to blocked,
            "orange" to orange,
            "orangeCard" to orangeCard,
            "orangeTile" to orangeTile,
            "orangeButton" to orangeButton,
            "breakTile" to breakTile,
            "violet" to violet,
            "pinkCard" to pinkCard,
            "pinkLink" to pinkLink,
            "pinkHero" to pinkHero,
            "kidsQr" to kidsQr,
            "scan" to scan,
            "typeCycle" to typeCycle,
            "typeSchedule" to typeSchedule,
            "typeNow" to typeNow,
        )
}
