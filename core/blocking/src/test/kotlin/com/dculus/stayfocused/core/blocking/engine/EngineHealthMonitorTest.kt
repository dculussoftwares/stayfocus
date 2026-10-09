package com.dculus.stayfocused.core.blocking.engine

import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EngineHealthMonitorTest {
    private var now = Instant.parse("2026-01-01T10:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = now
        }
    private val monitor = EngineHealthMonitor(clock)

    @Test fun startsUnboundWithoutEvents() {
        assertFalse(monitor.health.value.serviceBound)
        assertNull(monitor.health.value.lastEventAt)
    }

    @Test fun connectingBindsAndStampsTime() {
        monitor.onServiceConnected()
        assertEquals(EngineHealth(true, now), monitor.health.value)
    }

    @Test fun eventsMoveLastEventTime() {
        monitor.onServiceConnected()
        now = now.plusSeconds(30)
        monitor.onEvent()
        assertEquals(now, monitor.health.value.lastEventAt)
    }

    @Test fun unbindKeepsLastEventButClearsBound() {
        monitor.onServiceConnected()
        monitor.onServiceUnbound()
        assertFalse(monitor.health.value.serviceBound)
        assertNotNull(monitor.health.value.lastEventAt)
    }
}
