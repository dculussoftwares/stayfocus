@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.devices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dculus.stayfocused.core.ui.art.ScanFrame
import com.dculus.stayfocused.core.ui.components.PillButton
import com.dculus.stayfocused.core.ui.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.components.RoundIconButton
import com.dculus.stayfocused.core.ui.icons.V5Icons
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import com.dculus.stayfocused.core.ui.theme.V5Gradients
import com.dculus.stayfocused.core.ui.theme.V5Screen
import com.dculus.stayfocused.core.ui.theme.v5NavigationBarPadding
import com.dculus.stayfocused.core.ui.theme.v5StatusBarPadding
import timber.log.Timber

internal const val LINK_SCAN_VIEWFINDER_TAG = "link_scan_viewfinder"
internal const val LINK_SCAN_ALLOW_TAG = "link_scan_allow"
internal const val LINK_SCAN_SETTINGS_TAG = "link_scan_settings"

/** How long the same invalid code is ignored after its toast, so a code held in view does not spam. */
internal const val INVALID_CODE_COOLDOWN_MS = 3_000L

/** What the scan screen can show, from the camera permission. */
internal enum class CameraAccess {
    /** Not asked yet: show the rationale first. */
    NeedsRationale,
    Granted,

    /** The person said no (or turned it off). Scanning cannot work until they allow it in Settings. */
    Denied,

    /** Permission is on but the camera can't be started (none, or in use by another app). */
    Unavailable,
}

@Composable
internal fun LinkScanRoute(
    onBack: () -> Unit,
    onToken: (token: String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val invalidMessage = stringResource(R.string.link_scan_invalid)
    var access by rememberSaveable {
        mutableStateOf(if (context.hasCameraPermission()) CameraAccess.Granted else CameraAccess.NeedsRationale)
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            access = if (granted) CameraAccess.Granted else CameraAccess.Denied
        }
    // Coming back from Settings: pick up a permission that was just turned on (or off).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val granted = context.hasCameraPermission()
                    if (granted && access != CameraAccess.Granted) {
                        access = CameraAccess.Granted
                    } else if (!granted && access == CameraAccess.Granted) {
                        access = CameraAccess.Denied
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currentOnToken by rememberUpdatedState(onToken)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val handler =
        remember {
            ScanHandler(
                onToken = { currentOnToken(it) },
                onInvalid = { currentOnMessage(invalidMessage) },
            )
        }
    LinkScanScreen(
        access = access,
        onBack = onBack,
        onAllow = { launcher.launch(android.Manifest.permission.CAMERA) },
        onOpenSettings = { context.openAppSettings() },
        onCodes = handler::onCodes,
        onCameraUnavailable = { access = CameraAccess.Unavailable },
    )
}

/**
 * Turns decoded QR strings into at most one [onToken] call, and rate-limits [onInvalid] for the same wrong code.
 * Not thread-safe: use from the main thread.
 */
internal class ScanHandler(
    private val onToken: (String) -> Unit,
    private val onInvalid: () -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var done = false
    private val lastInvalidAt = mutableMapOf<String, Long>()

    /** The strings decoded from one frame: a valid link wins over other codes in view. */
    fun onCodes(raws: List<String>) {
        if (done) return
        val token = raws.firstNotNullOfOrNull(LinkCodeParser::parseToken)
        if (token != null) {
            done = true
            onToken(token)
        } else {
            raws.firstOrNull()?.let(::reportInvalid)
        }
    }

    private fun reportInvalid(raw: String) {
        val at = now()
        lastInvalidAt.values.removeAll { at - it >= INVALID_CODE_COOLDOWN_MS }
        if (lastInvalidAt.containsKey(raw)) return
        lastInvalidAt[raw] = at
        onInvalid()
    }
}

@Composable
internal fun LinkScanScreen(
    access: CameraAccess,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onCodes: (List<String>) -> Unit,
    onCameraUnavailable: () -> Unit,
    preview: @Composable (Modifier) -> Unit = { QrScannerPreview(onCodes, onCameraUnavailable, it) },
) {
    V5Screen(darkContent = true, background = V5Gradients.scan.adaptive) {
        val colors = StayFocusedTheme.colors
        Column(
            Modifier
                .fillMaxSize()
                // The prototype's 56 dp top padding includes its 46 dp fake status bar.
                .v5StatusBarPadding(extra = 10.dp)
                .v5NavigationBarPadding(extra = 40.dp)
                .padding(horizontal = StayFocusedTheme.spacing.screenOnboarding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoundIconButton(
                icon = V5Icons.back,
                contentDescription = stringResource(R.string.link_back),
                onClick = onBack,
                dark = true,
                modifier = Modifier.align(Alignment.Start).testTag(LINK_BACK_TAG),
            )
            when (access) {
                CameraAccess.Granted -> {
                    Text(
                        stringResource(R.string.link_scan_title),
                        style = StayFocusedTheme.type.title.copy(fontSize = 30.sp),
                        color = colors.onGradient,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Text(
                        stringResource(R.string.link_scan_body),
                        style = StayFocusedTheme.type.body.copy(lineHeight = 21.75.sp),
                        color = colors.onInkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).widthIn(max = 290.dp),
                    )
                    Box(Modifier.padding(top = 40.dp).testTag(LINK_SCAN_VIEWFINDER_TAG).size(260.dp)) {
                        preview(Modifier.fillMaxSize().padding(14.dp).clip(RoundedCornerShape(22.dp)))
                        ScanFrame()
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.link_scan_looking),
                        style = StayFocusedTheme.type.body.copy(fontSize = 14.sp),
                        color = colors.onInkMuted,
                    )
                }

                CameraAccess.NeedsRationale -> {
                    PermissionMessage(
                        title = stringResource(R.string.link_camera_title),
                        body = stringResource(R.string.link_camera_rationale),
                        cta = stringResource(R.string.link_camera_allow),
                        ctaTag = LINK_SCAN_ALLOW_TAG,
                        onCta = onAllow,
                    )
                }

                CameraAccess.Unavailable -> {
                    PermissionMessage(
                        title = stringResource(R.string.link_camera_unavailable_title),
                        body = stringResource(R.string.link_camera_unavailable_body),
                        cta = null,
                        ctaTag = "",
                        onCta = {},
                    )
                }

                CameraAccess.Denied -> {
                    PermissionMessage(
                        title = stringResource(R.string.link_camera_denied_title),
                        body = stringResource(R.string.link_camera_denied_body),
                        cta = stringResource(R.string.link_camera_settings),
                        ctaTag = LINK_SCAN_SETTINGS_TAG,
                        onCta = onOpenSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PermissionMessage(
    title: String,
    body: String,
    cta: String?,
    ctaTag: String,
    onCta: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = StayFocusedTheme.type.title.copy(fontSize = 30.sp),
            color = colors.onGradient,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = StayFocusedTheme.type.body.copy(lineHeight = 21.75.sp),
            color = colors.onInkMuted,
            textAlign = TextAlign.Center,
        )
        if (cta != null) {
            PillButton(
                text = cta,
                onClick = onCta,
                variant = PillButtonVariant.White,
                modifier = Modifier.padding(top = 12.dp).testTag(ctaTag),
            )
        }
    }
}

private fun Context.openAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No settings screen for app details")
    }
}
