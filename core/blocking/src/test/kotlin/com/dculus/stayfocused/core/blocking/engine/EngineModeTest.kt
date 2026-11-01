package com.dculus.stayfocused.core.blocking.engine

import org.junit.Test
import kotlin.test.assertEquals

class EngineModeTest {
    @Test fun accessibilityAlwaysWins() {
        for (usage in listOf(true, false)) {
            for (overlay in listOf(true, false)) {
                assertEquals(EngineMode.ACCESSIBILITY, resolveEngineMode(true, usage, overlay))
            }
        }
    }

    @Test fun standardNeedsUsageAndOverlay() {
        assertEquals(EngineMode.STANDARD, resolveEngineMode(false, usage = true, overlay = true))
        assertEquals(EngineMode.OFF, resolveEngineMode(false, usage = true, overlay = false))
        assertEquals(EngineMode.OFF, resolveEngineMode(false, usage = false, overlay = true))
        assertEquals(EngineMode.OFF, resolveEngineMode(false, usage = false, overlay = false))
    }

    @Test fun healthRunningFollowsMode() {
        assertEquals(false, EngineHealth().running)
        assertEquals(true, EngineHealth(mode = EngineMode.STANDARD).running)
        assertEquals(true, EngineHealth(mode = EngineMode.ACCESSIBILITY).running)
    }
}
