package com.dculus.stayfocused.kids.monitoring

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldMonitorTest {
    @Test
    fun monitorsOnlyWhileAParentIsLinked() {
        assertTrue(shouldMonitor("Alex"))
        assertFalse(shouldMonitor(null))
        assertFalse(shouldMonitor("  "))
    }
}
