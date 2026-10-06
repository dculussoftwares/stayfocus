package com.dculus.stayfocused.core.sync

import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckProviders {
    @Suppress("UNUSED_PARAMETER")
    fun factory(context: Context): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
