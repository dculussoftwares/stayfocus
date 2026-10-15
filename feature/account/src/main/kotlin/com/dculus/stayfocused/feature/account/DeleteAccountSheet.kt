package com.dculus.stayfocused.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.components.SfTextField
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
    SfBottomSheet(visible = state.step != DeleteStep.Hidden, onDismiss = onCancel) {
        val colors = StayFocusedTheme.colors
        val type = StayFocusedTheme.type
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step == DeleteStep.Reauth) {
                Text(stringResource(R.string.account_reauth_title), style = type.displayM, color = colors.text)
                Text(stringResource(R.string.account_reauth_body), style = type.body, color = colors.secondary)
                if (state.usesPassword) {
                    var password by rememberSaveable { mutableStateOf("") }
                    SfTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.account_reauth_password),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        enabled = !state.working,
                        error = state.error?.takeIf { it == DeleteError.WrongPassword }?.let { errorText(it) },
                        modifier = Modifier.testTag(DELETE_PASSWORD_TAG),
                    )
                    OtherError(state)
                    PrimaryButton(
                        text = stringResource(R.string.account_reauth_continue),
                        onClick = { onPassword(password) },
                        enabled = password.isNotEmpty() && !state.working,
                        loading = state.working,
                        modifier = Modifier.fillMaxWidth().testTag(DELETE_REAUTH_TAG),
                    )
                } else {
                    OtherError(state)
                    PrimaryButton(
                        text = stringResource(R.string.account_reauth_google),
                        onClick = onGoogle,
                        enabled = !state.working,
                        loading = state.working,
                        modifier = Modifier.fillMaxWidth().testTag(DELETE_GOOGLE_TAG),
                    )
                }
            } else {
                Text(stringResource(R.string.account_delete_title), style = type.displayM, color = colors.text)
                Text(stringResource(R.string.account_delete_body), style = type.body, color = colors.secondary)
                OtherError(state)
                // The account is already gone when the local erase failed: nothing is left to confirm.
                if (state.error != DeleteError.LocalWipe) {
                    PrimaryButton(
                        text = stringResource(R.string.account_delete_confirm),
                        onClick = onConfirm,
                        enabled = !state.working,
                        loading = state.working,
                        modifier = Modifier.fillMaxWidth().testTag(DELETE_CONFIRM_TAG),
                    )
                }
            }
            if (state.step == DeleteStep.Reauth) {
                OutlineButton(
                    text = stringResource(R.string.account_delete_cancel),
                    onClick = onCancel,
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth().testTag(DELETE_CANCEL_TAG),
                )
            } else {
                GhostButton(
                    text = stringResource(R.string.account_delete_cancel),
                    onClick = onCancel,
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth().testTag(DELETE_CANCEL_TAG),
                )
            }
        }
    }
}

@Composable
private fun OtherError(state: DeleteUiState) {
    val error = state.error ?: return
    if (error == DeleteError.WrongPassword && state.usesPassword) return
    Text(errorText(error), style = StayFocusedTheme.type.bodyS, color = StayFocusedTheme.colors.alertText)
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
