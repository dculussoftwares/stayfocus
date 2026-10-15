package com.dculus.stayfocused.feature.account

import app.cash.turbine.test
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AuthUser
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class FakePermissions : PermissionsRepository {
        val state =
            MutableStateFlow(Permissions(usage = false, accessibility = false, overlay = false, notifications = false))

        override fun observe(): Flow<Permissions> = state

        override fun current(): Permissions = state.value

        override fun needsRestrictedSettingsGuidance(): Boolean = false
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun skippedSignInHasNoUser() =
        runTest {
            val viewModel =
                AccountViewModel(FakeAuthRepository(), FakePermissions(), EngineHealthMonitor(Clock.systemUTC()))
            viewModel.state.test {
                var item = awaitItem()
                if (!item.authLoaded) item = awaitItem()
                assertTrue(item.authLoaded)
                assertNull(item.user)
            }
        }

    @Test
    fun permissionsAndEngineFollowTheirSourcesAndSignOutClearsTheUser() =
        runTest {
            val auth = FakeAuthRepository()
            val permissions = FakePermissions()
            val engine = EngineHealthMonitor(Clock.systemUTC())
            auth.signInWithEmail("alex@example.com", "secret1")
            val viewModel = AccountViewModel(auth, permissions, engine)
            viewModel.state.test {
                var item = awaitItem()
                if (!item.authLoaded) item = awaitItem()
                assertNotNull(item.user)
                permissions.state.value = permissions.state.value.copy(usage = true)
                item = awaitItem()
                assertTrue(item.permissions.usage)
                engine.onServiceConnected()
                assertTrue(awaitItem().engine.serviceBound)
                viewModel.signOut()
                assertNull(awaitItem().user)
            }
        }

    @Test
    fun nameFallsBackToTheEmailLocalPart() {
        assertEquals("Alex", AuthUser("u", "a@b.co", "Alex").shownName())
        assertEquals("alex", AuthUser("u", "alex@b.co", null).shownName())
        assertEquals("A", AuthUser("u", "alex@b.co", null).initial())
        assertEquals("?", AuthUser("u", null, null).initial())
    }
}
