@file:Suppress("MagicNumber", "TooManyFunctions")

package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.model.mmss
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.sync.RemoteBlock
import com.dculus.stayfocused.core.ui.components.IconTile
import com.dculus.stayfocused.core.ui.components.LedDot
import com.dculus.stayfocused.core.ui.components.SfToggle
import com.dculus.stayfocused.core.ui.components.TypeChip
import com.dculus.stayfocused.core.ui.components.daysLabel
import com.dculus.stayfocused.core.ui.icon.AppIcon
import com.dculus.stayfocused.core.ui.theme.StayFocusedTheme
import kotlinx.coroutines.delay
import java.time.Instant

internal const val REMOTE_LIST_TAG = "remote_list"
internal const val REMOTE_STATUS_TAG = "remote_status"
internal const val REMOTE_ALERT_TAG = "remote_alert"
internal const val REMOTE_REMIND_TAG = "remote_remind"
internal const val REMOTE_DISMISS_TAG = "remote_dismiss"
internal const val REMOTE_REQUEST_TAG = "remote_request"
internal const val REMOTE_FOCUS_TAG = "remote_focus"
internal const val REMOTE_FOCUS_STOP_TAG = "remote_focus_stop"
internal const val REMOTE_FOCUS_LEFT_TAG = "remote_focus_left"
internal const val REMOTE_NEW_BLOCK_TAG = "remote_new_block"
internal const val REMOTE_UNLINK_TAG = "remote_unlink"

internal fun remoteFocusTag(mins: Int) = "remote_focus_$mins"

internal fun remoteBlockTag(id: String) = "remote_block_$id"

internal fun remoteLockTag(pkg: String) = "remote_lock_$pkg"

/** What the screen can ask for; the route wires these to the view model. */
internal data class RemoteActions(
    val onBack: () -> Unit = {},
    val onRemind: (RemoteAlertCard) -> Unit = {},
    val onDismiss: (RemoteAlertCard) -> Unit = {},
    /** The unlock request buttons (M8-05): minutes to allow, or 0 for No. */
    val onDecide: (RemoteRequestCard, Int) -> Unit = { _, _ -> },
    val onStartFocus: (Int) -> Unit = {},
    val onStopFocus: () -> Unit = {},
    val onBlockEnabled: (blockId: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onNewBlock: () -> Unit = {},
    val onLock: (app: RemoteAppRow, locked: Boolean) -> Unit = { _, _ -> },
    val onUnlink: () -> Unit = {},
)

@Composable
internal fun RemoteDeviceRoute(
    onBack: () -> Unit,
    onNewBlock: (deviceId: String) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: RemoteDeviceViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalContext.current.resources
    val currentBack by rememberUpdatedState(onBack)
    val currentMessage by rememberUpdatedState(onMessage)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { event ->
            currentMessage(event.text(resources))
            if (event is RemoteEvent.Unlinked) currentBack()
        }
    }
    // The phone vanished while this screen was open (the child unlinked): there is nothing left to show.
    var wasReady by remember { mutableStateOf(false) }
    if (state is RemoteUiState.Ready) wasReady = true
    LaunchedEffect(state, wasReady) { if (state is RemoteUiState.NotFound && wasReady) currentBack() }
    val ready = state as? RemoteUiState.Ready
    RemoteDeviceScreen(
        state = state,
        now = { Instant.now() },
        actions =
            RemoteActions(
                onBack = onBack,
                onRemind = viewModel::remind,
                onDismiss = viewModel::dismiss,
                onStartFocus = viewModel::startFocus,
                onStopFocus = viewModel::stopFocus,
                onBlockEnabled = viewModel::setBlockEnabled,
                onNewBlock = { ready?.let { onNewBlock(it.device.id) } },
                onLock = { app, locked -> viewModel.setLocked(app.label, app.pkg, locked) },
                onUnlink = viewModel::unlink,
            ),
    )
}

