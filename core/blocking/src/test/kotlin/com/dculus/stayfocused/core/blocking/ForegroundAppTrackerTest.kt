package com.dculus.stayfocused.core.blocking

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

private class FakeEnv : ForegroundEnvironment {
    override val ownPackage = "com.dculus.stayfocused"
    var imes = setOf("com.keyboard")
    var activityAnswer: (String, String) -> Boolean? = { _, _ -> true }
    var now = 1_000L

    override fun imePackages() = imes

    override fun isActivity(
        packageName: String,
        className: String,
    ) = activityAnswer(packageName, className)

    override fun nowMillis() = now
}

class ForegroundAppTrackerTest {
    private val env = FakeEnv()
    private val tracker = ForegroundAppTracker(env)

    @Test fun startsEmpty() = assertNull(tracker.foreground.value)

    @Test fun recordsPackageAndTime() {
        env.now = 42
        tracker.onWindowStateChanged("com.a", "com.a.Main")
        assertEquals(ForegroundApp("com.a", 42), tracker.foreground.value)
    }

    @Test fun transitionsLauncherToAppToAnotherApp() {
        tracker.onWindowStateChanged("com.launcher", "Launcher")
        tracker.onWindowStateChanged("com.a", "A")
        env.now = 2_000
        tracker.onWindowStateChanged("com.b", "B")
        assertEquals(ForegroundApp("com.b", 2_000), tracker.foreground.value)
    }

    @Test fun duplicateKeepsOriginalSince() {
        tracker.onWindowStateChanged("com.a", "A")
        val first = tracker.foreground.value
        env.now = 5_000
        tracker.onWindowStateChanged("com.a", "A2")
        assertSame(first, tracker.foreground.value)
    }

    @Test fun systemUiIsIgnored() {
        tracker.onWindowStateChanged("com.a", "A")
        tracker.onWindowStateChanged("com.android.systemui", "NotificationShade")
        assertEquals("com.a", tracker.foreground.value?.packageName)
    }

    @Test fun ownBlockScreenIsIgnoredButOwnOtherScreensCount() {
        tracker.onWindowStateChanged("com.a", "A")
        tracker.onWindowStateChanged(env.ownPackage, ForegroundAppTracker.BLOCK_SCREEN_CLASS)
        assertEquals("com.a", tracker.foreground.value?.packageName)
        tracker.onWindowStateChanged(env.ownPackage, "com.dculus.stayfocused.MainActivity")
        assertEquals(env.ownPackage, tracker.foreground.value?.packageName)
    }

    @Test fun keyboardIsIgnored() {
        tracker.onWindowStateChanged("com.a", "A")
        tracker.onWindowStateChanged("com.keyboard", "Ime")
        assertEquals("com.a", tracker.foreground.value?.packageName)
    }

    @Test fun nonActivityWindowIsIgnored() {
        tracker.onWindowStateChanged("com.a", "A")
        env.activityAnswer = { _, cls -> cls == "A" || cls == "B" }
        tracker.onWindowStateChanged("com.b", "android.widget.FrameLayout")
        assertEquals("com.a", tracker.foreground.value?.packageName)
        tracker.onWindowStateChanged("com.b", "B")
        assertEquals("com.b", tracker.foreground.value?.packageName)
    }

    @Test fun failedActivityLookupDoesNotDropTheEvent() {
        env.activityAnswer = { _, _ -> null }
        tracker.onWindowStateChanged("com.a", "A")
        assertEquals("com.a", tracker.foreground.value?.packageName)
    }

    @Test fun nullOrEmptyFieldsAreIgnored() {
        tracker.onWindowStateChanged(null, "A")
        tracker.onWindowStateChanged("com.a", null)
        tracker.onWindowStateChanged("", "A")
        assertNull(tracker.foreground.value)
    }

    @Test fun screenOffClearsAndSameAppIsRecordedAgainAfterwards() {
        tracker.onWindowStateChanged("com.a", "A")
        tracker.onScreenOff()
        assertNull(tracker.foreground.value)
        env.now = 9_000
        tracker.onWindowStateChanged("com.a", "A")
        assertEquals(ForegroundApp("com.a", 9_000), tracker.foreground.value)
    }
}
