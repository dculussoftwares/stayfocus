@file:Suppress("MagicNumber")

package com.dculus.stayfocused.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

const val ACCESSIBILITY_DISCLOSURE_AGREE_TAG = "accessibility_disclosure_agree"
const val ACCESSIBILITY_DISCLOSURE_NO_TAG = "accessibility_disclosure_no"
const val ACCESSIBILITY_DISCLOSURE_PRIVACY_TAG = "accessibility_disclosure_privacy"

/** The words of the Play prominent disclosure. The Kids app supplies its own copy. */
data class AccessibilityDisclosureCopy(
    val eyebrow: String,
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
 * Play-required prominent disclosure shown right before the Accessibility settings screen.
 * Only [onAgree] may lead to Settings; [onDismiss] ("No thanks", back, scrim) must do nothing else.
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
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    SfBottomSheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonoLabel(copy.eyebrow, color = colors.accent)
            Text(
                copy.title,
                style = type.displayM.copy(fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp),
                color = colors.text,
            )
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(colors.hairline06),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                DisclosureSection(copy.readsLabel, copy.reads)
                DisclosureSection(copy.whyLabel, copy.why)
                DisclosureSection(copy.neverLabel, copy.never)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(copy.footer, style = type.bodyS.copy(fontSize = 12.sp), color = colors.secondary)
                Text(
                    copy.privacyPolicy,
                    style = type.bodyS.copy(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                    color = colors.text,
                    modifier =
                        Modifier
                            .testTag(ACCESSIBILITY_DISCLOSURE_PRIVACY_TAG)
                            .clickable(role = Role.Button, onClick = onPrivacyPolicy),
                )
            }
            PrimaryButton(
                text = copy.agree,
                onClick = onAgree,
                modifier = Modifier.testTag(ACCESSIBILITY_DISCLOSURE_AGREE_TAG),
            )
            GhostButton(
                text = copy.noThanks,
                onClick = onDismiss,
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
    val colors = StayFocusedTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MonoLabel(label, color = colors.secondary)
        Text(
            body,
            style = StayFocusedTheme.type.bodyS.copy(fontSize = 13.5.sp, lineHeight = 19.5.sp),
            color = colors.text,
        )
    }
}
