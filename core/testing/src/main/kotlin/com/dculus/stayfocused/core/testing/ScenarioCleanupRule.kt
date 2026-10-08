package com.dculus.stayfocused.core.testing

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import org.junit.rules.ExternalResource

/**
 * Launches activities for a test and closes them after the test, outside the test body.
 *
 * Use it as an outer rule (lower `order`) than [ScreenshotOnFailureRule]: `@After` methods run before rule failure
 * callbacks, so closing the activity there would leave the failure screenshot showing the launcher instead of the app.
 */
class ScenarioCleanupRule : ExternalResource() {
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    fun <A : Activity> launch(activityClass: Class<A>): ActivityScenario<A> =
        ActivityScenario.launch(activityClass).also { scenarios += it }

    override fun after() {
        scenarios.forEach { it.close() }
        scenarios.clear()
    }
}