private fun RemoteEvent.text(r: android.content.res.Resources): String =
    when (this) {
        is RemoteEvent.AppLocked -> r.getString(R.string.remote_toast_locked, app, device)
        is RemoteEvent.AppUnlocked -> r.getString(R.string.remote_toast_unlocked, app, device)
        is RemoteEvent.FocusStarted -> r.getString(R.string.remote_toast_focus, device)
        is RemoteEvent.Reminded -> r.getString(R.string.remote_toast_reminded, device)
        is RemoteEvent.Offline -> r.getString(R.string.remote_toast_offline, device)
        is RemoteEvent.Failed -> r.getString(R.string.remote_toast_failed, device)
        is RemoteEvent.Unlinked -> r.getString(R.string.remote_toast_unlinked, device)
        is RemoteEvent.UnlinkFailed -> r.getString(R.string.remote_toast_unlink_failed, device)
    }

@Composable
internal fun RemoteDeviceScreen(
    state: RemoteUiState,
    now: () -> Instant,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    val ready = state as? RemoteUiState.Ready
    LazyColumn(
        Modifier.fillMaxSize().background(c.background).testTag(REMOTE_LIST_TAG),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(ready, actions.onBack) }
        if (ready == null) return@LazyColumn
        items(ready.alerts, key = { "alert_${it.id}" }) { AlertCard(it, actions) }
        items(ready.requests, key = { "request_${it.id}" }) { RequestCard(it, actions) }
        item { FocusCard(ready.device.focusEndsAt, now, actions) }
        item { SectionRow(stringResource(R.string.remote_blocks_header), null) }
        items(ready.blocks, key = { "block_${it.rule.id}" }) { BlockRow(it, actions) }
        item { NewBlockButton(ready.device.name, actions.onNewBlock) }
        item {
            SectionRow(
                stringResource(R.string.remote_today_header, formatMinutes(ready.todayMins ?: 0).uppercase()),
                stringResource(R.string.remote_lock_header),
            )
        }
        items(ready.apps, key = { "app_${it.pkg}" }) { AppRow(it, actions) }
        item { UnlinkButton(ready.unlinking, actions.onUnlink) }
    }
}

@Composable
private fun Header(
    ready: RemoteUiState.Ready?,
    onBack: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LinkBackButton(onBack, light = false)
        if (ready != null) {
            val device = ready.device
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    device.name,
                    style = StayFocusedTheme.type.title.copy(fontSize = 22.sp),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tone = if (device.online) c.accent else c.tertiary
                    LedDot(color = tone, pulse = device.online)
                    Text(
                        statusLine(ready),
                        style = StayFocusedTheme.type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium),
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(REMOTE_STATUS_TAG),
                    )
                }
            }
        }
    }
}

/** "ONLINE · 64% · USING YOUTUBE"; an offline phone shows no app, as its last one is not current. */
@Composable
private fun statusLine(ready: RemoteUiState.Ready): String {
    val device = ready.device
    val parts =
        mutableListOf(
            stringResource(if (device.online) R.string.remote_status_online else R.string.remote_status_offline),
        )
    device.battery?.let { parts += "$it%" }
    if (device.online) device.currentApp?.let { parts += stringResource(R.string.remote_status_using, it) }
    return parts.joinToString(" · ").uppercase()
}

@Composable
private fun AlertCard(
    alert: RemoteAlertCard,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val title =
        stringResource(
            R.string.remote_alert_title,
            when (alert.kind) {
                TamperKind.PERMISSION_LOST -> {
                    stringResource(
                        if (alert.permission.equals("accessibility", ignoreCase = true)) {
                            R.string.devices_alert_accessibility
                        } else {
                            R.string.devices_alert_permission
                        },
                    )
                }

                TamperKind.UNLINKED -> {
                    stringResource(R.string.devices_alert_unlinked)
                }

                TamperKind.OFFLINE -> {
                    stringResource(R.string.devices_alert_offline)
                }
            },
            alert.time,
        )
    Column(
        Modifier
            .testTag(REMOTE_ALERT_TAG)
            .fillMaxWidth()
            .clip(shape)
            .background(c.alert.copy(alpha = 0.10f))
            .border(1.dp, c.alert.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(
                        top = 6.dp,
                    ).size(8.dp)
                    .background(c.alert, RoundedCornerShape(4.dp))
                    .clearAndSetSemantics { },
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = StayFocusedTheme.type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = c.alertText,
                )
                if (alert.kind == TamperKind.PERMISSION_LOST) {
                    Text(
                        stringResource(R.string.remote_alert_body),
                        style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, lineHeight = 19.sp),
                        color = c.cameraPrompt,
                    )
                }
            }
        }
        Row(Modifier.padding(start = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (alert.kind == TamperKind.PERMISSION_LOST) {
                SmallButton(
                    stringResource(R.string.remote_alert_remind),
                    background = c.alert,
                    content = c.onAccent,
                    onClick = { actions.onRemind(alert) },
                    modifier = Modifier.testTag(REMOTE_REMIND_TAG),
                )
            }
            SmallButton(
                stringResource(R.string.remote_alert_dismiss),
                background = Color.Transparent,
                content = c.cameraPrompt,
                onClick = { actions.onDismiss(alert) },
                modifier = Modifier.testTag(REMOTE_DISMISS_TAG),
            )
        }
    }
}

