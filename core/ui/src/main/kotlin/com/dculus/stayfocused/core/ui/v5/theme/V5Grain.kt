// Noise constants are literal v5 spec values (docs/design/V5_SPEC.md §3, "Grain").
@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.v5.theme

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
import java.util.Random

internal const val GRAIN_TILE_SIZE = 180
internal const val GRAIN_SEED = 0x5F0CA11L
internal const val GRAIN_ALPHA = 0.16f

// Soft-light needs Paint.setBlendMode (API 29); older versions would draw a plain white veil, so it is lighter.
private const val GRAIN_ALPHA_LEGACY = 0.08f

/**
 * Fixed-seed fractal-ish noise: white pixels whose alpha is a 3-octave sum of random values (the prototype's
 * `feTurbulence` at alpha .9). The same [seed] always gives the same pixels, so screenshots are stable.
 */
internal fun grainPixels(
    seed: Long = GRAIN_SEED,
    size: Int = GRAIN_TILE_SIZE,
): IntArray {
    val random = Random(seed)
    return IntArray(size * size) {
        val noise = (random.nextFloat() * 4f + random.nextFloat() * 2f + random.nextFloat()) / 7f
        val alpha = (noise * 0.9f * 255f).toInt().coerceIn(0, 255)
        (alpha shl 24) or 0x00FFFFFF
    }
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
