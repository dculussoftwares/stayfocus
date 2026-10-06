package com.dculus.stayfocused.core.blocking

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityStatusTest {
    private val full = "com.dculus.stayfocused/com.dculus.stayfocused.core.blocking.ForegroundAppService"
    private val short = "com.dculus.stayfocused/.core.blocking.ForegroundAppService"

    @Test fun enabledWhenListedAndMasterOn() =
        assertTrue(AccessibilityStatus.isEnabled(true, "com.other/.Svc:$full", full, short))

    @Test fun shortFormIsMatched() = assertTrue(AccessibilityStatus.isEnabled(true, short, full, short))

    @Test fun masterSwitchOffMeansDisabledEvenWhenListed() =
        assertFalse(AccessibilityStatus.isEnabled(false, full, full, short))

    @Test fun notListedMeansDisabled() = assertFalse(AccessibilityStatus.isEnabled(true, "com.other/.Svc", full, short))

    @Test fun nullOrEmptyListMeansDisabled() {
        assertFalse(AccessibilityStatus.isEnabled(true, null, full, short))
        assertFalse(AccessibilityStatus.isEnabled(true, "", full, short))
    }
}
