package com.dculus.stayfocused.core.blocking.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Own package, launcher(s), default dialer, default SMS app, Settings and System UI. */
class AndroidBlockAllowlistProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BlockAllowlistProvider {
        override fun allowlist(): Set<String> =
            buildSet {
                add(context.packageName)
                add(SYSTEM_UI)
                add(SETTINGS)
                addAll(launchers())
                defaultDialer()?.let(::add)
                defaultSms()?.let(::add)
            }

        private fun launchers(): Set<String> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val default = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            // "android" is the chooser: no default is set, so every launcher counts.
            if (default != null && default != ANDROID) return setOf(default)
            return pm
                .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNullTo(HashSet()) { it.activityInfo?.packageName }
        }

        private fun defaultDialer(): String? =
            try {
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            } catch (_: SecurityException) {
                null
            }

        private fun defaultSms(): String? =
            try {
                Telephony.Sms.getDefaultSmsPackage(context)
            } catch (_: SecurityException) {
                null
            }

        private companion object {
            const val SYSTEM_UI = "com.android.systemui"
            const val SETTINGS = "com.android.settings"
            const val ANDROID = "android"
        }
    }
