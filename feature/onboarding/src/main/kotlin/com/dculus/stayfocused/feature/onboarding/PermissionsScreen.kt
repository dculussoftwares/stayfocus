@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.blocking.permissions.PermissionIntents
import com.dculus.stayfocused.core.model.Permissions
import com.dculus.stayfocused.core.ui.components.ACCESSIBILITY_DISCLOSURE_AGREE_TAG
import com.dculus.stayfocused.core.ui.components.ACCESSIBILITY_DISCLOSURE_NO_TAG
import com.dculus.stayfocused.core.ui.components.AccessibilityDisclosure
import com.dculus.stayfocused.core.ui.components.AccessibilityDisclosureCopy
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonSize
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.V5BottomSheet
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

internal const val PERMS_CONTINUE_TAG = "perms_continue"
internal const val PERMS_SKIP_TAG = "perms_skip"
internal const val PERMS_RESTRICTED_APP_INFO_TAG = "perms_restricted_app_info"
internal const val PERMS_RESTRICTED_ACCESSIBILITY_TAG = "perms_restricted_accessibility"
internal const val PERMS_DISCLOSURE_AGREE_TAG = ACCESSIBILITY_DISCLOSURE_AGREE_TAG
internal const val PERMS_DISCLOSURE_NO_TAG = ACCESSIBILITY_DISCLOSURE_NO_TAG
internal const val PRIVACY_URL = "https://dculussoftwares.github.io/stayfocus/privacy.html"

internal fun permsAllowTag(row: PermissionRow) = "perms_allow_${row.tag}"

/**
 * The words that differ between hosts (the phone app and the Kids app); everything else is shared.
 * [disclosureReads], [disclosureWhy] and [disclosureNever] are the three body paragraphs of the Play disclosure.
 */
data class PermissionsCopy(
    @StringRes val headline1: Int = R.string.perms_headline_1,
    @StringRes val headline2: Int = R.string.perms_headline_2,
    @StringRes val body: Int = R.string.perms_body,
    @StringRes val disclosureTitle: Int = R.string.perms_disclosure_title,
    @StringRes val disclosureReads: Int = R.string.perms_disclosure_reads,
    @StringRes val disclosureWhy: Int = R.string.perms_disclosure_why,
    @StringRes val disclosureNever: Int = R.string.perms_disclosure_never,
)

/**
 * The system check. [onFinish] gets the toast the host shows on the next screen ("All set, {first name}" with all
 * four on, "Blocking needs all 4" when leaving with fewer) and whether all four are on. [copy] swaps the headline and
 * disclosure words; [footer] is extra content under the four cards (the Kids app puts battery guidance there).
 */
