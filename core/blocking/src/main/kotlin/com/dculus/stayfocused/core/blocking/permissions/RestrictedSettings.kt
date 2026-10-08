package com.dculus.stayfocused.core.blocking.permissions

import android.os.Build

/**
 * Android 13+ blocks Accessibility for apps installed outside a store ("restricted settings") until the user
 * allows it from App info. We show guidance first so the greyed-out toggle is not a dead end.
 *
 * Installs with no installer (adb) are not restricted, and neither are installs by a known app store; anything
 * else (a file manager, a browser download, the package installer) is.
 */
object RestrictedSettings {
    private val storeInstallers =
        setOf(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia",
            "com.xiaomi.mipicks",
            "com.huawei.appmarket",
        )

    fun needsGuidance(
        sdk: Int,
        installerPackage: String?,
        accessibilityGranted: Boolean,
    ): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU && !accessibilityGranted && isRestrictedInstall(installerPackage)

    /** True when a person installed the APK by hand through an app that is not a store. */
    fun isRestrictedInstall(installerPackage: String?): Boolean =
        installerPackage != null && installerPackage !in storeInstallers
}
