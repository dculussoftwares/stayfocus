package com.dculus.vesper

import android.content.Intent
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BootReceiverTest {
    @Test fun bootAndUpdateRestartTheStandardEngine() {
        assertTrue(BootReceiver.restartsEngine(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReceiver.restartsEngine(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test fun otherBroadcastsAreIgnored() {
        assertFalse(BootReceiver.restartsEngine(Intent.ACTION_LOCALE_CHANGED))
        assertFalse(BootReceiver.restartsEngine(null))
    }
}
