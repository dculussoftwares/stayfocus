// Noise constants are literal v5 spec values (docs/design/V5_SPEC.md §3, "Grain").
@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.floor
import kotlin.math.roundToInt

internal const val GRAIN_TILE_SIZE = 180
internal const val GRAIN_SEED = 0x5F0CA11L
internal const val GRAIN_ALPHA = 0.16f
private const val GRAIN_OCTAVES = 3
private const val GRAIN_BASE_FREQUENCY = 0.85f

// Soft-light needs Paint.setBlendMode (API 29); older versions would draw a plain white veil, so it is lighter.
private const val GRAIN_ALPHA_LEGACY = 0.08f

/**
 * Fixed-seed fractal noise: white pixels whose alpha follows 3 octaves of smooth, tileable value noise at
 * `baseFrequency .85` (the prototype's `feTurbulence fractalNoise`, alpha .9, `stitchTiles`). The same [seed]
 * always gives the same pixels, so screenshots are stable, and the tile repeats without a seam.
 */
internal fun grainPixels(
    seed: Long = GRAIN_SEED,
    size: Int = GRAIN_TILE_SIZE,
): IntArray {
    val octaves = IntArray(GRAIN_OCTAVES) { (size * GRAIN_BASE_FREQUENCY * (1 shl it)).roundToInt().coerceAtLeast(1) }
    return IntArray(size * size) { index ->
        var sum = 0f
        octaves.forEachIndexed { octave, cells ->
            val x = index % size * cells / size.toFloat()
            val y = index / size * cells / size.toFloat()
            sum += valueNoise(seed, octave, cells, x, y) / (1 shl octave)
        }
        val alpha = ((sum + 1f) / 2f * 0.9f * 255f).toInt().coerceIn(0, 255)
        (alpha shl 24) or 0x00FFFFFF
    }
}

/** Smooth value noise in -.5..+.5 on a [cells] x [cells] lattice that wraps around. */
private fun valueNoise(
    seed: Long,
    octave: Int,
    cells: Int,
    x: Float,
    y: Float,
): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val tx = smooth(x - x0)
    val ty = smooth(y - y0)

    fun at(
        dx: Int,
        dy: Int,
    ) = lattice(seed, octave, (x0 + dx) % cells, (y0 + dy) % cells)
    val top = at(0, 0) + (at(1, 0) - at(0, 0)) * tx
    val bottom = at(0, 1) + (at(1, 1) - at(0, 1)) * tx
    return top + (bottom - top) * ty
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun lattice(
    seed: Long,
    octave: Int,
    x: Int,
    y: Int,
): Float {
    var h = seed + octave * 0x632BE5ABL + x * 0x85EBCA6BL + y * 0xC2B2AE35L
    h = (h xor (h ushr 33)) * 0x2545F4914F6CDD1DL
    h = h xor (h ushr 29)
    return ((h ushr 40) and 0xFFFFFFL) / 0xFFFFFF.toFloat() - 0.5f
}

private val grainBrush: ShaderBrush by lazy {
    val bitmap = Bitmap.createBitmap(grainPixels(), GRAIN_TILE_SIZE, GRAIN_TILE_SIZE, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Draws the grain tile under the content (soft-light, white, alpha .16). */
fun Modifier.grainOverlay(): Modifier =
    drawBehind {
        val soft = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        drawRect(
            brush = grainBrush,
            alpha = if (soft) GRAIN_ALPHA else GRAIN_ALPHA_LEGACY,
            blendMode = BlendMode.Softlight,
        )
    }

/** Clips to [shape], paints [gradient] and the grain on it. The content is drawn on top. */
fun Modifier.glow(
    gradient: V5Gradient,
    shape: Shape = RectangleShape,
): Modifier =
    clip(shape)
        .background(gradient.adaptive)
        .grainOverlay()
