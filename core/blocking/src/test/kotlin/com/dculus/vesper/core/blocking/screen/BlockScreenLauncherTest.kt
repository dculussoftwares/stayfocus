package com.dculus.vesper.core.blocking.screen

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.dculus.vesper.core.blocking.ForegroundAppTracker
import com.dculus.vesper.core.blocking.ForegroundEnvironment
import com.dculus.vesper.core.blocking.evaluator.BlockReason
import com.dculus.vesper.core.blocking.evaluator.Decision
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.time.Clock
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The standard engine's path: no accessibility service attached, so the launcher uses [OverlaySurface]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockScreenLauncherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private val env =
        object : ForegroundEnvironment {
            override val ownPackage = app.packageName

            override fun imePackages() = emptySet<String>()

            override fun isActivity(
                packageName: String,
                className: String,
            ): Boolean? = null

            override fun nowMillis() = 0L
        }
    private val launcher =
        BlockScreenLauncher(
            app,
            { _, initial, _ -> initial },
            Clock.systemUTC(),
            Optional.empty(),
            ForegroundAppTracker(env),
        )
    private val decision = Decision.Block(BlockReason.MANUAL_LOCK, null, null)

    @Test fun withoutAnySurfaceNothingIsLaunched() {
        ShadowSettings.setCanDrawOverlays(false)
        launcher.show("com.example", decision)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(false, launcher.isShowing())
    }

    @Test fun withDisplayOverOtherAppsGoesHomeAndStartsTheBlockActivity() {
        ShadowSettings.setCanDrawOverlays(true)
        launcher.show("com.example", decision)
        shadowOf(Looper.getMainLooper()).idle()
        // nextStartedActivity pops the most recent start first: the block screen came after going home.
        val block = shadowOf(app).nextStartedActivity
        assertEquals(BlockActivity::class.java.name, block.component?.className)
        val home = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, home.action)
        assertTrue(home.hasCategory(Intent.CATEGORY_HOME))
    }
}
