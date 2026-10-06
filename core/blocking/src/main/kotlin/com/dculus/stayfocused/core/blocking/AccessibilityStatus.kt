package com.dculus.stayfocused.core.blocking

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    /** Whether the user has enabled [ForegroundAppService] in Settings. */
    fun isEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val self = ComponentName(context, ForegroundAppService::class.java)
        return isEnabled(
            masterSwitchOn = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1,
            enabledServices = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            self.flattenToString(),
            self.flattenToShortString(),
        )
    }

    /** Pure decision: the system master switch must be on and the service must be in the enabled list. */
    internal fun isEnabled(
        masterSwitchOn: Boolean,
        enabledServices: String?,
        vararg names: String,
    ): Boolean {
        if (!masterSwitchOn || enabledServices.isNullOrEmpty()) return false
        return enabledServices.split(':').any { entry -> names.any { it.equals(entry, ignoreCase = true) } }
    }
}
