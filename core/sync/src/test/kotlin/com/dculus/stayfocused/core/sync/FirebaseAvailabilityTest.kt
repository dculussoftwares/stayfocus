package com.dculus.stayfocused.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseAvailabilityTest {
    @Test
    fun notConfiguredWhenNoFirebaseAppExists() {
        assertFalse(FirebaseAvailability { false }.isConfigured)
    }

    @Test
    fun configuredWhenAFirebaseAppExists() {
        assertTrue(FirebaseAvailability { true }.isConfigured)
    }

    @Test
    fun reflectsLaterChanges() {
        var apps = false
        val availability = FirebaseAvailability { apps }
        assertFalse(availability.isConfigured)
        apps = true
        assertTrue(availability.isConfigured)
    }
}
