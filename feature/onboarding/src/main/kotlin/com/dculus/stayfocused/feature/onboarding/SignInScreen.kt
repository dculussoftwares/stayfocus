@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonSize
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.RoundIconButton
import com.dculus.stayfocused.core.ui.components.ToastController
import com.dculus.stayfocused.core.ui.components.V5TextField
import com.dculus.stayfocused.core.ui.components.V5ToastHost
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding

internal const val SIGN_IN_GOOGLE_TAG = "sign_in_google"
internal const val SIGN_IN_EMAIL_TAG = "sign_in_email"
internal const val SIGN_IN_PASSWORD_TAG = "sign_in_password"
internal const val SIGN_IN_SUBMIT_TAG = "sign_in_submit"
internal const val SIGN_IN_TOGGLE_TAG = "sign_in_toggle"
internal const val SIGN_IN_FORGOT_TAG = "sign_in_forgot"
internal const val SIGN_IN_SKIP_TAG = "sign_in_skip"
internal const val SIGN_IN_BACK_TAG = "sign_in_back"
internal const val SIGN_IN_ERROR_TAG = "sign_in_error"

@Composable
internal fun SignInRoute(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val resources = LocalResources.current
    val context = LocalContext.current
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
    V5Screen(darkContent = false) {
        SignInScreen(
            state = state,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSubmit = viewModel::submit,
            onToggleMode = viewModel::toggleMode,
            onGoogle = { viewModel.continueWithGoogle(context) },
            onForgotPassword = viewModel::forgotPassword,
            onSkip = viewModel::skip,
            onBack = onBack,
        )
        V5ToastHost(toast, bottomOffset = 24.dp)
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val signIn = state.mode == AuthMode.SignIn
    val error = state.error?.let { stringResource(it.messageRes()) }
    // Fills the screen when it fits and scrolls on short screens, with the keyboard up or at large font scales.
    BoxWithConstraints(modifier.fillMaxWidth().v5StatusBarPadding().imePadding()) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                .v5NavigationBarPadding(extra = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoundIconButton(
                icon = V5Icons.back,
                contentDescription = stringResource(R.string.auth_back),
                onClick = onBack,
                modifier = Modifier.testTag(SIGN_IN_BACK_TAG),
            )
            Text(
                stringResource(if (signIn) R.string.sign_in_title else R.string.auth_create_title),
                style = type.headline.copy(fontSize = 36.sp, lineHeight = 37.8.sp),
                color = colors.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                stringResource(if (signIn) R.string.auth_subtitle else R.string.auth_create_subtitle),
                style = type.body.copy(lineHeight = 21.75.sp),
                color = colors.textSecondary,
            )
            PillButton(
                text = stringResource(R.string.auth_google),
                onClick = onGoogle,
                variant = PillButtonVariant.White,
                size = PillButtonSize.Sheet,
                enabled = !state.busy,
                leadingIcon = { GoogleMark() },
                modifier = Modifier.padding(top = 8.dp).testTag(SIGN_IN_GOOGLE_TAG),
            )
            OrDivider()
            V5TextField(
                value = state.email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.auth_email_placeholder),
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.testTag(SIGN_IN_EMAIL_TAG),
            )
            V5TextField(
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.auth_password_placeholder),
                isPassword = true,
                enabled = !state.busy,
                modifier = Modifier.testTag(SIGN_IN_PASSWORD_TAG),
            )
            if (error != null) {
                Text(
                    error,
                    style = type.label,
                    color = colors.error,
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .testTag(SIGN_IN_ERROR_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (signIn) {
                PillButton(
                    text = stringResource(R.string.auth_forgot),
                    onClick = onForgotPassword,
                    variant = PillButtonVariant.Text,
                    size = PillButtonSize.Medium,
                    enabled = !state.busy,
                    textColor = colors.textSecondary,
                    modifier = Modifier.testTag(SIGN_IN_FORGOT_TAG),
                )
            }
            Box(Modifier.weight(1f, fill = true).heightIn(min = 8.dp))
            PillButton(
                text = stringResource(if (signIn) R.string.sign_in_title else R.string.auth_continue),
                onClick = onSubmit,
                variant = PillButtonVariant.Primary,
                size = PillButtonSize.Large,
                loading = state.busy,
                modifier = Modifier.testTag(SIGN_IN_SUBMIT_TAG),
            )
            ModeSwitch(signIn = signIn, enabled = !state.busy, onClick = onToggleMode)
            PillButton(
                text = stringResource(R.string.auth_skip),
                onClick = onSkip,
                variant = PillButtonVariant.Text,
                size = PillButtonSize.Medium,
                enabled = !state.busy,
                textColor = colors.textSecondary,
                modifier = Modifier.testTag(SIGN_IN_SKIP_TAG),
            )
        }
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
        AuthError.NoGoogleAccount -> R.string.auth_error_no_google_account
        AuthError.Other -> R.string.auth_error_other
    }

/** "Already have an account? **Sign in**" (14 sp, bold ink action); sign-in mode keeps the shipped line. */
@Composable
private fun ModeSwitch(
    signIn: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val text =
        if (signIn) {
            AnnotatedString(stringResource(R.string.auth_switch_to_create))
        } else {
            buildAnnotatedString {
                append(stringResource(R.string.auth_have_account))
                append(" ")
                withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.auth_switch_to_sign_in))
                }
            }
        }
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = StayFocusedTheme.spacing.minTouchTarget)
            .testTag(SIGN_IN_TOGGLE_TAG)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = StayFocusedTheme.type.body.copy(fontSize = 14.sp),
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The prototype's blue bold "G" in front of the Google button label. */
@Composable
private fun GoogleMark() {
    Text(
        stringResource(R.string.auth_google_glyph),
        style = StayFocusedTheme.type.bodyL.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        color = StayFocusedTheme.colors.google,
        // Decorative: the button label already says "Continue with Google".
        modifier = Modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun OrDivider() {
    val colors = StayFocusedTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.hairline))
        Text(
            stringResource(R.string.auth_or_email),
            style = StayFocusedTheme.type.body.copy(fontSize = 13.sp),
            color = colors.textSecondary,
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.hairline))
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
internal fun SignInScreenPreview() {
    V5Screen(darkContent = false) {
        SignInScreen(SignInUiState(mode = AuthMode.CreateAccount), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
