package com.dculus.stayfocused.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonSize
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.components.V5TextField
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val DELETE_CONFIRM_TAG = "account_delete_confirm"
internal const val DELETE_CANCEL_TAG = "account_delete_cancel"
internal const val DELETE_PASSWORD_TAG = "account_delete_password"
internal const val DELETE_REAUTH_TAG = "account_delete_reauth"
internal const val DELETE_GOOGLE_TAG = "account_delete_google"

/**
 * Confirm sheet for "Delete account and all data". When the sign-in is older than 5 minutes the same sheet asks for
 * the password (or the Google account) again before anything is deleted.
 */
@Composable
internal fun DeleteAccountSheet(
    state: DeleteUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onPassword: (String) -> Unit,
    onGoogle: () -> Unit,
) {
    V5BottomSheet(visible = state.step != DeleteStep.Hidden, onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step == DeleteStep.Reauth) {
                Text(
                    stringResource(R.string.account_reauth_title),
                    style = StayFocusedTheme.type.heading,
                    color = StayFocusedTheme.colors.ink,
                )
                Text(
                    stringResource(R.string.account_reauth_body),
                    style = StayFocusedTheme.type.body,
                    color = StayFocusedTheme.colors.textSecondary,
                )
                if (state.usesPassword) {
                    var password by rememberSaveable { mutableStateOf("") }
                    V5TextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = stringResource(R.string.account_reauth_password),
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !state.working,
                        error = state.error?.takeIf { it == DeleteError.WrongPassword }?.let { errorText(it) },
                        modifier = Modifier.testTag(DELETE_PASSWORD_TAG),
                    )
                    OtherError(state)
                    PillButton(
                        stringResource(R.string.account_reauth_continue),
                        {
                            onPassword(password)
                        },
                        Modifier.fillMaxWidth().testTag(DELETE_REAUTH_TAG),
                        enabled =
                            password.isNotEmpty() && !state.working,
                        loading = state.working,
                    )
                } else {
                    OtherError(state)
                    PillButton(
                        stringResource(R.string.account_reauth_google),
                        onGoogle,
                        Modifier.fillMaxWidth().testTag(DELETE_GOOGLE_TAG),
                        enabled = !state.working,
                        loading = state.working,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.account_delete_title),
                    style = StayFocusedTheme.type.heading,
                    color = StayFocusedTheme.colors.ink,
                )
                Text(
                    stringResource(R.string.account_delete_body),
                    style = StayFocusedTheme.type.body,
                    color = StayFocusedTheme.colors.textSecondary,
                )
                OtherError(state)
                // The account is already gone when the local erase failed: nothing is left to confirm.
                if (state.error != DeleteError.LocalWipe) {
                    PillButton(
                        stringResource(R.string.account_delete_confirm),
                        onConfirm,
                        Modifier.fillMaxWidth().testTag(DELETE_CONFIRM_TAG),
                        enabled = !state.working,
                        loading = state.working,
                    )
                }
            }
            if (state.step == DeleteStep.Reauth) {
                PillButton(
                    stringResource(R.string.account_delete_cancel),
                    onCancel,
                    Modifier.fillMaxWidth().testTag(DELETE_CANCEL_TAG),
                    PillButtonVariant.Secondary,
                    PillButtonSize.Sheet,
                    enabled = !state.working,
                )
            } else {
                PillButton(
                    stringResource(R.string.account_delete_cancel),
                    onCancel,
                    Modifier.fillMaxWidth().testTag(DELETE_CANCEL_TAG),
                    PillButtonVariant.Text,
                    PillButtonSize.Medium,
                    enabled = !state.working,
                )
            }
        }
    }
}

@Composable
private fun OtherError(state: DeleteUiState) {
    val error = state.error ?: return
    if (error == DeleteError.WrongPassword && state.usesPassword) return
    Text(errorText(error), style = StayFocusedTheme.type.body, color = StayFocusedTheme.colors.error)
}

@Composable
private fun errorText(error: DeleteError): String =
    stringResource(
        when (error) {
            DeleteError.WrongPassword -> R.string.account_delete_error_password
            DeleteError.Network -> R.string.account_delete_error_network
            DeleteError.Other -> R.string.account_delete_error_other
            DeleteError.LocalWipe -> R.string.account_delete_error_local
        },
    )
