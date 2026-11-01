package com.dculus.stayfocused.core.blocking.standard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.dculus.stayfocused.core.blocking.engine.EngineHealthMonitor
import com.dculus.stayfocused.core.blocking.engine.EngineMode
import com.dculus.stayfocused.core.blocking.engine.EngineModeArbiter
import com.dculus.stayfocused.core.blocking.permissions.PermissionsRepository
import com.dculus.stayfocused.core.model.Permissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandardEngineStarterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private var permissions = Permissions(usage = true, accessibility = false, overlay = true, notifications = true)
    private var declared = true
    private val health = EngineHealthMonitor(Clock.systemUTC())
    private val arbiter =
        EngineModeArbiter(
            object : PermissionsRepository {
                override fun observe(): Flow<Permissions> = emptyFlow()

                override fun current() = permissions

                override fun needsRestrictedSettingsGuidance() = false
            },
            health,
            { declared },
        )
    private val starter = StandardEngineStarter(app, arbiter)

    @Test fun startsTheServiceWhenUsageAndOverlayAreGranted() {
        starter.sync()
        assertEquals(EngineMode.STANDARD, arbiter.mode.value)
        assertEquals(EngineMode.STANDARD, health.health.value.mode)
        val started = shadowOf(app).nextStartedService
        assertEquals(BlockingService::class.java.name, started.component?.className)
    }

    @Test fun doesNothingWithoutDisplayOverOtherApps() {
        permissions = permissions.copy(overlay = false)
        starter.sync()
        assertEquals(EngineMode.OFF, arbiter.mode.value)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun doesNothingWhereTheServiceIsNotDeclared() {
        declared = false
        starter.sync()
        assertEquals(EngineMode.OFF, arbiter.mode.value)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun androidSupportSeesTheDeclaredService() {
        // Robolectric merges the library manifest: BlockingService is only declared by :app, never by :core:blocking.
        assertEquals(false, AndroidStandardSupport(app).declared())
    }
}
