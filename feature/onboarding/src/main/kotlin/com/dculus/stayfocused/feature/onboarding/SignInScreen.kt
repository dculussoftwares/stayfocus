@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.ui.components.GhostButton
import com.dculus.stayfocused.core.ui.components.MonoLabel
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfTextField
import com.dculus.stayfocused.core.ui.components.SfToastHost
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val SIGN_IN_GOOGLE_TAG = "sign_in_google"
internal const val SIGN_IN_EMAIL_TAG = "sign_in_email"
internal const val SIGN_IN_PASSWORD_TAG = "sign_in_password"
internal const val SIGN_IN_SUBMIT_TAG = "sign_in_submit"
internal const val SIGN_IN_TOGGLE_TAG = "sign_in_toggle"
internal const val SIGN_IN_FORGOT_TAG = "sign_in_forgot"
internal const val SIGN_IN_SKIP_TAG = "sign_in_skip"

@Composable
internal fun SignInRoute(
    onContinue: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val toast = remember(scope) { ToastController(scope) }
    val currentOnContinue by rememberUpdatedState(onContinue)
    LaunchedEffect(viewModel, toast, resources) {
        viewModel.events.collect { event ->
            when (event) {
                SignInEvent.Done -> currentOnContinue()
                is SignInEvent.ResetSent -> toast.show(resources.getString(R.string.auth_reset_sent, event.email))
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        SignInScreen(
            state = state,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSubmit = viewModel::submit,
            onToggleMode = viewModel::toggleMode,
            onGoogle = viewModel::continueWithGoogle,
            onForgotPassword = viewModel::forgotPassword,
            onSkip = viewModel::skip,
        )
        SfToastHost(toast, bottomPadding = 24.dp)
    }
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val signIn = state.mode == AuthMode.SignIn
    val error = state.error?.let { stringResource(it.messageRes()) }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (signIn) R.string.sign_in_title else R.string.auth_create_title),
                style = StayFocusedTheme.type.displayL,
                color = colors.text,
            )
            Text(
                stringResource(R.string.auth_subtitle),
                style = StayFocusedTheme.type.body,
                color = colors.secondary,
            )
        }
        OutlineButton(
            text = stringResource(R.string.auth_google),
            onClick = onGoogle,
            enabled = !state.busy,
            leadingIcon = { GoogleMark() },
            modifier = Modifier.testTag(SIGN_IN_GOOGLE_TAG),
        )
        OrDivider()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SfTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.auth_email_label),
                placeholder = stringResource(R.string.auth_email_placeholder),
                error = error.takeIf { state.error?.onEmailField == true },
                keyboardType = KeyboardType.Email,
                enabled = !state.busy,
                modifier = Modifier.testTag(SIGN_IN_EMAIL_TAG),
            )
            SfTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_password_label),
                placeholder = stringResource(R.string.auth_password_placeholder),
                error = error.takeIf { state.error?.onEmailField == false },
                isPassword = true,
                enabled = !state.busy,
                modifier = Modifier.testTag(SIGN_IN_PASSWORD_TAG),
            )
            if (signIn) {
                GhostButton(
                    text = stringResource(R.string.auth_forgot),
                    onClick = onForgotPassword,
                    enabled = !state.busy,
                    modifier = Modifier.testTag(SIGN_IN_FORGOT_TAG),
                )
            }
        }
        PrimaryButton(
            text = stringResource(if (signIn) R.string.sign_in_title else R.string.auth_create_title),
            onClick = onSubmit,
            loading = state.busy,
            modifier = Modifier.padding(top = 8.dp).testTag(SIGN_IN_SUBMIT_TAG),
        )
        GhostButton(
            text = stringResource(if (signIn) R.string.auth_switch_to_create else R.string.auth_switch_to_sign_in),
            onClick = onToggleMode,
            enabled = !state.busy,
            modifier = Modifier.testTag(SIGN_IN_TOGGLE_TAG),
        )
        GhostButton(
            text = stringResource(R.string.auth_skip),
            onClick = onSkip,
            enabled = !state.busy,
            modifier = Modifier.testTag(SIGN_IN_SKIP_TAG),
        )
    }
}

private fun AuthError.messageRes(): Int =
    when (this) {
        AuthError.InvalidEmail -> R.string.auth_error_email
        AuthError.ShortPassword -> R.string.auth_error_password
        AuthError.EmailFirst -> R.string.auth_error_forgot
        AuthError.WrongCredentials -> R.string.auth_error_wrong_credentials
        AuthError.EmailInUse -> R.string.auth_error_email_in_use
        AuthError.Network -> R.string.auth_error_network
        AuthError.Other -> R.string.auth_error_other
    }

/** The prototype's stand-in for the Google logo: an off-white disc with a "G". */
@Composable
private fun GoogleMark() {
    val colors = StayFocusedTheme.colors
    Box(Modifier.size(22.dp).background(colors.text, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            "G",
            style = StayFocusedTheme.type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = colors.onAccent,
        )
    }
}

@Composable
private fun OrDivider() {
    val colors = StayFocusedTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.hairline08))
        MonoLabel(stringResource(R.string.auth_or_email), color = colors.tertiary)
        Box(Modifier.weight(1f).height(1.dp).background(colors.hairline08))
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun SignInScreenPreview() {
    StayFocusedTheme {
        SignInScreen(SignInUiState(), {}, {}, {}, {}, {}, {}, {})
    }
}
