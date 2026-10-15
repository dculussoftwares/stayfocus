package com.dculus.stayfocused.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.blocking.engine.EngineHealth
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    ) : ViewModel() {
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

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
