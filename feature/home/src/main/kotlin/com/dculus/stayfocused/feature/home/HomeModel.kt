package com.dculus.stayfocused.feature.home

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.GoalNote
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.UnlockRequest
import com.dculus.stayfocused.core.model.UnlockRequestStatus
import com.dculus.stayfocused.core.model.badgeCount
import com.dculus.stayfocused.core.model.goalNote
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINS_PER_HOUR = 60
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val TILTED_APPS = 2

/** Top of Home: the date line, the avatar letter and the greeting name. [firstName] is null without an account. */
internal data class HeaderUi(
    val dateLabel: String,
    val firstName: String?,
) {
    /** Letter on the avatar, or null (no account) for the generic person glyph. */
    val initial: String? get() = firstName?.firstOrNull()?.uppercase()
}

private val DateLineFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** "Sunday, 18 October" (prototype header line). */
internal fun headerFor(
    now: ZonedDateTime,
    firstName: String?,
    locale: Locale = Locale.getDefault(),
): HeaderUi =
    HeaderUi(
        dateLabel = DateLineFormat.withLocale(locale).format(now),
        firstName = firstName?.trim()?.takeIf { it.isNotEmpty() },
    )

/** The app used most today: [mins] of use, [label] is its display name. */
internal data class MostUsedUi(
    val pkg: String,
    val label: String,
    val mins: Int,
)

/** The hero card. */
internal sealed interface GaugeUi {
    /** Usage access is off: nothing can be measured. */
    data object NoAccess : GaugeUi

    /**
     * Today's numbers. [hourlyMins] are the 24 hourly columns of the dot grid and [currentHour] the highlighted one.
     * [goalMins] is the screen-time goal. [avgMins] is null until a complete day of history exists.
     */
    data class Ready(
        val totalMins: Int,
        val goalMins: Int,
        val hourlyMins: List<Int>,
        val currentHour: Int,
        val mostUsed: MostUsedUi?,
        val blocked: Int,
        val avgMins: Int?,
        val launches: Int,
        val unlocks: Int,
    ) : GaugeUi {
        /** How today compares with the goal (hero note). */
        val goalNote: GoalNote get() = goalNote(totalMins, goalMins)

        /** Minutes above (+) or below (-) the average, or null without one. */
        val deltaMins: Int? get() = avgMins?.let { totalMins - it }
    }
}

/** "h:mm" of the hero numerals (prototype `hm`). */
internal fun clockMins(mins: Int): String =
    String.format(Locale.ROOT, "%d:%02d", mins / MINS_PER_HOUR, mins % MINS_PER_HOUR)

/** "mm:ss" of the running break (the prototype pads the minutes too). */
internal fun paddedMmss(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    return String.format(Locale.ROOT, "%02d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** One "Active blocks" row. [appLabels] are the display names of the block's apps. */
internal data class HomeBlockUi(
    val block: Block,
    val appLabels: List<String>,
)

/** Everything on Home except the break tile. */
internal data class HomeUiState(
    val header: HeaderUi,
    val gauge: GaugeUi,
    val blocks: List<HomeBlockUi>,
    val devices: List<LinkedDevice>,
    /** AI is switched on in settings; the "Describe a block" pill is hidden otherwise. */
    val aiAvailable: Boolean,
) {
    /** Blocks of this phone that are switched on (the "{n} on this phone" line). */
    val activeBlockCount: Int get() = blocks.count { it.block.enabled }

    /** Package names on the two tilted tiles of "Block apps": the apps of the first active blocks. */
    val tiltedApps: List<String>
        get() =
            blocks
                .filter { it.block.enabled }
                .flatMap { it.block.apps }
                .distinct()
                .take(TILTED_APPS)

    /** The phone the bell opens: the first needing attention (alert or open request), else null (go to Family). */
    val attentionDevice: LinkedDevice? get() = devices.firstOrNull { it.badgeCount() > 0 }
}

/** The first open request of this phone, or null. */
internal fun LinkedDevice.pendingRequest(): UnlockRequest? =
    requests.firstOrNull { it.status == UnlockRequestStatus.PENDING }
