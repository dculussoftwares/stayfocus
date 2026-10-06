package com.dculus.stayfocused.core.blocking

/** The app currently in front of the user and when it came to the front (epoch millis). */
data class ForegroundApp(
    val packageName: String,
    val sinceMillis: Long,
)
