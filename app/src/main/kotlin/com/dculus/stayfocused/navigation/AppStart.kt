package com.dculus.stayfocused.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The graph the app opens on. [route] is the type-safe route object passed to `NavHost`. */
enum class StartGraph(
    val route: Any,
) {
    ONBOARDING(OnboardingGraph),
    MAIN(MainGraph),
}

internal fun startGraphFor(onboardingComplete: Boolean): StartGraph =
    if (onboardingComplete) StartGraph.MAIN else StartGraph.ONBOARDING

/**
 * Picks the start graph from the `onboardingComplete` flag; `null` until the flag has loaded (splash stays up).
 * Only the first value counts: a later change (finishing onboarding, signing out) must not rebuild the NavHost.
 */
@HiltViewModel
class AppStartViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val startGraph: StateFlow<StartGraph?> =
            settings.settings
                .take(1)
                .map { startGraphFor(it.onboardingComplete) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** Remembers that onboarding is done (the last onboarding step). */
        fun completeOnboarding() {
            viewModelScope.launch { settings.setOnboardingComplete(true) }
        }
    }
