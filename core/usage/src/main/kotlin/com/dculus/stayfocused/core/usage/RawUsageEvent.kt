package com.dculus.stayfocused.core.usage

/** The platform `UsageEvents.Event` types the aggregation cares about, decoupled from the SDK so it runs on the JVM. */
enum class RawEventType {
    /** `ACTIVITY_RESUMED` (API 29+) or `MOVE_TO_FOREGROUND` (API 26-28). */
    ACTIVITY_RESUMED,

    /** `ACTIVITY_PAUSED` (API 29+) or `MOVE_TO_BACKGROUND` (API 26-28). */
    ACTIVITY_PAUSED,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,

    /** `KEYGUARD_HIDDEN` (API 28+). */
    KEYGUARD_HIDDEN,
    DEVICE_SHUTDOWN,
}

/** One usage event. [className] is empty for events that aren't tied to an activity. */
data class RawUsageEvent(
    val pkg: String,
    val className: String,
    val type: RawEventType,
    val timeMillis: Long,
)

/** Which event counts as "the phone was unlocked". */
enum class UnlockSignal {
    /** API 28+: the keyguard was dismissed. */
    KEYGUARD_HIDDEN,

    /**
     * API 26-27 fallback: the screen turned on. `KEYGUARD_HIDDEN` doesn't exist there, so this over-counts
     * wake-ups on devices without a secure lock screen and counts a wake-up that isn't followed by an unlock.
     */
    SCREEN_INTERACTIVE,
}
