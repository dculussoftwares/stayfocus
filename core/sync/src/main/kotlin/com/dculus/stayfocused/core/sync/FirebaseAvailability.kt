package com.dculus.stayfocused.core.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this build has a Firebase configuration (`google-services.json` was present at build time).
 * When false, account, linking and cloud AI show "Not available in this build"; blocking works as usual.
 */
@Singleton
class FirebaseAvailability internal constructor(
    private val hasFirebaseApp: () -> Boolean,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this({ FirebaseApp.getApps(context).isNotEmpty() })

    val isConfigured: Boolean
        get() = hasFirebaseApp()
}
