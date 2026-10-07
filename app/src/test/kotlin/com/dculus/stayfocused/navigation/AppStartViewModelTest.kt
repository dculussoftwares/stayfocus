package com.dculus.stayfocused.navigation

import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun settings(complete: Boolean = false) = FakeSettingsRepository(AppSettings(onboardingComplete = complete))

    @Test
    fun startGraphFollowsTheFlag() {
        assertEquals(StartGraph.ONBOARDING, startGraphFor(false))
        assertEquals(StartGraph.MAIN, startGraphFor(true))
    }

    @Test
    fun routesAreTheTypeSafeGraphObjects() {
        assertEquals(OnboardingGraph, StartGraph.ONBOARDING.route)
        assertEquals(MainGraph, StartGraph.MAIN.route)
    }

    @Test
    fun incompleteOnboardingStartsOnboarding() {
        assertEquals(StartGraph.ONBOARDING, AppStartViewModel(settings(false)).startGraph.value)
    }

    @Test
    fun completedOnboardingStartsMain() {
        assertEquals(StartGraph.MAIN, AppStartViewModel(settings(true)).startGraph.value)
    }

    @Test
    fun completingOnboardingSavesTheFlagButKeepsTheStartGraph() {
        val repository = settings(false)
        val viewModel = AppStartViewModel(repository)
        viewModel.completeOnboarding()
        assertEquals(true, runBlocking { repository.settings.first().onboardingComplete })
        assertEquals(StartGraph.ONBOARDING, viewModel.startGraph.value)
    }
}
