package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which source tells the engine what is in front. */
enum class EngineMode {
    /** Nothing can detect the foreground app: a permission is missing and Accessibility is off. */
    OFF,

    /** The accessibility service reports window changes. Wins whenever it is bound. */
    ACCESSIBILITY,

    /** Usage access + Display over other apps: usage events are polled by a foreground service. */
    STANDARD,
}

/** Pure choice of the [EngineMode]; one source at a time, so time is never booked twice. */
fun resolveEngineMode(
    accessibilityBound: Boolean,
    usage: Boolean,
    overlay: Boolean,
): EngineMode =
    when {
        accessibilityBound -> EngineMode.ACCESSIBILITY
        usage && overlay -> EngineMode.STANDARD
        else -> EngineMode.OFF
    }

/**
 * Holds the current [EngineMode]. Call [refresh] when something it depends on may have changed: the accessibility
 * service (un)binds, the app comes back to the front, a permission read fails.
 */
@Singleton
class EngineModeArbiter
    @Inject
    constructor(
        private val permissions: PermissionsRepository,
        private val health: EngineHealthMonitor,
    ) {
        private val _mode = MutableStateFlow(EngineMode.OFF)
        val mode: StateFlow<EngineMode> = _mode.asStateFlow()

        fun refresh(): EngineMode {
            val p = permissions.current()
            val next = resolveEngineMode(health.health.value.serviceBound, p.usage, p.overlay)
            _mode.value = next
            health.onModeChanged(next)
            return next
        }
    }
