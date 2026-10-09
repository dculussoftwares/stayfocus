package com.dculus.stayfocused.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.sync.AuthFailure
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

internal enum class AuthMode { SignIn, CreateAccount }

/** What is wrong, shown inline. The wording lives in `strings.xml`. */
internal enum class AuthError(
    val slot: ErrorSlot,
) {
    InvalidEmail(ErrorSlot.Email),
    EmailFirst(ErrorSlot.Email),
    ShortPassword(ErrorSlot.Password),
    WrongCredentials(ErrorSlot.Password),
    EmailInUse(ErrorSlot.Email),
    Network(ErrorSlot.General),
    Other(ErrorSlot.General),
}

/** Where an error is shown: under a field, or as a line of its own for failures that blame neither. */
internal enum class ErrorSlot { Email, Password, General }

internal data class SignInUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val error: AuthError? = null,
    val busy: Boolean = false,
)

internal sealed interface SignInEvent {
    /** Signed in, created, or skipped: move on to permissions. */
    data object Done : SignInEvent

    data class ResetSent(
        val email: String,
    ) : SignInEvent
}

private val EMAIL_PATTERN = Regex("""\S+@\S+\.\S+""")
internal const val MIN_PASSWORD_LENGTH = 6

@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SignInUiState())
        internal val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

        private val eventChannel = Channel<SignInEvent>(Channel.BUFFERED)
        internal val events: Flow<SignInEvent> = eventChannel.receiveAsFlow()

        internal fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, error = null) }

        internal fun onPasswordChange(value: String) = mutableState.update { it.copy(password = value, error = null) }

        internal fun toggleMode() =
            mutableState.update {
                it.copy(
                    mode = if (it.mode == AuthMode.SignIn) AuthMode.CreateAccount else AuthMode.SignIn,
                    error = null,
                )
            }

        internal fun submit() {
            val current = mutableState.value
            if (current.busy) return
            val error =
                when {
                    !EMAIL_PATTERN.matches(current.email) -> AuthError.InvalidEmail
                    current.password.length < MIN_PASSWORD_LENGTH -> AuthError.ShortPassword
                    else -> null
                }
            if (error != null) {
                mutableState.update { it.copy(error = error) }
                return
            }
            launchAuth {
                when (current.mode) {
                    AuthMode.SignIn -> auth.signInWithEmail(current.email, current.password)
                    AuthMode.CreateAccount -> auth.createAccount(current.email, current.password)
                }
            }
        }

        internal fun continueWithGoogle() {
            if (mutableState.value.busy) return
            launchAuth { auth.signInWithGoogle() }
        }

        internal fun forgotPassword() {
            val current = mutableState.value
            if (current.busy) return
            if (!current.email.contains('@')) {
                mutableState.update { it.copy(error = AuthError.EmailFirst) }
                return
            }
            viewModelScope.launch {
                mutableState.update { it.copy(busy = true, error = null) }
                val result = attempt { auth.sendPasswordReset(current.email) }
                mutableState.update { it.copy(busy = false, error = (result as? AuthResult.Failure)?.toError()) }
                if (result is AuthResult.Success) eventChannel.send(SignInEvent.ResetSent(current.email))
            }
        }

        /** "Continue without an account": remembers the choice and moves on. */
        internal fun skip() {
            if (mutableState.value.busy) return
            viewModelScope.launch {
                mutableState.update { it.copy(busy = true, error = null) }
                if (storeSkipped(true)) {
                    eventChannel.send(SignInEvent.Done)
                } else {
                    mutableState.update { it.copy(error = AuthError.Other) }
                }
                mutableState.update { it.copy(busy = false) }
            }
        }

        private fun launchAuth(call: suspend () -> AuthResult) {
            viewModelScope.launch {
                mutableState.update { it.copy(busy = true, error = null) }
                val result = attempt(call)
                val error =
                    when {
                        result is AuthResult.Failure -> result.toError()
                        !storeSkipped(false) -> AuthError.Other
                        else -> null
                    }
                if (error == null) eventChannel.send(SignInEvent.Done)
                mutableState.update { it.copy(busy = false, error = error) }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun storeSkipped(skipped: Boolean): Boolean =
            try {
                settings.setAccountSkipped(skipped)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not store accountSkipped")
                false
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun attempt(call: suspend () -> AuthResult): AuthResult =
            try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Auth call failed")
                AuthResult.Failure(AuthFailure.Other)
            }

        private fun AuthResult.Failure.toError(): AuthError =
            when (reason) {
                AuthFailure.InvalidCredentials -> AuthError.WrongCredentials
                AuthFailure.EmailInUse -> AuthError.EmailInUse
                AuthFailure.Network -> AuthError.Network
                AuthFailure.Other -> AuthError.Other
            }
    }
