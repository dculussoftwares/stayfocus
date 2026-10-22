@file:Suppress("MagicNumber", "TooManyFunctions")

package com.dculus.stayfocused.feature.devices

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.formatMinutes
import com.dculus.stayfocused.core.model.mmss
import com.dculus.stayfocused.core.sync.KidsRule
import com.dculus.stayfocused.core.sync.KidsRuleType
import com.dculus.stayfocused.core.sync.RemoteBlock
import com.dculus.stayfocused.core.ui.components.daysLabel
import com.dculus.stayfocused.core.ui.v5.charts.DotGrid
import com.dculus.stayfocused.core.ui.v5.components.AppIconTile
import com.dculus.stayfocused.core.ui.v5.components.DotMatrixText
import com.dculus.stayfocused.core.ui.v5.components.GlowCard
import com.dculus.stayfocused.core.ui.v5.components.LocalTabBarPadding
import com.dculus.stayfocused.core.ui.v5.components.OnlinePill
import com.dculus.stayfocused.core.ui.v5.components.PillButton
import com.dculus.stayfocused.core.ui.v5.components.PillButtonSize
import com.dculus.stayfocused.core.ui.v5.components.PillButtonVariant
import com.dculus.stayfocused.core.ui.v5.components.RoundIconButton
import com.dculus.stayfocused.core.ui.v5.components.StatusTag
import com.dculus.stayfocused.core.ui.v5.components.StatusTagKind
import com.dculus.stayfocused.core.ui.v5.components.V5Card
import com.dculus.stayfocused.core.ui.v5.components.V5Toggle
import com.dculus.stayfocused.core.ui.v5.icons.V5Icons
import com.dculus.stayfocused.core.ui.v5.theme.V5Gradients
import com.dculus.stayfocused.core.ui.v5.theme.V5Screen
import com.dculus.stayfocused.core.ui.v5.theme.V5Theme
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale

internal const val REMOTE_LIST_TAG = "remote_list"
internal const val REMOTE_HERO_TAG = "remote_hero"
internal const val REMOTE_LOCK_ALL_TAG = "remote_lock_all"
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
    val onLockAll: () -> Unit = {},
    val onUnlockAll: () -> Unit = {},
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
    // Leaving happens once, whichever comes first: the unlink result or the phone vanishing from the list.
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { event ->
            currentMessage(event.text(resources))
            if (event is RemoteEvent.Unlinked && !left) {
                left = true
                currentBack()
            }
        }
    }
    // The phone vanished while this screen was open (the child unlinked): there is nothing left to show.
    var wasReady by remember { mutableStateOf(false) }
    if (state is RemoteUiState.Ready) wasReady = true
    LaunchedEffect(state, wasReady) {
        if (state is RemoteUiState.NotFound && wasReady && !left) {
            left = true
            currentBack()
        }
    }
    val ready = state as? RemoteUiState.Ready
    RemoteDeviceScreen(
        state = state,
        now = { Instant.now() },
        actions =
            RemoteActions(
                onBack = onBack,
                onRemind = viewModel::remind,
                onDismiss = viewModel::dismiss,
                onDecide = viewModel::decide,
                onStartFocus = viewModel::startFocus,
                onStopFocus = viewModel::stopFocus,
                onBlockEnabled = viewModel::setBlockEnabled,
                onNewBlock = { ready?.let { onNewBlock(it.device.id) } },
                onLockAll = viewModel::lockAll,
                onUnlockAll = viewModel::unlockAll,
                onLock = { app, locked -> viewModel.setLocked(app.label, app.pkg, locked) },
                onUnlink = viewModel::unlink,
            ),
    )
}

