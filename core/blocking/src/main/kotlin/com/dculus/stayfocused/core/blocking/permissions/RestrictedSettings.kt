package com.dculus.stayfocused.core.blocking.permissions

import android.os.Build

/**
 * Android 13+ blocks Accessibility for apps installed outside a store ("restricted settings") until the user
 * allows it from App info. We show guidance first so the greyed-out toggle is not a dead end.
 *
 * Installs by a known app store are not restricted; anything else (file manager, browser download, package
 * installer, unknown installer) is treated as restricted.
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

    /**
     * True unless a known store installed the app. A null installer is ambiguous (adb, or an installer app that was
     * since removed), so it errs towards showing the guidance: a redundant hint costs far less than a dead end.
     */
    fun isRestrictedInstall(installerPackage: String?): Boolean =
        installerPackage == null || installerPackage !in storeInstallers
}
