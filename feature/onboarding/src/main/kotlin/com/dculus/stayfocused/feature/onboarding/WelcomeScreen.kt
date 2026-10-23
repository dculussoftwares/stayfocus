@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import com.dculus.stayfocused.core.ui.v5.theme.glow
import com.dculus.stayfocused.core.ui.v5.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.v5.theme.v5StatusBarPadding

internal const val WELCOME_GET_STARTED_TAG = "welcome_get_started"
internal const val WELCOME_CHILD_PHONE_TAG = "welcome_child_phone"
internal const val WELCOME_OPEN_KIDS_TAG = "welcome_open_kids"

@Composable
internal fun WelcomeRoute(onContinue: () -> Unit) {
    val context = LocalContext.current
    WelcomeScreen(onContinue = onContinue, onOpenKidsListing = { openKidsListing(context) })
}

@Composable
internal fun WelcomeScreen(
    onContinue: () -> Unit,
    onOpenKidsListing: () -> Unit,
    modifier: Modifier = Modifier,
    initialSheetVisible: Boolean = false,
) {
    var sheetVisible by rememberSaveable { mutableStateOf(initialSheetVisible) }
    V5Screen(darkContent = true, modifier = modifier) {
        Box(Modifier.matchParentSize().glow(V5Gradients.welcome))
        // Spread over the screen when it fits; scrolls on short screens or with large system fonts.
        BoxWithConstraints(Modifier.matchParentSize()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .v5StatusBarPadding(extra = 24.dp)
                    .v5NavigationBarPadding(extra = 40.dp)
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.welcome_brand),
                    style = V5Theme.type.label.copy(fontSize = 16.sp, letterSpacing = 0.02.sp),
                    color = V5Theme.colors.onGradient,
                )
                WelcomeCopy(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        text = stringResource(R.string.welcome_get_started),
                        onClick = onContinue,
                        variant = PillButtonVariant.White,
                        size = PillButtonSize.Large,
                        modifier = Modifier.testTag(WELCOME_GET_STARTED_TAG),
                    )
                    PillButton(
                        text = stringResource(R.string.welcome_child_phone),
                        onClick = { sheetVisible = true },
                        variant = PillButtonVariant.Text,
                        size = PillButtonSize.Medium,
                        underline = true,
                        textColor = V5Theme.colors.onGradient,
                        modifier = Modifier.testTag(WELCOME_CHILD_PHONE_TAG),
                    )
                }
            }
        }
    }
    V5BottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
        ChildPhoneSheetContent(onOpenKidsListing)
    }
}

@Composable
private fun WelcomeCopy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.welcome_headline),
            style = V5Theme.type.hero,
            color = V5Theme.colors.onGradient,
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = V5Theme.type.bodyL.copy(fontWeight = FontWeight.Medium, lineHeight = 25.sp, fontSize = 18.sp),
            color = V5Theme.colors.onGradient,
            modifier = Modifier.widthIn(max = 290.dp),
        )
    }
}

@Composable
private fun ChildPhoneSheetContent(onOpenKidsListing: () -> Unit) {
    val colors = V5Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.welcome_sheet_title),
            style = V5Theme.type.heading,
            color = colors.ink,
        )
        Text(
            stringResource(R.string.welcome_sheet_body),
            style = V5Theme.type.body,
            color = colors.textSecondary,
        )
        PillButton(
            text = stringResource(R.string.welcome_sheet_open_play),
            onClick = onOpenKidsListing,
            variant = PillButtonVariant.Primary,
            size = PillButtonSize.Sheet,
            modifier = Modifier.testTag(WELCOME_OPEN_KIDS_TAG),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
internal fun WelcomeScreenPreview() {
    WelcomeScreen(onContinue = {}, onOpenKidsListing = {})
}
