package com.dculus.stayfocused.core.blocking

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    /** Whether the user has enabled [ForegroundAppService] in Settings. */
    fun isEnabled(context: Context): Boolean {
        val masterOn = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!masterOn) return false
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val self = ComponentName(context, ForegroundAppService::class.java)
        return isServiceListed(enabled, self.flattenToString(), self.flattenToShortString())
    }

    internal fun isServiceListed(enabledServices: String?, vararg names: String): Boolean {
        if (enabledServices.isNullOrEmpty()) return false
        return enabledServices.split(':').any { entry -> names.any { it.equals(entry, ignoreCase = true) } }
    }
}
