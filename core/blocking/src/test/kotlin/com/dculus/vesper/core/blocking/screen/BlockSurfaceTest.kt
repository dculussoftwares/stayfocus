package com.dculus.vesper.core.blocking.screen

import android.app.Application
import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockSurfaceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val surface = OverlaySurface(app)

    @Test fun overlaySurfaceUsesApplicationOverlayWindows() {
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, surface.windowType)
    }

    @Test fun overlaySurfaceIsAvailableOnlyWithDisplayOverOtherApps() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(surface.isAvailable())
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(surface.isAvailable())
    }

    @Test fun goHomeStartsTheHomeScreenInANewTask() {
        assertTrue(surface.goHome())
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun goHomeReportsFailureWhenTheStartIsRefused() {
        shadowOf(app).checkActivities(true)
        assertFalse(surface.goHome())
    }
}
