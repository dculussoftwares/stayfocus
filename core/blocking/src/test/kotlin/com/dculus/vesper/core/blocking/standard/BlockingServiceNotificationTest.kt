package com.dculus.vesper.core.blocking.standard

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.dculus.vesper.core.blocking.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockingServiceNotificationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun channelIsLowImportanceSoItStaysQuiet() {
        BlockingService.ensureChannel(app)
        val channel =
            app.getSystemService(NotificationManager::class.java).getNotificationChannel(BlockingService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test fun notificationIsOngoingAndSaysWhatTheServiceDoes() {
        val n = BlockingService.buildNotification(app)
        val extras = n.extras
        assertEquals(app.getString(R.string.blocking_service_title), extras.getCharSequence("android.title").toString())
        assertEquals(app.getString(R.string.blocking_service_text), extras.getCharSequence("android.text").toString())
        assertEquals(true, n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(BlockingService.CHANNEL_ID, n.channelId)
    }
}
