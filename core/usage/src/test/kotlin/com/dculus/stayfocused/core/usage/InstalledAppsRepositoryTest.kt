package com.dculus.stayfocused.core.usage

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import app.cash.turbine.test
import com.dculus.stayfocused.core.model.AppInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val shadowPm = shadowOf(context.packageManager)
    private val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    private val entries = mutableListOf<ResolveInfo>()

    @Before
    fun setUp() {
        shadowPm.addResolveInfoForIntent(launcherIntent, entries)
    }

    private fun publish() {
        shadowPm.addResolveInfoForIntent(launcherIntent, entries.toList())
    }

    private fun launcher(pkg: String, label: String, activity: String = "Main") = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.$activity"
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
        }
        nonLocalizedLabel = label
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `lists launchable apps sorted by label, deduped, without our own apps`() = runTest {
        entries += launcher("com.zed", "Zed")
        entries += launcher("com.apple", "apple")
        entries += launcher("com.apple", "apple", activity = "Second")
        entries += launcher("com.dculus.stayfocused", "Stay Focused")
        entries += launcher("com.dculus.stayfocused.kids", "Stay Focused Kids")
        entries += launcher("com.banana", "Banana")
        publish()

        PackageManagerInstalledAppsRepository(context).observeLaunchableApps().test {
            assertEquals(
                listOf(
                    AppInfo("com.apple", "apple"),
                    AppInfo("com.banana", "Banana"),
                    AppInfo("com.zed", "Zed"),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installing and uninstalling an app re-emits the list`() = runTest {
        entries += launcher("com.a", "A")
        publish()

        PackageManagerInstalledAppsRepository(context).observeLaunchableApps().test {
            assertEquals(listOf(AppInfo("com.a", "A")), awaitItem())

            entries += launcher("com.b", "B")
            publish()
            context.sendBroadcast(Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:com.b")))
            idleMain()
            assertEquals(listOf(AppInfo("com.a", "A"), AppInfo("com.b", "B")), awaitItem())

            entries.removeAll { it.activityInfo.packageName == "com.a" }
            publish()
            context.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.a")))
            idleMain()
            assertEquals(listOf(AppInfo("com.b", "B")), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sorting is locale aware and falls back to the package for blank labels`() {
        val sorted = toSortedApps(
            entries = listOf("p.z" to "Zebra", "p.e" to "Élan", "p.f" to "Fox", "p.blank" to " "),
            excluded = emptySet(),
            locale = java.util.Locale.FRANCE,
        )
        assertEquals(listOf("p.e", "p.f", "p.blank", "p.z"), sorted.map { it.pkg })
        assertEquals("p.blank", sorted[2].label)
    }

    @Test
    fun `merged manifest does not request QUERY_ALL_PACKAGES`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
    }
}
