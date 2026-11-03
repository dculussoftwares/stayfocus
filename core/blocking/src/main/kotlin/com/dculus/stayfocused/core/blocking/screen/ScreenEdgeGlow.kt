package com.dculus.stayfocused.core.blocking.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View

/** Brand colours of a warning: the screen-edge glow and the halo behind the chip. */
internal class WarningPalette(
    /** Glow colours at the top-left, top-right, bottom-left and bottom-right corners. */
    val corners: IntArray,
    /** The thin wash along each edge. */
    val edge: Int,
    /** The halo that pulses behind the card. */
    val halo: Int,
) {
    companion object {
        private val GREEN = Color.rgb(0x2F, 0x9A, 0x3C)
        private val GREEN_GLOW = Color.rgb(0x9C, 0xCB, 0x4A)
        private val LIME = Color.rgb(0xD8, 0xF2, 0x3C)

        // v5 green ("Use, then lock": #9CCB4A glow on #2F9A3C), lifted with the v5 lime (#D8F23C) for urgency.
        val URGENT =
            WarningPalette(
                corners = intArrayOf(LIME, GREEN_GLOW, GREEN_GLOW, LIME),
                edge = GREEN_GLOW,
                halo = LIME,
            )

        // v5 green: `radial(80% 70% at 75% 15%, #9CCB4A)` on `#2F9A3C`.
        val CALM =
            WarningPalette(
                corners = intArrayOf(GREEN_GLOW, GREEN, GREEN, GREEN_GLOW),
                edge = GREEN,
                halo = GREEN_GLOW,
            )
    }
}

/**
 * A colour glow along the edges and corners of the whole screen: a bloom in each corner plus a thin wash along each
 * edge, all fading to transparent towards the middle so the app underneath stays readable.
 */
internal class EdgeGlowView(
    context: Context,
    private val palette: WarningPalette,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BAND_DP, resources.displayMetrics)

    /** One shader per corner bloom and edge band; rebuilt only when the size changes. */
    private var shapes: List<Pair<Shader, RectF>> = emptyList()

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val radius = minOf(wf, hf) * CORNER_RADIUS_FRACTION
        val corners =
            listOf(0f to 0f, wf to 0f, 0f to hf, wf to hf).mapIndexed { i, (cx, cy) ->
                val c = palette.corners[i]
                RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(withAlpha(c, CORNER_ALPHA), withAlpha(c, CORNER_MID_ALPHA), withAlpha(c, 0)),
                    floatArrayOf(0f, CORNER_MID_STOP, 1f),
                    Shader.TileMode.CLAMP,
                ) to RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            }
        val bands =
            listOf(
                linear(0f, 0f, 0f, band) to RectF(0f, 0f, wf, band),
                linear(0f, hf, 0f, hf - band) to RectF(0f, hf - band, wf, hf),
                linear(0f, 0f, band, 0f) to RectF(0f, 0f, band, hf),
                linear(wf, 0f, wf - band, 0f) to RectF(wf - band, 0f, wf, hf),
            )
        shapes = corners + bands
    }

    override fun onDraw(canvas: Canvas) {
        for ((shader, rect) in shapes) {
            paint.shader = shader
            canvas.drawRect(rect, paint)
        }
    }

    private fun linear(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ) = LinearGradient(
        x0,
        y0,
        x1,
        y1,
        withAlpha(palette.edge, EDGE_ALPHA),
        withAlpha(palette.edge, 0),
        Shader.TileMode.CLAMP,
    )

    private fun withAlpha(
        color: Int,
        alpha: Int,
    ) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val BAND_DP = 36f
        const val EDGE_ALPHA = 0xC8
        const val CORNER_ALPHA = 0xF0
        const val CORNER_MID_ALPHA = 0x70
        const val CORNER_MID_STOP = 0.45f
        const val CORNER_RADIUS_FRACTION = 0.6f
    }
}
