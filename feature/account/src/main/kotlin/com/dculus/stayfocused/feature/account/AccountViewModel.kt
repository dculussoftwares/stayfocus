package com.dculus.stayfocused.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.blocking.engine.EngineHealth
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AccountDeletionRepository
import com.dculus.stayfocused.core.sync.AuthFailure
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.AuthResult
import com.dculus.stayfocused.core.sync.AuthUser
import com.dculus.stayfocused.core.sync.DeleteAccountFailure
import com.dculus.stayfocused.core.sync.DeleteAccountResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the account screen shows. [user] is null when the person skipped sign-in. */
internal data class AccountUiState(
    val user: AuthUser? = null,
    val permissions: Permissions =
        Permissions(usage = false, accessibility = false, overlay = false, notifications = false),
    val engine: EngineHealth = EngineHealth(),
    /** False until the auth repository has emitted, so a persisted session is not shown as signed out. */
    val authLoaded: Boolean = true,
)

/** Where the delete-account flow is. */
internal enum class DeleteStep {
    Hidden,

    /** "Delete account and all data?" with Cancel. */
    Confirm,

    /** The sign-in is older than 5 minutes: ask for the password (or Google) again. */
    Reauth,
}

/** What went wrong in the delete flow, shown inline in the sheet. */
internal enum class DeleteError {
    WrongPassword,
    Network,
    Other,

    /** The account is deleted but this phone's data could not be erased. */
    LocalWipe,
}

internal data class DeleteUiState(
    val step: DeleteStep = DeleteStep.Hidden,
    val usesPassword: Boolean = true,
    val working: Boolean = false,
    val error: DeleteError? = null,
    /** True once everything is deleted; the route navigates to Welcome and calls [AccountViewModel.deleteHandled]. */
    val deleted: Boolean = false,
)

/** Name line: the display name, else the part of the email before the "@", else null. */
internal fun AuthUser.shownName(): String? =
    displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }

/** One letter for the avatar tile. */
internal fun AuthUser.initial(): String =
    (shownName() ?: email)?.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        permissions: PermissionsRepository,
        engine: EngineHealthMonitor,
        private val deletion: AccountDeletionRepository,
    ) : ViewModel() {
        private val deleteState = MutableStateFlow(DeleteUiState())
        internal val delete: StateFlow<DeleteUiState> = deleteState.asStateFlow()

        internal val state: StateFlow<AccountUiState> =
            combine(auth.currentUser, permissions.observe(), engine.health) { user, perms, health ->
                AccountUiState(user, perms, health, authLoaded = true)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                AccountUiState(
                    permissions = permissions.current(),
                    engine = engine.health.value,
                    authLoaded = false,
                ),
            )

        /** Signs out. Blocks stay on this device; nothing else is stored per account yet (cached devices: M7). */
        fun signOut() {
            viewModelScope.launch {
                try {
                    auth.signOut()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Timber.w(e, "Sign out failed")
                }
            }
        }

        fun startDelete() {
            deleteState.value = DeleteUiState(step = DeleteStep.Confirm)
        }

        fun cancelDelete() {
            if (!deleteState.value.working) deleteState.value = DeleteUiState()
        }

        /** Confirmed: delete now, or ask the person to sign in again first when the sign-in is too old. */
        fun confirmDelete() {
            if (deleteState.value.working) return
            deleteState.update { it.copy(working = true, error = null) }
            viewModelScope.launch {
                if (deletion.isSignInFresh()) runDelete() else showReauth()
            }
        }

        fun reauthenticateWithPassword(password: String) {
            reauthenticate { deletion.reauthenticateWithPassword(password) }
        }

        fun reauthenticateWithGoogle(activityContext: Context) {
            reauthenticate { deletion.reauthenticateWithGoogle(activityContext) }
        }

        fun deleteHandled() {
            deleteState.value = DeleteUiState()
        }

        private fun reauthenticate(call: suspend () -> AuthResult) {
            if (deleteState.value.working) return
            deleteState.update { it.copy(working = true, error = null) }
            viewModelScope.launch {
                when (val result = call()) {
                    AuthResult.Success -> {
                        runDelete()
                    }

                    AuthResult.Cancelled -> {
                        deleteState.update { it.copy(working = false) }
                    }

                    is AuthResult.Failure -> {
                        deleteState.update {
                            it.copy(working = false, error = result.reason.toDeleteError(it.usesPassword))
                        }
                    }
                }
            }
        }

        private suspend fun showReauth() {
            val password = deletion.usesPassword()
            deleteState.value = DeleteUiState(step = DeleteStep.Reauth, usesPassword = password)
        }

        private suspend fun runDelete() {
            when (val result = deletion.deleteAccount()) {
                DeleteAccountResult.Success -> {
                    deleteState.value = DeleteUiState(deleted = true)
                }

                DeleteAccountResult.NeedsReauth -> {
                    showReauth()
                }

                is DeleteAccountResult.Failure -> {
                    val error =
                        when (result.reason) {
                            DeleteAccountFailure.Network -> DeleteError.Network
                            DeleteAccountFailure.LocalWipe -> DeleteError.LocalWipe
                            DeleteAccountFailure.Other -> DeleteError.Other
                        }
                    deleteState.update {
                        // Nothing is left to confirm or re-authenticate once the account is gone.
                        val step = if (error == DeleteError.LocalWipe) DeleteStep.Confirm else it.step
                        it.copy(step = step, working = false, error = error)
                    }
                }
            }
        }

        private fun AuthFailure.toDeleteError(usesPassword: Boolean): DeleteError =
            when (this) {
                // Only a password account can enter a wrong password; for Google it is another account.
                AuthFailure.InvalidCredentials -> if (usesPassword) DeleteError.WrongPassword else DeleteError.Other

                AuthFailure.Network -> DeleteError.Network

                else -> DeleteError.Other
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
