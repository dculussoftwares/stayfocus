package com.dculus.stayfocused.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.navigation.KidsLink
import com.dculus.stayfocused.core.navigation.KidsPermissions
import com.dculus.stayfocused.core.sync.DeviceAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/** First run goes through the permission setup; afterwards the app opens on the QR (Link) screen. */
internal fun kidsStartRoute(setupComplete: Boolean): Any = if (setupComplete) KidsLink else KidsPermissions

/**
 * Picks the start destination from the setup flag (`null` until it has loaded; only the first value counts so
 * finishing setup does not rebuild the NavHost) and makes sure this phone has its anonymous Firebase identity.
 */
@HiltViewModel
class KidsStartViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val deviceAuth: DeviceAuth,
    ) : ViewModel() {
        val startRoute: StateFlow<Any?> =
            settings.settings
                .take(1)
                .map { kidsStartRoute(it.onboardingComplete) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        init {
            // Anonymous sign-in on first launch; later launches reuse the persisted session. A failure (offline)
            // is retried on the next launch, and the Link screen asks again before it needs the uid.
            viewModelScope.launch { deviceAuth.ensureSignedIn() }
        }

        /** Remembers that the permission setup is done, so the next launch opens on Link. */
        suspend fun completeSetup() {
            settings.setOnboardingComplete(true)
        }
    }
