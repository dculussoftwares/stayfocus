package com.dculus.stayfocused.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

/** Where the `onboardingComplete` flag comes from. DataStore replaces the stub in M1-11. */
interface OnboardingFlagSource {
    val onboardingComplete: Flow<Boolean>
}

/** Stub until DataStore is wired (M1-11): onboarding is never complete. */
class StubOnboardingFlagSource
    @Inject
    constructor() : OnboardingFlagSource {
        override val onboardingComplete: Flow<Boolean> = flowOf(false)
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppStartModule {
    @Binds
    abstract fun onboardingFlagSource(impl: StubOnboardingFlagSource): OnboardingFlagSource
}

/** Picks the start graph from the `onboardingComplete` flag; `null` until the flag has loaded (splash stays up). */
@HiltViewModel
class AppStartViewModel
    @Inject
    constructor(
        flagSource: OnboardingFlagSource,
    ) : ViewModel() {
        val startGraph: StateFlow<StartGraph?> =
            flagSource.onboardingComplete
                .map { startGraphFor(it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }
