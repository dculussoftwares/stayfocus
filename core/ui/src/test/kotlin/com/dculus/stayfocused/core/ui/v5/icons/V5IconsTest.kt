package com.dculus.stayfocused.core.ui.v5.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

class V5IconsTest {
    private fun strokeWidth(icon: ImageVector): Float = (icon.root.first() as VectorPath).strokeLineWidth

    @Test
    fun everyIconIsA24ViewportVector() {
        v5IconSet.forEach { (name, icon) ->
            assertEquals(name, 24f, icon.viewportWidth)
            assertEquals(name, 24f, icon.viewportHeight)
            assertEquals(name, 24f, icon.defaultWidth.value)
        }
    }

    @Test
    fun strokeWidthsAreAsDrawnInThePrototype() {
        assertEquals(2f, strokeWidth(V5Icons.back))
        assertEquals(2f, strokeWidth(V5Icons.close))
        assertEquals(1.8f, strokeWidth(V5Icons.bell))
        assertEquals(1.8f, strokeWidth(V5Icons.lock))
        assertEquals(1.8f, strokeWidth(V5Icons.timer))
        assertEquals(2.2f, strokeWidth(V5Icons.plusMedium))
        assertEquals(2.6f, strokeWidth(V5Icons.plusBold))
        assertEquals(2.6f, strokeWidth(V5Icons.check))
        assertEquals(3f, strokeWidth(V5Icons.checkBold))
    }

    @Test
    fun iconNamesAreUnique() {
        assertEquals(v5IconSet.size, v5IconSet.map { it.first }.toSet().size)
    }
}
