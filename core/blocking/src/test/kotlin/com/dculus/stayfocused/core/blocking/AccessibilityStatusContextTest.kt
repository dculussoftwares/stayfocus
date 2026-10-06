package com.dculus.stayfocused.core.blocking

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityStatusContextTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun setSettings(
        master: Int,
        services: String?,
    ) {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, master)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
    }

    @Test fun enabledWhenServiceListedAndMasterOn() {
        val self = ComponentName(context, ForegroundAppService::class.java)
        setSettings(1, self.flattenToShortString())
        assertTrue(AccessibilityStatus.isEnabled(context))
    }

    @Test fun disabledWhenNothingEnabled() {
        setSettings(0, null)
        assertFalse(AccessibilityStatus.isEnabled(context))
    }
}