@Composable
fun PermissionsRoute(
    onFinish: (message: String, allGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    copy: PermissionsCopy = PermissionsCopy(),
    footer: (@Composable () -> Unit)? = null,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var agreeJob by remember { mutableStateOf<Job?>(null) }
    val consentFailed = stringResource(R.string.perms_disclosure_save_failed)
    var disclosureVisible by rememberSaveable { mutableStateOf(false) }
    var restrictedVisible by rememberSaveable { mutableStateOf(false) }
    val notificationRuntime = PermissionIntents.notificationRuntimePermission()
    // The result needs no handling: the repository re-reads when the app resumes after the dialog closes.
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val incomplete = stringResource(R.string.perms_toast_incomplete)
    val firstName by viewModel.firstName.collectAsStateWithLifecycle()
    val allSet =
        firstName?.let { stringResource(R.string.perms_toast_all_set, it) }
            ?: stringResource(R.string.perms_toast_all_set_anonymous)
    PermissionsScreen(
        permissions = permissions,
        modifier = modifier,
        copy = copy,
        footer = footer,
        onAllow = { row ->
            when (row) {
                PermissionRow.Usage -> {
                    context.open(PermissionIntents.usageAccess())
                }

                PermissionRow.Overlay -> {
                    context.open(PermissionIntents.overlay(context.packageName))
                }

                // Never straight to Settings: the disclosure comes first.
                PermissionRow.Accessibility -> {
                    disclosureVisible = true
                }

                PermissionRow.Notifications -> {
                    when (viewModel.nextNotificationStep(notificationRuntime != null)) {
                        NotificationStep.RequestRuntime -> {
                            notificationRuntime?.let(requestNotifications::launch)
                        }

                        NotificationStep.OpenSettings -> {
                            context.open(PermissionIntents.notificationSettings(context.packageName))
                        }
                    }
                }
            }
        },
        onContinue = {
            val all = permissions.grantedCount() == PermissionRow.entries.size
            onFinish(if (all) allSet else incomplete, all)
        },
    )
    PhoneAccessibilityDisclosure(
        visible = disclosureVisible,
        words = copy,
        onAgree = {
            // One agreement at a time; "No thanks" cancels a pending one so Settings never opens after it.
            if (agreeJob?.isActive != true) {
                agreeJob =
                    scope.launch {
                        if (viewModel.recordAccessibilityConsent()) {
                            disclosureVisible = false
                            if (viewModel.needsRestrictedSettingsGuidance()) {
                                restrictedVisible = true
                            } else {
                                context.open(PermissionIntents.accessibility())
                            }
                        } else {
                            Toast.makeText(context, consentFailed, Toast.LENGTH_LONG).show()
                        }
                    }
            }
        },
        onDismiss = {
            agreeJob?.cancel()
            disclosureVisible = false
        },
        onPrivacyPolicy = { context.open(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
    )
    RestrictedSettingsSheet(
        visible = restrictedVisible,
        onDismiss = { restrictedVisible = false },
        onOpenAppInfo = {
            restrictedVisible = false
            context.open(PermissionIntents.appInfo(context.packageName))
        },
        onOpenAccessibility = {
            restrictedVisible = false
            context.open(PermissionIntents.accessibility())
        },
    )
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No activity for %s", intent.action)
    }
}

@Composable
internal fun PermissionsScreen(
    permissions: Permissions,
    onAllow: (PermissionRow) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    copy: PermissionsCopy = PermissionsCopy(),
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val granted = permissions.grantedCount()
    val all = granted == PermissionRow.entries.size
    V5Screen(darkContent = false, modifier = modifier) {
        // Fills the screen when it fits; scrolls on short screens or at large font scales.
        BoxWithConstraints(Modifier.matchParentSize()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .v5StatusBarPadding(extra = 18.dp)
                    .v5NavigationBarPadding(extra = 34.dp)
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(copy.headline1) + "\n" + stringResource(copy.headline2),
                    style = type.headline.copy(fontSize = 36.sp, lineHeight = 37.8.sp),
                    color = colors.ink,
                )
                Text(
                    stringResource(copy.body),
                    style = type.body.copy(lineHeight = 21.75.sp),
                    color = colors.textSecondary,
                )
                // Decorative: the bottom button says "{n} of 4 allowed".
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PermissionRow.entries.forEach { row ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (row.isGranted(permissions)) colors.success else colors.hairline),
                        )
                    }
                }
                PermissionRow.entries.forEach { row ->
                    PermissionCard(row, row.isGranted(permissions)) { onAllow(row) }
                }
                footer?.invoke()
                Box(Modifier.weight(1f))
                PillButton(
                    text =
                        if (all) {
                            stringResource(R.string.perms_start)
                        } else {
                            stringResource(R.string.perms_count_button, granted)
                        },
                    onClick = onContinue,
                    enabled = all,
                    modifier = Modifier.testTag(PERMS_CONTINUE_TAG),
                )
                if (!all) {
                    PillButton(
                        text = stringResource(R.string.perms_continue_now),
                        onClick = onContinue,
                        variant = PillButtonVariant.Text,
                        size = PillButtonSize.Medium,
                        modifier = Modifier.testTag(PERMS_SKIP_TAG),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    row: PermissionRow,
    on: Boolean,
    onAllow: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    val title = stringResource(row.title)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(StayFocusedTheme.shapes.card)
            .background(colors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = type.bodyL, color = colors.ink)
            Text(
                stringResource(row.why),
                style = type.body.copy(fontSize = 14.sp, lineHeight = 18.9.sp),
                color = colors.textSecondary,
            )
        }
        if (on) {
            val allowed = stringResource(R.string.perms_allowed_cd, title)
            Box(
                Modifier
                    .testTag(permsAllowTag(row))
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.success)
                    .semantics { contentDescription = allowed },
                contentAlignment = Alignment.Center,
            ) {
                Icon(V5Icons.check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        } else {
            val allowDescription = stringResource(R.string.perms_allow_cd, title)
            PillButton(
                text = stringResource(R.string.perms_allow),
                onClick = onAllow,
                size = PillButtonSize.Small,
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .testTag(permsAllowTag(row))
                        .semantics { contentDescription = allowDescription },
            )
        }
    }
}

@Composable
internal fun PhoneAccessibilityDisclosure(
    visible: Boolean,
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    words: PermissionsCopy = PermissionsCopy(),
) {
    val copy =
        AccessibilityDisclosureCopy(
            title = stringResource(words.disclosureTitle),
            readsLabel = stringResource(R.string.perms_disclosure_reads_label),
            reads = stringResource(words.disclosureReads),
            whyLabel = stringResource(R.string.perms_disclosure_why_label),
            why = stringResource(words.disclosureWhy),
            neverLabel = stringResource(R.string.perms_disclosure_never_label),
            never = stringResource(words.disclosureNever),
            footer = stringResource(R.string.perms_disclosure_footer),
            privacyPolicy = stringResource(R.string.perms_disclosure_privacy),
            agree = stringResource(R.string.perms_disclosure_agree),
            noThanks = stringResource(R.string.perms_disclosure_no),
        )
    AccessibilityDisclosure(visible, copy, onAgree, onDismiss, onPrivacyPolicy)
}

/** Android 13+ "Allow restricted settings" guidance, shown after the disclosure and before Accessibility settings. */
@Composable
internal fun RestrictedSettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    StayFocusedTheme {
        V5BottomSheet(visible = visible, onDismiss = onDismiss) {
            Text(
                stringResource(R.string.perms_restricted_title),
                style = StayFocusedTheme.type.heading,
                color = StayFocusedTheme.colors.ink,
            )
            Text(
                stringResource(R.string.perms_restricted_body),
                style = StayFocusedTheme.type.body,
                color = StayFocusedTheme.colors.textSecondary,
            )
            PillButton(
                text = stringResource(R.string.perms_restricted_open_app_info),
                onClick = onOpenAppInfo,
                variant = PillButtonVariant.Primary,
                size = PillButtonSize.Sheet,
                modifier = Modifier.testTag(PERMS_RESTRICTED_APP_INFO_TAG),
            )
            PillButton(
                text = stringResource(R.string.perms_restricted_open_accessibility),
                onClick = onOpenAccessibility,
                variant = PillButtonVariant.Text,
                size = PillButtonSize.Medium,
                modifier = Modifier.testTag(PERMS_RESTRICTED_ACCESSIBILITY_TAG),
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun PermissionsScreenPreview() {
    PermissionsScreen(
        Permissions(usage = true, accessibility = false, overlay = true, notifications = false),
        {},
        {},
    )
}
