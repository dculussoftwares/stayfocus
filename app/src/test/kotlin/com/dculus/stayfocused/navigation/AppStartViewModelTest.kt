package com.dculus.stayfocused.navigation

import com.dculus.stayfocused.core.navigation.MainGraph
import com.dculus.stayfocused.core.navigation.OnboardingGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun source(flow: Flow<Boolean>) =
        object : OnboardingFlagSource {
            override val onboardingComplete: Flow<Boolean> = flow
        }

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
    fun stubReportsOnboardingIncomplete() {
        assertEquals(StartGraph.ONBOARDING, AppStartViewModel(StubOnboardingFlagSource()).startGraph.value)
    }

    @Test
    fun completedOnboardingStartsMain() {
        assertEquals(StartGraph.MAIN, AppStartViewModel(source(flowOf(true))).startGraph.value)
    }

    @Test
    fun startGraphIsNullUntilTheFlagLoads() {
        val flag = MutableSharedFlow<Boolean>()
        val viewModel = AppStartViewModel(source(flag))
        assertNull(viewModel.startGraph.value)
    }
}
