package com.dculus.stayfocused.core.blocking.engine

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBreakNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun postsBreakOverOnTheBreaksChannelWhenPermitted() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidBreakNotifier(app).breakEnded()

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Break over", posted.extras.getString("android.title"))
        assertEquals(AndroidBreakNotifier.CHANNEL_ID, posted.channelId)
        assertNotNull(manager.getNotificationChannel("breaks"))
    }

    @Test fun postsNothingWithoutTheNotificationPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidBreakNotifier(app).breakEnded()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
