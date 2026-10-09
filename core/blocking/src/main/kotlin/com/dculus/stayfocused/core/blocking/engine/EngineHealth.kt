package com.dculus.stayfocused.core.blocking.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the permission-health screens (M5-03, M6-05) can say about the blocking engine.
 *
 * [serviceBound] is true between the accessibility service connecting and unbinding; a reboot, a process death or a
 * force-stop leaves it false until the system rebinds the service (a force-stop keeps it off until the user turns
 * the service on again). [lastEventAt] is when the service last saw a window change in this process, or `null`.
 */
data class EngineHealth(
    val serviceBound: Boolean = false,
    val lastEventAt: Instant? = null,
)

/** Written by `ForegroundAppService`; read through [health]. */
@Singleton
class EngineHealthMonitor
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        private val _health = MutableStateFlow(EngineHealth())
        val health: StateFlow<EngineHealth> = _health.asStateFlow()

        fun onServiceConnected() = _health.update { it.copy(serviceBound = true) }

        fun onServiceUnbound() = _health.update { it.copy(serviceBound = false) }

        fun onEvent() = _health.update { it.copy(lastEventAt = clock.instant()) }
    }
