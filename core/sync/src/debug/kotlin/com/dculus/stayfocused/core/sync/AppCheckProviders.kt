package com.dculus.stayfocused.core.sync

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviders {
    private const val STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
    private const val SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    /**
     * Debug provider. When a token was supplied at build time (`APP_CHECK_DEBUG_TOKEN` env var), it is
     * seeded as the SDK's debug secret so CI and emulators use a token registered in the Firebase console.
     * Otherwise the SDK generates one and logs it (Logcat tag `DebugAppCheckProvider`).
     */
    fun factory(context: Context): AppCheckProviderFactory {
        val token = BuildConfig.APP_CHECK_DEBUG_TOKEN
        if (token.isNotEmpty()) {
            val key = STORE_PREFIX + FirebaseApp.getInstance().persistenceKey
            context.getSharedPreferences(key, Context.MODE_PRIVATE).edit { putString(SECRET_KEY, token) }
        }
        return DebugAppCheckProviderFactory.getInstance()
    }
}
