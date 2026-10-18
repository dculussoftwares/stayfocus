package com.dculus.stayfocused.core.model

import java.time.Instant
import java.time.LocalDate

data class BreakSession(
    val startedAt: Instant,
    val endsAt: Instant,
    val lengthMins: Int,
)

data class FocusSession(
    val startedAt: Instant,
    val endsAt: Instant,
    val lengthMins: Int,
)

data class LockedApp(
    val pkg: String,
    val target: BlockTarget,
    val since: Instant,
)

data class TemporaryAllowance(
    val pkg: String,
    val until: Instant,
)

data class Permissions(
    val usage: Boolean,
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    /** Battery optimisation exemption. Informational: never gates onboarding or blocking. */
    val batteryUnrestricted: Boolean = false,
) {
    /** The four permissions that gate blocking; [batteryUnrestricted] is deliberately excluded. */
    val allGatingGranted: Boolean get() = usage && accessibility && overlay && notifications
}

data class AppInfo(
    val pkg: String,
    val label: String,
)

data class DayUsage(
    val date: LocalDate,
    val totalMins: Int,
)

data class AppUsage(
    val pkg: String,
    val label: String,
    val mins: Int,
    val opens: Int,
)

enum class TamperKind { PERMISSION_LOST, UNLINKED, OFFLINE }

data class TamperAlert(
    val id: String,
    val kind: TamperKind,
    /** Set when [kind] is [TamperKind.PERMISSION_LOST]. */
    val permission: String?,
    val createdAt: Instant,
    val dismissed: Boolean,
)

enum class UnlockRequestStatus { PENDING, APPROVED, DENIED }

data class UnlockRequest(
    val id: String,
    val app: String,
    val minutes: Int,
    val status: UnlockRequestStatus,
    val createdAt: Instant,
)

data class LinkedDevice(
    val id: String,
    val name: String,
    val model: String,
    val battery: Int?,
    val currentApp: String?,
    val online: Boolean,
    val lastSeen: Instant?,
    val alerts: List<TamperAlert>,
    val requests: List<UnlockRequest>,
    val focusEndsAt: Instant?,
    /** Minutes of screen time the phone reported for today; null until it has reported any. */
    val todayMins: Int? = null,
    /**
     * Not heard from for at least 24 h (derived from [lastSeen]; no function watches for it). Possibly switched off,
     * out of battery or uninstalled: the parent sees "Offline for a while".
     */
    val longOffline: Boolean = false,
)

/** True when the phone is offline for 24 h and no `offline` alert of its own is open, so the parent-side one counts. */
val LinkedDevice.hasDerivedOfflineAlert: Boolean
    get() = longOffline && alerts.none { it.kind == TamperKind.OFFLINE && !it.dismissed }

/** Undismissed alerts, the derived offline alert and pending unlock requests: the number on the phone's card. */
fun LinkedDevice.badgeCount(): Int =
    alerts.count { !it.dismissed } +
        (if (hasDerivedOfflineAlert) 1 else 0) +
        requests.count { it.status == UnlockRequestStatus.PENDING }
