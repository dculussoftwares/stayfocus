package com.dculus.vesper.core.model

import java.time.Instant

/** App-wide preferences stored in the `settings` DataStore. Break sessions are owned by the break repository. */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val accountSkipped: Boolean = false,
    val focusSession: FocusSession? = null,
    /** The parent locked every app (except the always-allowed ones) with no end time; survives restarts. */
    val allLocked: Boolean = false,
    /** When the user agreed to the accessibility disclosure; null until then. */
    val accessibilityConsentAt: Instant? = null,
    val aiEnabled: Boolean = false,
    /** Whether the usage warning glows along the screen edges; off leaves only the centre chip. */
    val usageWarningGlow: Boolean = true,
    /** Whether a timer tab with the time left docks on the screen edge while a limited app is open. */
    val appStartTimer: Boolean = true,
)