private fun RemoteEvent.text(r: android.content.res.Resources): String =
    when (this) {
        is RemoteEvent.AppLocked -> r.getString(R.string.remote_toast_locked, app, device)
        is RemoteEvent.AppUnlocked -> r.getString(R.string.remote_toast_unlocked, app, device)
        is RemoteEvent.AllLocked -> r.getString(R.string.remote_toast_all_locked, device)
        RemoteEvent.AllUnlocked -> r.getString(R.string.remote_toast_all_unlocked)
        is RemoteEvent.FocusStarted -> r.getString(R.string.remote_toast_focus, device)
        is RemoteEvent.FocusStopped -> r.getString(R.string.remote_toast_focus_stopped)
        is RemoteEvent.Reminded -> r.getString(R.string.remote_toast_reminded, device)
        is RemoteEvent.Offline -> r.getString(R.string.remote_toast_offline, device)
        is RemoteEvent.Failed -> r.getString(R.string.remote_toast_failed, device)
        is RemoteEvent.RequestApproved -> r.getString(R.string.remote_toast_approved, device, minutesText(r, mins))
        RemoteEvent.RequestDenied -> r.getString(R.string.remote_toast_denied)
        is RemoteEvent.Unlinked -> r.getString(R.string.remote_toast_unlinked)
        is RemoteEvent.UnlinkFailed -> r.getString(R.string.remote_toast_unlink_failed, device)
    }

/** Prototype `fm`: "30 min", "1h", "1h 30m". */
private fun minutesText(
    r: android.content.res.Resources,
    mins: Int,
): String = if (mins < 60) r.getString(R.string.remote_minutes, mins) else formatMinutes(mins).replace(" 0m", "")

@Composable
internal fun RemoteDeviceScreen(
    state: RemoteUiState,
    now: () -> Instant,
    actions: RemoteActions,
) = CompositionLocalProvider(LocalNow provides now) {
    V5Screen(darkContent = false) { RemoteDeviceList(state, now, actions) }
}

@Composable
private fun RemoteDeviceList(
    state: RemoteUiState,
    now: () -> Instant,
    actions: RemoteActions,
) {
    val spacing = V5Theme.spacing
    val ready = state as? RemoteUiState.Ready
    LazyColumn(
        Modifier.fillMaxSize().testTag(REMOTE_LIST_TAG),
        contentPadding =
            PaddingValues(
                start = spacing.screenTabs,
                end = spacing.screenTabs,
                // The prototype's 56 dp top padding includes its 46 dp fake status bar.
                top = 10.dp,
                bottom = 40.dp + LocalTabBarPadding.current,
            ),
        verticalArrangement = Arrangement.spacedBy(spacing.gap10),
    ) {
        item { Header(ready, actions.onBack) }
        if (ready == null) return@LazyColumn
        item { Hero(ready) }
        items(ready.alerts, key = { "alert_${it.id}" }) { AlertCard(it, ready.device.name, actions) }
        items(ready.requests, key = { "request_${it.id}" }) { RequestCard(it, ready.device.name, actions) }
        item { Tiles(ready.device.allLocked, ready.device.focusEndsAt, now, actions) }
        // v5-extra: M13-05 (focus 50 and 90 minutes)
        item { ExtraFocusRow(ready.device.focusEndsAt, now, actions) }
        item { BlocksHeader(actions.onNewBlock) }
        items(ready.blocks, key = { "block_${it.rule.id}" }) { BlockRow(it, actions) }
        // v5-extra: M13-05 (per-app lock list)
        item { AppsHeader(ready.todayMins ?: 0) }
        items(ready.apps, key = { "app_${it.pkg}" }) { AppRow(it, actions) }
        item { UnlinkButton(ready.unlinking, actions.onUnlink) }
    }
}

@Composable
private fun Header(
    ready: RemoteUiState.Ready?,
    onBack: () -> Unit,
) {
    val c = V5Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(V5Icons.back, stringResource(R.string.link_back), onBack, Modifier.testTag(LINK_BACK_TAG))
        if (ready != null) {
            val device = ready.device
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = V5Theme.type.cardTitle.copy(fontSize = 22.sp),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle(ready)?.let {
                    Text(
                        it,
                        style = V5Theme.type.body.copy(fontSize = 13.sp),
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(REMOTE_STATUS_TAG),
                    )
                }
            }
            OnlinePill(online = device.online)
        }
    }
}

