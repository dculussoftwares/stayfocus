package com.dculus.stayfocused.core.blocking.permissions

import android.os.Build

/**
 * Android 13+ blocks Accessibility for apps installed outside a store ("restricted settings") until the user
 * allows it from App info. We show guidance first so the greyed-out toggle is not a dead end.
 */
object RestrictedSettings {
    private val trustedInstallers = setOf("com.android.vending")

    fun needsGuidance(
        sdk: Int,
        installerPackage: String?,
        accessibilityGranted: Boolean,
    ): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU && !accessibilityGranted && isSideloaded(installerPackage)

    /** No installer, or one that is not a store, means a sideloaded install. */
    fun isSideloaded(installerPackage: String?): Boolean =
        installerPackage == null || installerPackage !in trustedInstallers
}