@Composable
private fun SmallButton(
    text: String,
    background: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = StayFocusedTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                color = content,
            )
        }
    }
}

@Composable
private fun RequestCard(
    request: RemoteRequestCard,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val ago =
        if (request.agoMins >= 60) {
            stringResource(R.string.remote_request_ago_hour, request.agoMins / 60)
        } else {
            stringResource(R.string.remote_request_ago_min, request.agoMins)
        }
    val options =
        listOf(
            stringResource(R.string.remote_request_15) to 15,
            stringResource(R.string.remote_request_30) to 30,
            stringResource(R.string.remote_request_60) to 60,
            stringResource(R.string.remote_request_no) to 0,
        )
    Column(
        Modifier
            .testTag(REMOTE_REQUEST_TAG)
            .fillMaxWidth()
            .clip(shape)
            .background(c.warning.copy(alpha = 0.10f))
            .border(1.dp, c.warning.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                ago,
                style = StayFocusedTheme.type.label.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = c.warning,
            )
            Text(
                stringResource(R.string.remote_request_question, request.minutes, request.app),
                style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                color = c.text,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (label, mins) ->
                val allow = mins > 0
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (allow) c.warning else Color.Transparent)
                        .then(if (allow) Modifier else Modifier.border(1.dp, c.hairline14, RoundedCornerShape(12.dp)))
                        .clickable(role = Role.Button) { actions.onDecide(request, mins) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = StayFocusedTheme.type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (allow) c.onAccent else c.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusCard(
    endsAt: Instant?,
    now: () -> Instant,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    val inspecting = LocalInspectionMode.current
    val clock by rememberUpdatedState(now)
    val current by produceState(clock(), endsAt) {
        value = clock()
        while (!inspecting && endsAt != null) {
            delay(1_000)
            value = clock()
        }
    }
    val leftMs = endsAt?.let { it.toEpochMilli() - current.toEpochMilli() } ?: 0L
    val running = leftMs > 0
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .testTag(REMOTE_FOCUS_TAG)
            .fillMaxWidth()
            .clip(shape)
            .background(if (running) c.accent else c.panel)
            .border(1.dp, c.hairline06, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (running) R.string.remote_focus_running else R.string.remote_focus_idle),
                style = StayFocusedTheme.type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                color = if (running) c.onAccent else c.text,
            )
            if (running) {
                Box(
                    Modifier
                        .testTag(REMOTE_FOCUS_STOP_TAG)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = actions.onStopFocus),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, c.onAccent, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            stringResource(R.string.remote_focus_stop),
                            style =
                                StayFocusedTheme.type.body.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                            color = c.onAccent,
                        )
                    }
                }
            }
        }
        if (running) {
            Text(
                mmss(leftMs),
                style =
                    StayFocusedTheme.type.label.copy(
                        fontSize = 44.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = c.onAccent,
                modifier = Modifier.testTag(REMOTE_FOCUS_LEFT_TAG),
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.remote_focus_25) to 25,
                    stringResource(R.string.remote_focus_50) to 50,
                    stringResource(R.string.remote_focus_90) to 90,
                ).forEach { (label, mins) ->
                    val shapeBtn = RoundedCornerShape(14.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .testTag(remoteFocusTag(mins))
                            .height(48.dp)
                            .clip(shapeBtn)
                            .background(c.background)
                            .border(1.dp, c.hairline14, shapeBtn)
                            .clickable(role = Role.Button) { actions.onStartFocus(mins) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = StayFocusedTheme.type.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = c.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    start: String,
    end: String?,
) {
    val c = StayFocusedTheme.colors
    val style = StayFocusedTheme.type.label.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start, style = style, color = c.secondary)
        if (end != null) Text(end, style = style, color = c.secondary)
    }
}

@Composable
private fun BlockRow(
    block: RemoteBlock,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val name = block.rule.name
    Row(
        Modifier
            .testTag(remoteBlockTag(block.rule.id))
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.hairline06, shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeChip(block.rule.type.toBlockType(), dimmed = !block.enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                name,
                style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = c.text,
            )
            Text(
                ruleSummary(block.rule),
                style = StayFocusedTheme.type.body.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = c.secondary,
            )
        }
        val label = stringResource(R.string.remote_block_toggle, name)
        SfToggle(
            checked = block.enabled,
            onCheckedChange = { actions.onBlockEnabled(block.rule.id, it) },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

private fun KidsRuleType.toBlockType(): BlockType =
    when (this) {
        KidsRuleType.Limit -> BlockType.LIMIT
        KidsRuleType.Cycle -> BlockType.CYCLE
        KidsRuleType.Schedule -> BlockType.SCHEDULE
        KidsRuleType.Now -> BlockType.NOW
    }

@Composable
private fun ruleSummary(rule: KidsRule): String =
    when (rule.type) {
        KidsRuleType.Limit -> {
            stringResource(
                if (rule.hourly) R.string.remote_rule_limit_hourly else R.string.remote_rule_limit_daily,
                rule.limitMins ?: 0,
            )
        }

        KidsRuleType.Cycle -> {
            stringResource(R.string.remote_rule_cycle, rule.useMins ?: 0, rule.restMins ?: 0)
        }

        KidsRuleType.Schedule -> {
            stringResource(
                R.string.remote_rule_schedule,
                daysLabel(DaysOfWeek(rule.days)),
                rule.start.orEmpty(),
                rule.end.orEmpty(),
            )
        }

        KidsRuleType.Now -> {
            stringResource(R.string.remote_rule_now, rule.durationMins ?: 0)
        }
    }

@Composable
private fun NewBlockButton(
    name: String,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    Row(
        Modifier
            .testTag(REMOTE_NEW_BLOCK_TAG)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile { Text("+", color = c.accent, style = StayFocusedTheme.type.body.copy(fontSize = 16.sp)) }
        Text(
            stringResource(R.string.remote_new_block, name),
            style = StayFocusedTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Black),
            color = c.onAccent,
        )
    }
}

@Composable
private fun AppRow(
    app: RemoteAppRow,
    actions: RemoteActions,
) {
    val c = StayFocusedTheme.colors
    Row(
        Modifier
            .testTag("remote_app_${app.pkg}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg = app.pkg, label = app.label, size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    app.label,
                    style = StayFocusedTheme.type.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMinutes(app.mins),
                    style = StayFocusedTheme.type.label.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = c.secondary,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(app.fraction)
                        .height(3.dp)
                        .background(if (app.locked) c.alert else c.accent),
                )
            }
        }
        val label = stringResource(R.string.remote_lock_toggle, app.label)
        SfToggle(
            checked = app.locked,
            onCheckedChange = { actions.onLock(app, it) },
            modifier = Modifier.testTag(remoteLockTag(app.pkg)).semantics { contentDescription = label },
        )
    }
}

@Composable
private fun UnlinkButton(
    busy: Boolean,
    onClick: () -> Unit,
) {
    val c = StayFocusedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .testTag(REMOTE_UNLINK_TAG)
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(50.dp)
            .clip(shape)
            .border(1.dp, c.alert.copy(alpha = 0.3f), shape)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.remote_unlink),
            style = StayFocusedTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = if (busy) c.tertiary else c.alertText,
        )
    }
}
