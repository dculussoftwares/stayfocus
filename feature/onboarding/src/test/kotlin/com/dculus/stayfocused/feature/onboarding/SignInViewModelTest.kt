package com.dculus.stayfocused.feature.onboarding

import app.cash.turbine.test
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.sync.AuthFailure
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import com.dculus.stayfocused.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SignInViewModel(auth, settings)

    private fun SignInViewModel.fill(
        email: String,
        password: String,
    ) {
        onEmailChange(email)
        onPasswordChange(password)
    }

    @Test fun startsInSignInMode() {
        assertEquals(AuthMode.SignIn, vm().state.value.mode)
    }

    @Test fun toggleSwitchesModeAndClearsError() {
        val vm = vm()
        vm.submit()
        assertEquals(AuthError.InvalidEmail, vm.state.value.error)
        vm.toggleMode()
        assertEquals(AuthMode.CreateAccount, vm.state.value.mode)
        assertNull(vm.state.value.error)
        vm.toggleMode()
        assertEquals(AuthMode.SignIn, vm.state.value.mode)
    }

    @Test fun emptyEmailIsInvalid() {
        val vm = vm()
        vm.fill("", "secret1")
        vm.submit()
        assertEquals(AuthError.InvalidEmail, vm.state.value.error)
    }

    @Test fun malformedEmailsAreInvalid() {
        listOf("plain", "a@b", "a@b.", "@b.co", "a b@c.de", "a@b .co").forEach { bad ->
            val vm = vm()
            vm.fill(bad, "secret1")
            vm.submit()
            assertEquals(AuthError.InvalidEmail, vm.state.value.error, bad)
        }
    }

    @Test fun validEmailPassesTheEmailCheck() {
        val vm = vm()
        vm.fill("me@example.com", "short")
        vm.submit()
        assertEquals(AuthError.ShortPassword, vm.state.value.error)
    }

    @Test fun passwordUnderSixCharactersIsRejected() {
        val vm = vm()
        vm.fill("me@example.com", "12345")
        vm.submit()
        assertEquals(AuthError.ShortPassword, vm.state.value.error)
        assertEquals(ErrorSlot.Password, AuthError.ShortPassword.slot)
    }

    @Test fun emailErrorTakesPriorityOverPasswordError() {
        val vm = vm()
        vm.fill("nope", "1")
        vm.submit()
        assertEquals(AuthError.InvalidEmail, vm.state.value.error)
    }

    @Test fun typingClearsTheError() {
        val vm = vm()
        vm.submit()
        vm.onEmailChange("m")
        assertNull(vm.state.value.error)
        vm.submit()
        vm.onPasswordChange("x")
        assertNull(vm.state.value.error)
    }

    @Test fun passwordOfSixCharactersSignsIn() =
        runTest {
            val vm = vm()
            vm.fill("me@example.com", "123456")
            vm.events.test {
                vm.submit()
                advanceUntilIdle()
                assertEquals(SignInEvent.Done, awaitItem())
            }
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.busy)
            assertEquals(listOf("signInWithEmail:me@example.com:123456"), auth.calls)
        }

    @Test fun createAccountAlsoValidatesAndContinues() =
        runTest {
            val vm = vm()
            vm.toggleMode()
            vm.fill("bad", "123456")
            vm.submit()
            assertEquals(AuthError.InvalidEmail, vm.state.value.error)
            vm.fill("me@example.com", "123456")
            vm.events.test {
                vm.submit()
                advanceUntilIdle()
                assertEquals(SignInEvent.Done, awaitItem())
            }
            assertEquals(listOf("createAccount:me@example.com:123456"), auth.calls)
        }

    @Test fun successfulSignInClearsAccountSkipped() =
        runTest {
            settings.setAccountSkipped(true)
            val vm = vm()
            vm.continueWithGoogle()
            advanceUntilIdle()
            assertFalse(settings.settings.first().accountSkipped)
        }

    @Test fun googleContinues() =
        runTest {
            val vm = vm()
            vm.events.test {
                vm.continueWithGoogle()
                advanceUntilIdle()
                assertEquals(SignInEvent.Done, awaitItem())
            }
            assertEquals(listOf("signInWithGoogle"), auth.calls)
        }

    @Test fun failedSettingsWriteAfterSignInShowsErrorInsteadOfContinuing() =
        runTest {
            val failing =
                object : SettingsRepository by settings {
                    override suspend fun setAccountSkipped(skipped: Boolean) = throw IOException("disk full")
                }
            val vm = SignInViewModel(auth, failing)
            vm.continueWithGoogle()
            vm.events.test {
                advanceUntilIdle()
                expectNoEvents()
            }
            assertEquals(AuthError.Other, vm.state.value.error)
            assertFalse(vm.state.value.busy)
        }

    @Test fun failedSettingsWriteOnSkipShowsError() =
        runTest {
            val failing =
                object : SettingsRepository by settings {
                    override suspend fun setAccountSkipped(skipped: Boolean) = throw IOException("disk full")
                }
            val vm = SignInViewModel(auth, failing)
            vm.skip()
            advanceUntilIdle()
            assertEquals(AuthError.Other, vm.state.value.error)
        }

    @Test fun failuresAreMappedToErrorsAndDoNotContinue() =
        runTest {
            mapOf(
                AuthFailure.InvalidCredentials to AuthError.WrongCredentials,
                AuthFailure.EmailInUse to AuthError.EmailInUse,
                AuthFailure.Network to AuthError.Network,
                AuthFailure.Other to AuthError.Other,
            ).forEach { (failure, error) ->
                auth.failWith = failure
                val vm = vm()
                vm.fill("me@example.com", "123456")
                vm.events.test {
                    vm.submit()
                    advanceUntilIdle()
                    expectNoEvents()
                }
                assertEquals(error, vm.state.value.error)
                assertFalse(vm.state.value.busy)
            }
        }

    @Test fun forgotPasswordWithEmptyEmailShowsHint() {
        val vm = vm()
        vm.forgotPassword()
        assertEquals(AuthError.EmailFirst, vm.state.value.error)
        assertEquals(ErrorSlot.Email, AuthError.EmailFirst.slot)
    }

    @Test fun forgotPasswordWithoutAtSignShowsHint() {
        val vm = vm()
        vm.onEmailChange("nobody")
        vm.forgotPassword()
        assertEquals(AuthError.EmailFirst, vm.state.value.error)
    }

    @Test fun forgotPasswordSendsResetAndReportsEmail() =
        runTest {
            val vm = vm()
            vm.onEmailChange("me@example.com")
            vm.events.test {
                vm.forgotPassword()
                advanceUntilIdle()
                assertEquals(SignInEvent.ResetSent("me@example.com"), awaitItem())
            }
            assertEquals(listOf("sendPasswordReset:me@example.com"), auth.calls)
            assertNull(vm.state.value.error)
        }

    @Test fun forgotPasswordFailureShowsError() =
        runTest {
            auth.failWith = AuthFailure.Network
            val vm = vm()
            vm.onEmailChange("me@example.com")
            vm.forgotPassword()
            advanceUntilIdle()
            assertEquals(AuthError.Network, vm.state.value.error)
        }

    @Test fun skipStoresAccountSkippedAndContinues() =
        runTest {
            val vm = vm()
            vm.events.test {
                vm.skip()
                advanceUntilIdle()
                assertEquals(SignInEvent.Done, awaitItem())
            }
            assertTrue(settings.settings.first().accountSkipped)
        }
}