/** "Pixel 7a · 74% battery"; an offline phone also says when it was last seen (v5-extra: M13-05). */
@Composable
private fun subtitle(ready: RemoteUiState.Ready): String? {
    val device = ready.device
    val parts = mutableListOf<String>()
    if (device.model.isNotBlank()) parts += device.model
    device.battery?.let { parts += stringResource(R.string.remote_battery, it) }
    if (!device.online) lastSeenText(device.lastSeen)?.let { parts += it }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun clock(mins: Int) = String.format(Locale.ROOT, "%d:%02d", mins / 60, mins % 60)

private const val HOUR_DOTS_MAX_MINS = 25

@Composable
private fun Hero(ready: RemoteUiState.Ready) {
    val white = V5Theme.colors.onGradient
    val total = ready.todayMins ?: 0
    GlowCard(
        V5Gradients.pinkHero,
        Modifier.fillMaxWidth().testTag(REMOTE_HERO_TAG),
        padding = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.remote_hero_title), style = V5Theme.type.label, color = white)
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                DotMatrixText(clock(total), formatMinutes(total), dotSize = 8.dp, color = white)
            }
            val using = ready.device.currentApp?.takeIf { ready.device.online }
            if (using != null) {
                Text(
                    stringResource(R.string.remote_hero_using, using),
                    style = V5Theme.type.label.copy(fontSize = 15.sp),
                    color = white,
                    textAlign = TextAlign.Center,
                )
            }
            ready.device.hourlyMins?.let {
                DotGrid(
                    values = it,
                    max = HOUR_DOTS_MAX_MINS,
                    color = white,
                    contentDescription = stringResource(R.string.remote_dots_description),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun agoText(mins: Long): String =
    when {
        mins < 1 -> stringResource(R.string.remote_ago_now)
        mins < 60 -> stringResource(R.string.remote_ago_min, mins)
        mins < 24 * 60 -> stringResource(R.string.remote_ago_hour, mins / 60)
        else -> stringResource(R.string.remote_ago_day, mins / (24 * 60))
    }

@Composable
private fun AlertCard(
    alert: RemoteAlertCard,
    deviceName: String,
    actions: RemoteActions,
) {
    val c = V5Theme.colors
    val permission = alert.kind == TamperKind.PERMISSION_LOST
    val body =
        when (alert.kind) {
            TamperKind.PERMISSION_LOST -> {
                stringResource(
                    if (alert.permission.equals("accessibility", ignoreCase = true)) {
                        R.string.remote_alert_accessibility
                    } else {
                        R.string.remote_alert_permission
                    },
                    deviceName,
                )
            }

            // v5-extra: M13-05 (the other alert kinds keep their shipped line)
            TamperKind.UNLINKED -> {
                stringResource(R.string.devices_alert_unlinked)
            }

            TamperKind.OFFLINE -> {
                stringResource(R.string.devices_alert_offline)
            }
        }
    V5Card(Modifier.fillMaxWidth().testTag(REMOTE_ALERT_TAG), radius = 30.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (permission) StatusTag(StatusTagKind.ProtectionOff)
                Text(agoText(alert.agoMins), style = V5Theme.type.body.copy(fontSize = 13.sp), color = c.textSecondary)
            }
            Text(body, style = V5Theme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = c.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (permission) {
                    PillButton(
                        stringResource(R.string.remote_alert_remind),
                        onClick = { actions.onRemind(alert) },
                        modifier = Modifier.weight(1f).testTag(REMOTE_REMIND_TAG),
                        size = PillButtonSize.Compact,
                    )
                }
                PillButton(
                    stringResource(R.string.remote_alert_dismiss),
                    onClick = { actions.onDismiss(alert) },
                    modifier = Modifier.weight(1f).testTag(REMOTE_DISMISS_TAG),
                    variant = PillButtonVariant.Secondary,
                    size = PillButtonSize.Compact,
                )
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: RemoteRequestCard,
    deviceName: String,
    actions: RemoteActions,
) {
    val c = V5Theme.colors
    val options =
        listOf(
            stringResource(R.string.remote_request_15) to 15,
            stringResource(R.string.remote_request_30) to 30,
            stringResource(R.string.remote_request_60) to 60,
            stringResource(R.string.remote_request_no) to 0,
        )
    V5Card(Modifier.fillMaxWidth().testTag(REMOTE_REQUEST_TAG), radius = 30.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusTag(StatusTagKind.Request)
                Text(
                    agoText(request.agoMins),
                    style = V5Theme.type.body.copy(fontSize = 13.sp),
                    color = c.textSecondary,
                )
            }
            Text(
                stringResource(R.string.remote_request_body, deviceName, request.minutes, request.app),
                style = V5Theme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = c.ink,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (label, mins) ->
                    CompactPill(
                        label,
                        onClick = { actions.onDecide(request, mins) },
                        modifier = Modifier.weight(1f),
                        dark = mins > 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tiles(
    allLocked: Boolean,
    endsAt: Instant?,
    now: () -> Instant,
    actions: RemoteActions,
) {
    val leftMs = focusLeftMs(endsAt, now)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LockTile(allLocked, actions, Modifier.weight(1f))
        FocusTile(leftMs > 0, leftMs, actions, Modifier.weight(1f))
    }
}

@Composable
private fun LockTile(
    allLocked: Boolean,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
) {
    val c = V5Theme.colors
    V5Card(
        modifier.height(140.dp).testTag(REMOTE_LOCK_ALL_TAG),
        radius = 32.dp,
        containerColor = c.ink,
        padding = 16.dp,
        onClick = if (allLocked) actions.onUnlockAll else actions.onLockAll,
    ) {
        Box(Modifier.fillMaxSize()) {
            Icon(V5Icons.lock, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(24.dp))
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (allLocked) R.string.remote_unlock_title else R.string.remote_lock_title),
                    style = V5Theme.type.cardTitle.copy(fontSize = 18.sp),
                    color = c.onGradient,
                )
                Text(
                    stringResource(if (allLocked) R.string.remote_unlock_sub else R.string.remote_lock_sub),
                    style = V5Theme.type.body.copy(fontSize = 13.sp),
                    color = c.onInkMuted,
                )
            }
        }
    }
}

private const val FOCUS_DEFAULT_MINS = 25

@Composable
private fun FocusTile(
    running: Boolean,
    leftMs: Long,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
) {
    val white = V5Theme.colors.onGradient
    GlowCard(
        V5Gradients.orangeTile,
        modifier.height(140.dp).testTag(if (running) REMOTE_FOCUS_STOP_TAG else REMOTE_FOCUS_TAG),
        radius = 32.dp,
        padding = 16.dp,
        onClick = if (running) actions.onStopFocus else ({ actions.onStartFocus(FOCUS_DEFAULT_MINS) }),
    ) {
        Box(Modifier.fillMaxSize()) {
            Icon(V5Icons.timer, contentDescription = null, tint = white, modifier = Modifier.size(24.dp))
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (running) R.string.remote_focus_on else R.string.remote_focus_title),
                    style = V5Theme.type.cardTitle.copy(fontSize = 18.sp),
                    color = white,
                )
                Text(
                    if (running) {
                        stringResource(R.string.remote_focus_left, mmss(leftMs))
                    } else {
                        stringResource(R.string.remote_focus_sub)
                    },
                    style = V5Theme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = white,
                    modifier = Modifier.testTag(REMOTE_FOCUS_LEFT_TAG),
                )
            }
        }
    }
}

