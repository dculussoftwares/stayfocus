package com.dculus.stayfocused.feature.account

import app.cash.turbine.test
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AuthFailure
import com.dculus.stayfocused.core.sync.AuthResult
import com.dculus.stayfocused.core.sync.AuthUser
import com.dculus.stayfocused.core.sync.DeleteAccountFailure
import com.dculus.stayfocused.core.sync.DeleteAccountResult
import com.dculus.stayfocused.core.sync.FakeAccountDeletionRepository
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
            val auth = FakeAuthRepository()
            val viewModel =
                AccountViewModel(
                    auth,
                    FakePermissions(),
                    EngineHealthMonitor(Clock.systemUTC()),
                    FakeAccountDeletionRepository(auth) {},
                )
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
            val viewModel = AccountViewModel(auth, permissions, engine, FakeAccountDeletionRepository(auth) {})
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

    private class DeleteFixture {
        val auth = FakeAuthRepository()
        val deletion = FakeAccountDeletionRepository(auth) {}
        val viewModel =
            AccountViewModel(auth, FakePermissions(), EngineHealthMonitor(Clock.systemUTC()), deletion)
    }

    @Test
    fun deleteWithAFreshSignInDeletesAndSignsOut() =
        runTest {
            val f = DeleteFixture()
            f.auth.signInWithEmail("alex@example.com", "secret1")
            f.viewModel.startDelete()
            assertEquals(DeleteStep.Confirm, f.viewModel.delete.value.step)
            f.viewModel.confirmDelete()
            assertTrue(f.viewModel.delete.value.deleted)
            assertEquals(1, f.deletion.deleteCalls)
            f.viewModel.deleteHandled()
            assertEquals(DeleteUiState(), f.viewModel.delete.value)
        }

    @Test
    fun cancelClosesTheSheetWithoutDeleting() =
        runTest {
            val f = DeleteFixture()
            f.viewModel.startDelete()
            f.viewModel.cancelDelete()
            assertEquals(DeleteStep.Hidden, f.viewModel.delete.value.step)
            assertEquals(0, f.deletion.deleteCalls)
        }

    @Test
    fun aStaleSignInAsksToReauthenticateBeforeDeleting() =
        runTest {
            val f = DeleteFixture()
            f.deletion.staleSignIn = true
            f.viewModel.startDelete()
            f.viewModel.confirmDelete()
            assertEquals(DeleteStep.Reauth, f.viewModel.delete.value.step)
            assertEquals(0, f.deletion.deleteCalls)

            f.viewModel.reauthenticateWithPassword("secret1")
            assertTrue(f.viewModel.delete.value.deleted)
            assertEquals(1, f.deletion.deleteCalls)
        }

    @Test
    fun aServerRefusalForAStaleTokenAlsoAsksToReauthenticate() =
        runTest {
            val f = DeleteFixture()
            f.deletion.deleteResult = DeleteAccountResult.NeedsReauth
            f.viewModel.startDelete()
            f.viewModel.confirmDelete()
            assertEquals(DeleteStep.Reauth, f.viewModel.delete.value.step)
        }

    @Test
    fun aWrongPasswordShowsAnErrorAndDeletesNothing() =
        runTest {
            val f = DeleteFixture()
            f.deletion.staleSignIn = true
            f.deletion.reauthResult = AuthResult.Failure(AuthFailure.InvalidCredentials)
            f.viewModel.startDelete()
            f.viewModel.confirmDelete()
            f.viewModel.reauthenticateWithPassword("nope")
            val state = f.viewModel.delete.value
            assertEquals(DeleteError.WrongPassword, state.error)
            assertEquals(DeleteStep.Reauth, state.step)
            assertEquals(false, state.working)
            assertEquals(0, f.deletion.deleteCalls)
        }

    @Test
    fun aFailedDeleteKeepsTheSheetOpenWithAnError() =
        runTest {
            val f = DeleteFixture()
            f.deletion.deleteResult = DeleteAccountResult.Failure(DeleteAccountFailure.Network)
            f.viewModel.startDelete()
            f.viewModel.confirmDelete()
            val state = f.viewModel.delete.value
            assertEquals(DeleteError.Network, state.error)
            assertEquals(DeleteStep.Confirm, state.step)
            assertEquals(false, state.working)
            assertEquals(false, state.deleted)
        }
}
