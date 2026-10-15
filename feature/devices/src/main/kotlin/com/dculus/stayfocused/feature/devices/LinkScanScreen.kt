@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.devices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import timber.log.Timber

internal const val LINK_SCAN_VIEWFINDER_TAG = "link_scan_viewfinder"
internal const val LINK_SCAN_ALLOW_TAG = "link_scan_allow"
internal const val LINK_SCAN_SETTINGS_TAG = "link_scan_settings"

/** How long the same invalid code is ignored after its toast, so a code held in view does not spam. */
internal const val INVALID_CODE_COOLDOWN_MS = 3_000L

private val CameraBlack = Color.Black
private val PromptText = Color(0xFFC9CDC0)

/** What the scan screen can show, from the camera permission. */
internal enum class CameraAccess {
    /** Not asked yet: show the rationale first. */
    NeedsRationale,
    Granted,

    /** The person said no (or turned it off). Scanning cannot work until they allow it in Settings. */
    Denied,
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
        onCode = handler::onCode,
    )
}

/**
 * Turns decoded QR strings into at most one [onToken] call, and rate-limits [onInvalid] for the same wrong code.
 * Not thread-safe: use from the main thread.
 */
internal class ScanHandler(
    private val onToken: (String) -> Unit,
    private val onInvalid: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var done = false
    private var lastInvalid: String? = null
    private var lastInvalidAt = 0L

    fun onCode(raw: String) {
        if (done) return
        val token = LinkCodeParser.parseToken(raw)
        if (token != null) {
            done = true
            onToken(token)
        } else {
            reportInvalid(raw)
        }
    }

    private fun reportInvalid(raw: String) {
        val at = now()
        if (raw == lastInvalid && at - lastInvalidAt < INVALID_CODE_COOLDOWN_MS) return
        lastInvalid = raw
        lastInvalidAt = at
        onInvalid()
    }
}

@Composable
internal fun LinkScanScreen(
    access: CameraAccess,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onCode: (String) -> Unit,
) {
    val type = StayFocusedTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .background(CameraBlack)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        LinkBackButton(onBack = onBack, light = true)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (access) {
                CameraAccess.Granted -> {
                    Viewfinder(onCode)
                    Text(
                        stringResource(R.string.link_scan_prompt),
                        style = type.bodyL,
                        color = PromptText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
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
private fun PermissionMessage(
    title: String,
    body: String,
    cta: String,
    ctaTag: String,
    onCta: () -> Unit,
) {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(
        Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = type.displayM, color = colors.text, textAlign = TextAlign.Center)
        Text(body, style = type.body, color = colors.secondary, textAlign = TextAlign.Center)
        PrimaryButton(text = cta, onClick = onCta, modifier = Modifier.fillMaxWidth().testTag(ctaTag))
    }
}

/** The 250 dp camera window with lime corner brackets. */
@Composable
private fun Viewfinder(onCode: (String) -> Unit) {
    val accent = StayFocusedTheme.colors.accent
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .testTag(LINK_SCAN_VIEWFINDER_TAG)
            .size(250.dp)
            .drawBehind {
                val arm = 46.dp.toPx()
                val width = 4.dp.toPx()
                val radius = 18.dp.toPx()
                val stroke = Stroke(width = width)
                val half = width / 2

                // Four corner brackets, each an L with one rounded outer corner.
                fun bracket(
                    x: Float,
                    y: Float,
                    dx: Float,
                    dy: Float,
                ) {
                    val path =
                        Path().apply {
                            moveTo(x + dx * arm, y + dy * half)
                            lineTo(x + dx * radius, y + dy * half)
                            quadraticTo(x + dx * half, y + dy * half, x + dx * half, y + dy * radius)
                            lineTo(x + dx * half, y + dy * arm)
                        }
                    drawPath(path, accent, style = stroke)
                }
                bracket(0f, 0f, 1f, 1f)
                bracket(size.width, 0f, -1f, 1f)
                bracket(0f, size.height, 1f, -1f)
                bracket(size.width, size.height, -1f, -1f)
            },
    ) {
        QrScannerPreview(
            onCode = onCode,
            modifier = Modifier.fillMaxSize().padding(22.dp).clip(shape),
        )
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