/** Milliseconds left of the focus session, re-read every second while one runs; 0 when none runs. */
@Composable
private fun focusLeftMs(
    endsAt: Instant?,
    now: () -> Instant,
): Long {
    val inspecting = LocalInspectionMode.current
    val clock by rememberUpdatedState(now)
    val current by produceState(clock(), endsAt) {
        value = clock()
        while (!inspecting && endsAt != null) {
            delay(1_000)
            value = clock()
        }
    }
    return endsAt?.let { it.toEpochMilli() - current.toEpochMilli() }?.coerceAtLeast(0) ?: 0L
}

@Composable
private fun ExtraFocusRow(
    endsAt: Instant?,
    now: () -> Instant,
    actions: RemoteActions,
) {
    if (focusLeftMs(endsAt, now) > 0) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            stringResource(R.string.remote_focus_50) to 50,
            stringResource(R.string.remote_focus_90) to 90,
        ).forEach { (label, mins) ->
            PillButton(
                label,
                onClick = { actions.onStartFocus(mins) },
                modifier = Modifier.weight(1f).testTag(remoteFocusTag(mins)),
                variant = PillButtonVariant.White,
                size = PillButtonSize.Compact,
            )
        }
    }
}

@Composable
private fun BlocksHeader(onNewBlock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.remote_blocks_title),
            style = V5Theme.type.cardTitle,
            color = V5Theme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        CompactPill(
            stringResource(R.string.remote_new_block),
            onClick = onNewBlock,
            modifier = Modifier.testTag(REMOTE_NEW_BLOCK_TAG),
            dark = true,
            height = 40.dp,
            icon = V5Icons.plus,
        )
    }
}

