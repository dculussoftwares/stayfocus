package com.dculus.stayfocused.kids

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.navigation.KidsHome
import com.dculus.stayfocused.core.navigation.KidsLink
import com.dculus.stayfocused.core.navigation.KidsPermissions
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.kids.monitoring.ParentLinkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A linked phone opens on Home. Otherwise first run goes through the permission setup and later runs open on the QR
 * (Link) screen.
 */
internal fun kidsStartRoute(
    setupComplete: Boolean,
    linked: Boolean = false,
): Any =
    when {
        // A linked phone went through setup before it could link; reopening never sends it back to the QR.
        linked -> KidsHome

        !setupComplete -> KidsPermissions

        else -> KidsLink
    }

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
        private val linkStore: ParentLinkStore,
    ) : ViewModel() {
        val startRoute: StateFlow<Any?> =
            settings.settings
                .take(1)
                .map { kidsStartRoute(it.onboardingComplete, linked = linkStore.parentName != null) }
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
