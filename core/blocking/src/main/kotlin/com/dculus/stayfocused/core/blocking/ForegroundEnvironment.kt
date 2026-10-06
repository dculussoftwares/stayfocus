package com.dculus.stayfocused.core.blocking

/** Everything the tracker needs from the platform, so the filtering logic is testable on the JVM. */
interface ForegroundEnvironment {
    /** Package name of this app. */
    val ownPackage: String

    /** Packages of the enabled input methods (keyboards). */
    fun imePackages(): Set<String>

    /**
     * Whether [className] is an Activity of [packageName]. Returns `null` when that can't be determined
     * (lookup failed, e.g. package visibility); the tracker then treats the window as an activity.
     */
    fun isActivity(packageName: String, className: String): Boolean?

    fun nowMillis(): Long
}
