package com.dculus.stayfocused.core.sync

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the App Check provider (Play Integrity in release, debug provider in debug builds) so that
 * Firestore and the callable Functions can enforce App Check. A no-op when Firebase is not configured.
 * Call once from `Application.onCreate`, in both apps.
 */
@Singleton
class AppCheckInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val availability: FirebaseAvailability,
    ) {
        fun install() {
            if (!availability.isConfigured) return
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(AppCheckProviders.factory(context))
        }
    }
