package com.dculus.stayfocused.core.model

import java.time.Instant

/** App-wide preferences stored in the `settings` DataStore. Break sessions are owned by the break repository. */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val accountSkipped: Boolean = false,
    val focusSession: FocusSession? = null,
    /** When the user agreed to the accessibility disclosure; null until then. */
    val accessibilityConsentAt: Instant? = null,
    val aiEnabled: Boolean = false,
)
