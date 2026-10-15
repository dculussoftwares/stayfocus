package com.dculus.stayfocused.kids

import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.navigation.KidsLink
import com.dculus.stayfocused.core.navigation.KidsPermissions
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class KidsStartViewModelTest {
    private class FakeDeviceAuth(
        existing: String? = null,
    ) : DeviceAuth {
        private val state = MutableStateFlow(existing)
        var signIns = 0
        override val uid: Flow<String?> = state

        override suspend fun ensureSignedIn(): String? {
            state.value?.let { return it }
            signIns++
            state.value = "anon-uid"
            return state.value
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun settings(complete: Boolean) = FakeSettingsRepository(AppSettings(onboardingComplete = complete))

    @Test
    fun freshInstallStartsOnPermissions() {
        assertEquals(KidsPermissions, KidsStartViewModel(settings(false), FakeDeviceAuth()).startRoute.value)
    }

    @Test
    fun afterSetupTheAppOpensOnLink() {
        assertEquals(KidsLink, KidsStartViewModel(settings(true), FakeDeviceAuth()).startRoute.value)
    }

    @Test
    fun completingSetupSavesTheFlagButKeepsTheStartRoute() {
        val repository = settings(false)
        val viewModel = KidsStartViewModel(repository, FakeDeviceAuth())
        runBlocking { viewModel.completeSetup() }
        assertEquals(true, runBlocking { repository.settings.first().onboardingComplete })
        assertEquals(KidsPermissions, viewModel.startRoute.value)
    }

    @Test
    fun firstLaunchSignsInAnonymously() {
        val auth = FakeDeviceAuth()
        KidsStartViewModel(settings(false), auth)
        assertEquals(1, auth.signIns)
        assertEquals("anon-uid", runBlocking { auth.uid.first() })
    }

    @Test
    fun laterLaunchesReuseTheExistingIdentity() {
        val auth = FakeDeviceAuth(existing = "anon-uid")
        KidsStartViewModel(settings(true), auth)
        assertEquals(0, auth.signIns)
    }
}
