package com.dculus.stayfocused.core.blocking.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.telecom.TelecomManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBlockAllowlistProviderTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun home(pkg: String) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg } }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }

    @Test fun containsSystemEssentialsAndOwnPackage() {
        val list = AndroidBlockAllowlistProvider(context).allowlist()
        assertTrue(context.packageName in list)
        assertTrue("com.android.systemui" in list)
        assertTrue("com.android.settings" in list)
    }

    @Test fun containsLauncher() {
        home("com.my.launcher")
        assertTrue("com.my.launcher" in AndroidBlockAllowlistProvider(context).allowlist())
    }

    @Test fun containsDefaultDialer() {
        shadowOf(context.getSystemService(TelecomManager::class.java)).setDefaultDialer("com.my.dialer")
        assertTrue("com.my.dialer" in AndroidBlockAllowlistProvider(context).allowlist())
    }
}
