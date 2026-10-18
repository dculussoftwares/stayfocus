@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package com.dculus.stayfocused.feature.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import java.time.Duration
import java.time.Instant

/** How long ago a phone last reported, in the largest whole unit. */
internal sealed interface LastSeen {
    data class Minutes(
        val count: Int,
    ) : LastSeen

    data class Hours(
        val count: Int,
    ) : LastSeen

    data class Days(
        val count: Int,
    ) : LastSeen
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/** [lastSeen] relative to [now]; at least one minute (a phone reported seconds ago is online, not "last seen"). */
internal fun lastSeenAgo(
    lastSeen: Instant,
    now: Instant,
): LastSeen {
    val minutes = Duration.between(lastSeen, now).toMinutes().coerceAtLeast(1L)
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours >= HOURS_PER_DAY -> LastSeen.Days((hours / HOURS_PER_DAY).toInt())
        hours >= 1 -> LastSeen.Hours(hours.toInt())
        else -> LastSeen.Minutes(minutes.toInt())
    }
}

/** The current time as the screens see it; tests provide a fixed one so "Last seen 3 h ago" is stable. */
internal val LocalNow = staticCompositionLocalOf<() -> Instant> { { Instant.now() } }

/** "Last seen 3 h ago", or null when the phone has never reported. */
@Composable
internal fun lastSeenText(lastSeen: Instant?): String? =
    lastSeen?.let {
        when (val ago = lastSeenAgo(it, LocalNow.current())) {
            is LastSeen.Minutes -> pluralStringResource(R.plurals.devices_last_seen_minutes, ago.count, ago.count)
            is LastSeen.Hours -> pluralStringResource(R.plurals.devices_last_seen_hours, ago.count, ago.count)
            is LastSeen.Days -> pluralStringResource(R.plurals.devices_last_seen_days, ago.count, ago.count)
        }
    }
