@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

const val ACCESSIBILITY_DISCLOSURE_AGREE_TAG = "accessibility_disclosure_agree"
const val ACCESSIBILITY_DISCLOSURE_NO_TAG = "accessibility_disclosure_no"
const val ACCESSIBILITY_DISCLOSURE_PRIVACY_TAG = "accessibility_disclosure_privacy"

/** The words of the Play prominent disclosure. The Kids app supplies its own copy. */
data class AccessibilityDisclosureCopy(
    val title: String,
    val readsLabel: String,
    val reads: String,
    val whyLabel: String,
    val why: String,
    val neverLabel: String,
    val never: String,
    val footer: String,
    val privacyPolicy: String,
    val agree: String,
    val noThanks: String,
)

/**
 * Play-required prominent disclosure shown right before the Accessibility settings screen, as a v5 bottom sheet.
 * Only [onAgree] may lead to Settings; [onDismiss] ("No thanks", back, scrim, drag down) must do nothing else.
 */
@Composable
fun AccessibilityDisclosure(
    visible: Boolean,
    copy: AccessibilityDisclosureCopy,
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StayFocusedTheme {
        val colors = StayFocusedTheme.colors
        val type = StayFocusedTheme.type
        V5BottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier, contentSpacing = 12.dp) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(copy.title, style = type.heading, color = colors.ink)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    DisclosureSection(copy.readsLabel, copy.reads)
                    DisclosureSection(copy.whyLabel, copy.why)
                    DisclosureSection(copy.neverLabel, copy.never)
                }
                Text(copy.footer, style = type.body.copy(fontSize = 13.sp), color = colors.textSecondary)
                Text(
                    copy.privacyPolicy,
                    style = type.body.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
                    color = colors.ink,
                    modifier =
                        Modifier
                            .testTag(ACCESSIBILITY_DISCLOSURE_PRIVACY_TAG)
                            .clickable(role = Role.Button, onClick = onPrivacyPolicy)
                            .padding(vertical = 4.dp),
                )
            }
            PillButton(
                text = copy.agree,
                onClick = onAgree,
                variant = PillButtonVariant.Primary,
                size = PillButtonSize.Sheet,
                modifier = Modifier.testTag(ACCESSIBILITY_DISCLOSURE_AGREE_TAG),
            )
            PillButton(
                text = copy.noThanks,
                onClick = onDismiss,
                variant = PillButtonVariant.Text,
                size = PillButtonSize.Medium,
                modifier = Modifier.testTag(ACCESSIBILITY_DISCLOSURE_NO_TAG),
            )
        }
    }
}

@Composable
private fun DisclosureSection(
    label: String,
    body: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = StayFocusedTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = StayFocusedTheme.colors.ink,
        )
        Text(body, style = StayFocusedTheme.type.body, color = StayFocusedTheme.colors.ink)
    }
}
