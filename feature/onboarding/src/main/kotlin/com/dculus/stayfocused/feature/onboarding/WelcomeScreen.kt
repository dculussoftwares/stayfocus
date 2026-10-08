@file:Suppress("MagicNumber")

package com.dculus.stayfocused.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dculus.stayfocused.core.ui.components.OutlineButton
import com.dculus.stayfocused.core.ui.components.PrimaryButton
import com.dculus.stayfocused.core.ui.components.SfBottomSheet
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme

internal const val WELCOME_GET_STARTED_TAG = "welcome_get_started"
internal const val WELCOME_CHILD_PHONE_TAG = "welcome_child_phone"
internal const val WELCOME_OPEN_KIDS_TAG = "welcome_open_kids"

private val UnlitTick = Color(0xFF2E322A)
private val RingTrack = Color(0xFF1F221C)
private const val VIEWBOX = 240f
private const val RING_RADIUS = 104f
private const val RING_STROKE = 10f
private const val RING_START_DEGREES = 150f
private const val RING_ARC_LENGTH = 230f
private const val RING_CIRCUMFERENCE = 654f

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
    val colors = StayFocusedTheme.colors
    var sheetVisible by rememberSaveable { mutableStateOf(initialSheetVisible) }
    Box(modifier.fillMaxSize().background(colors.background)) {
        TickRing(Modifier.align(Alignment.TopEnd).offset(x = 230.dp, y = (-60).dp))
        // Bottom-aligned when it fits; scrolls on short screens or with large system fonts.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 26.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                WelcomeCopy()
                Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GetStartedButton(onContinue)
                    OutlineButton(
                        text = stringResource(R.string.welcome_child_phone),
                        onClick = { sheetVisible = true },
                        modifier = Modifier.testTag(WELCOME_CHILD_PHONE_TAG),
                    )
                }
                Text(
                    text = stringResource(R.string.welcome_footer),
                    style = StayFocusedTheme.type.labelS.copy(fontSize = 10.5.sp, letterSpacing = 0.06.sp),
                    color = colors.tertiary,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // PROVISIONAL COPY: the child-phone sheet wording is not in the design handoff.
    SfBottomSheet(visible = sheetVisible, onDismiss = { sheetVisible = false }) {
        ChildPhoneSheetContent(onOpenKidsListing)
    }
}

@Composable
private fun WelcomeCopy() {
    val colors = StayFocusedTheme.colors
    val type = StayFocusedTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(
            text = stringResource(R.string.welcome_brand),
            style = type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.sp),
            color = colors.accent,
        )
        Text(
            text =
                buildAnnotatedString {
                    append(stringResource(R.string.welcome_headline_1))
                    append("\n")
                    withStyle(SpanStyle(color = colors.accent)) { append(stringResource(R.string.welcome_headline_2)) }
                },
            style = type.displayXL.copy(fontSize = 50.sp, lineHeight = 49.sp, letterSpacing = (-0.5).sp),
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = type.bodyL.copy(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
            color = colors.secondary,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

/** Lime call to action with the trailing arrow from the prototype (label left, arrow right). */
@Composable
private fun GetStartedButton(onClick: () -> Unit) {
    val colors = StayFocusedTheme.colors
    Row(
        Modifier
            .testTag(WELCOME_GET_STARTED_TAG)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(StayFocusedTheme.shapes.button)
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.welcome_get_started),
            style = StayFocusedTheme.type.bodyL.copy(fontWeight = FontWeight.ExtraBold),
            color = colors.onAccent,
        )
        Text("→", style = StayFocusedTheme.type.bodyL.copy(fontSize = 20.sp), color = colors.onAccent)
    }
}

@Composable
private fun ChildPhoneSheetContent(onOpenKidsListing: () -> Unit) {
    val colors = StayFocusedTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.welcome_sheet_title),
            style = StayFocusedTheme.type.title,
            color = colors.text,
        )
        Text(
            stringResource(R.string.welcome_sheet_body),
            style = StayFocusedTheme.type.body,
            color = colors.secondary,
        )
        PrimaryButton(
            text = stringResource(R.string.welcome_sheet_open_play),
            onClick = onOpenKidsListing,
            modifier = Modifier.padding(top = 8.dp).testTag(WELCOME_OPEN_KIDS_TAG),
        )
    }
}

/** The decorative gauge ring: track, lime arc and 60 ticks, drawn from the prototype's 240 unit viewBox. */
@Composable
private fun TickRing(modifier: Modifier = Modifier) {
    val lime = StayFocusedTheme.colors.accent
    Canvas(modifier.requiredSize(520.dp).alpha(0.9f)) {
        val unit = size.minDimension / VIEWBOX
        val centre = Offset(size.width / 2, size.height / 2)
        val radius = RING_RADIUS * unit
        val topLeft = Offset(centre.x - radius, centre.y - radius)
        val ring = Size(radius * 2, radius * 2)
        drawCircle(RingTrack, radius, centre, style = Stroke(RING_STROKE * unit))
        drawArc(
            color = lime,
            startAngle = RING_START_DEGREES,
            sweepAngle = RING_ARC_LENGTH / RING_CIRCUMFERENCE * 360f,
            useCenter = false,
            topLeft = topLeft,
            size = ring,
            style = Stroke(RING_STROKE * unit, cap = StrokeCap.Round),
        )
        welcomeTicks.forEach { t ->
            drawLine(
                color = if (t.lit) lime else UnlitTick,
                start = Offset(t.x1 * unit, t.y1 * unit),
                end = Offset(t.x2 * unit, t.y2 * unit),
                strokeWidth = (if (t.major) 2.5f else 1.5f) * unit,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
internal fun WelcomeScreenPreview() {
    StayFocusedTheme { WelcomeScreen(onContinue = {}, onOpenKidsListing = {}) }
}
