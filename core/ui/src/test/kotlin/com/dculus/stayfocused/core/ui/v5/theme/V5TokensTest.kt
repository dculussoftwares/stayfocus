package com.dculus.stayfocused.core.ui.v5.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V5TokensTest {
    @Test
    fun `grain tile is deterministic and 180 square`() {
        val first = grainPixels()
        val second = grainPixels()
        assertEquals(GRAIN_TILE_SIZE * GRAIN_TILE_SIZE, first.size)
        assertEquals(180, GRAIN_TILE_SIZE)
        assertTrue(first.contentEquals(second))
        assertTrue(!first.contentEquals(grainPixels(seed = GRAIN_SEED + 1)))
    }

    @Test
    fun `grain pixels are white with varying alpha below 0_9`() {
        val pixels = grainPixels()
        assertTrue(pixels.all { (it and 0x00FFFFFF) == 0x00FFFFFF })
        val alphas = pixels.map { it ushr 24 }
        assertTrue(alphas.max() <= (0.9f * 255).toInt())
        assertTrue(alphas.distinct().size > 20)
    }

    @Test
    fun `grain is spatially coherent noise and tiles without a seam`() {
        val size = GRAIN_TILE_SIZE
        val alpha = grainPixels().map { (it ushr 24).toDouble() }
        val mean = alpha.average()

        // Lag-1 correlation along rows and columns, including the wrap from the last pixel to the first.
        fun correlation(neighbour: (Int) -> Int): Double {
            val covariance = alpha.indices.sumOf { (alpha[it] - mean) * (alpha[neighbour(it)] - mean) }
            return covariance / alpha.sumOf { (it - mean) * (it - mean) }
        }
        val across = correlation { (it / size) * size + (it % size + 1) % size }
        val down = correlation { ((it / size + 1) % size) * size + it % size }
        assertTrue("across=$across", across > 0.15)
        assertTrue("down=$down", down > 0.15)
    }

    @Test
    fun `type scale follows the spec`() {
        val t = v5Type()
        assertEquals(92.sp, t.displayXL.fontSize)
        assertEquals(200, t.displayXL.fontWeight?.weight)
        assertEquals(66.sp, t.displayL.fontSize)
        assertEquals(62.sp, t.hero.fontSize)
        assertEquals(300, t.hero.fontWeight?.weight)
        assertEquals(38.sp, t.title.fontSize)
        assertEquals(28.sp, t.heading.fontSize)
        assertEquals(400, t.heading.fontWeight?.weight)
        assertEquals(22.sp, t.rule.fontSize)
        assertEquals(17.sp, t.bodyL.fontSize)
        assertEquals(600, t.bodyL.fontWeight?.weight)
        assertEquals(15.sp, t.body.fontSize)
        assertEquals(12.sp, t.tag.fontSize)
        assertEquals(700, t.tag.fontWeight?.weight)
        assertEquals(92 * 0.95f, t.displayXL.lineHeight.value, 0.01f)
    }

    @Test
    fun `every named gradient builds a shader at any size`() {
        V5Gradients.all.values.forEach { gradient ->
            gradient.brush(Size(390f, 844f))
            gradient.brush(Size(0f, 0f))
        }
        assertEquals(16, V5Gradients.all.size)
    }

    @Test
    fun `gradient depth is limited`() {
        val layer = V5Gradients.welcome.layers.first()
        assertThrows(IllegalArgumentException::class.java) {
            V5Gradient(V5GradientBase.Solid(V5Colors().orange), listOf(layer, layer, layer))
        }
    }

    @Test
    fun `welcome gradient has the two prototype layers over orange`() {
        val g = V5Gradients.welcome
        assertEquals(V5GradientBase.Solid(V5Colors().orange), g.base)
        assertEquals(2, g.layers.size)
        assertEquals(0.8f, g.layers[0].cx, 0f)
        assertEquals(0.5f, g.layers[0].ry, 0f)
        assertEquals(
            0.7f,
            g.layers[0]
                .stops
                .last()
                .position,
            0f,
        )
    }
}