@Composable
private fun BlockRow(
    block: RemoteBlock,
    actions: RemoteActions,
) {
    val c = V5Theme.colors
    val name = block.rule.name
    V5Card(
        Modifier.fillMaxWidth().testTag(remoteBlockTag(block.rule.id)).alpha(if (block.enabled) 1f else 0.6f),
        radius = 28.dp,
        padding = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The rule names no packages, so the tile shows the block's first letter.
            AppIconTile(block.rule.id, 34.dp, label = name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    name,
                    style = V5Theme.type.label.copy(fontSize = 15.sp),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ruleSummary(block.rule),
                    style = V5Theme.type.body.copy(fontSize = 13.sp),
                    color = c.textSecondary,
                )
            }
            val label = stringResource(R.string.remote_block_toggle, name)
            V5Toggle(
                checked = block.enabled,
                onCheckedChange = { actions.onBlockEnabled(block.rule.id, it) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
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
private fun AppsHeader(totalMins: Int) {
    val style = V5Theme.type.tag
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.remote_today_header, formatMinutes(totalMins).uppercase()),
            style = style,
            color = V5Theme.colors.textSecondary,
        )
        Text(stringResource(R.string.remote_lock_header), style = style, color = V5Theme.colors.textSecondary)
    }
}

@Composable
private fun AppRow(
    app: RemoteAppRow,
    actions: RemoteActions,
) {
    val c = V5Theme.colors
    V5Card(Modifier.fillMaxWidth().testTag("remote_app_${app.pkg}"), radius = 28.dp, padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconTile(app.pkg, 38.dp, label = app.label)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        app.label,
                        style = V5Theme.type.label.copy(fontSize = 15.sp),
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMinutes(app.mins),
                        style = V5Theme.type.body.copy(fontSize = 13.sp),
                        color = c.textSecondary,
                    )
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(c.track, RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth(app.fraction)
                            .height(3.dp)
                            .background(if (app.locked) c.red else c.orange, RoundedCornerShape(2.dp)),
                    )
                }
            }
            val label = stringResource(R.string.remote_lock_toggle, app.label)
            V5Toggle(
                checked = app.locked,
                onCheckedChange = { actions.onLock(app, it) },
                modifier = Modifier.testTag(remoteLockTag(app.pkg)).semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun UnlinkButton(
    busy: Boolean,
    onClick: () -> Unit,
) {
    PillButton(
        stringResource(R.string.remote_unlink),
        onClick = onClick,
        modifier = Modifier.padding(top = 6.dp).testTag(REMOTE_UNLINK_TAG),
        variant = PillButtonVariant.Text,
        size = PillButtonSize.Medium,
        enabled = !busy,
        textColor = V5Theme.colors.destructive,
    )
}

/** A pill that is only as wide as its label allows: the shared [PillButton] pads 24 dp a side, too much for four in a row. */
@Composable
private fun CompactPill(
    text: String,
    onClick: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 46.dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val c = V5Theme.colors
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .sizeIn(minHeight = V5Theme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(height)
                .clip(shape)
                .background(if (dark) c.ink else c.track)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.onGradient, modifier = Modifier.size(13.dp))
            }
            Text(
                text,
                style = V5Theme.type.label,
                color = if (dark) c.onGradient else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
