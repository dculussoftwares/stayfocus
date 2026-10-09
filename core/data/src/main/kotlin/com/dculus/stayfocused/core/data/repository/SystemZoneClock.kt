package com.dculus.stayfocused.core.data.repository

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the device's *current* time zone. Unlike `Clock.systemDefaultZone()` the zone is read on every
 * call, so a time-zone change (travel, automatic zone) is picked up without restarting the process. Schedules,
 * daily limits and "today" all depend on local midnight, so the zone must not be UTC.
 */
internal object SystemZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
