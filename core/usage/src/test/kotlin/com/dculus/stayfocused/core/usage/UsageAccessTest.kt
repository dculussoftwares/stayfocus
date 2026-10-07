package com.dculus.stayfocused.core.usage

import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageAccessTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val appOps = shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
    private val access = AppOpsUsageAccess(context)

    private fun setMode(mode: Int) =
        appOps.setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
            mode,
        )

    @Test
    fun `granted when the app op is allowed`() {
        setMode(AppOpsManager.MODE_ALLOWED)
        assertTrue(access.isGranted())
    }

    @Test
    fun `not granted when the app op is ignored`() {
        setMode(AppOpsManager.MODE_IGNORED)
        assertFalse(access.isGranted())
    }

    @Test
    fun `settings intent opens usage access settings`() {
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, access.settingsIntent().action)
    }
}
