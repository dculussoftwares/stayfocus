package com.dculus.stayfocused.core.sync

import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviders {
    /**
     * Debug provider. The SDK generates a per-install debug token and logs it (Logcat tag
     * `DebugAppCheckProvider`); register it in the Firebase console. No token is ever built into the APK.
     */
    @Suppress("UNUSED_PARAMETER")
    fun factory(context: Context): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
