package com.dculus.stayfocused.feature.onboarding

import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayListingTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun resolvable(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, ResolveInfo())
    }

    @Test
    fun opensTheMarketListingWhenPlayIsInstalled() {
        shadowOf(app).checkActivities(true)
        resolvable(KIDS_MARKET_URI)
        openKidsListing(app)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(KIDS_MARKET_URI, started.dataString)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun fallsBackToTheHttpsListingWithoutPlay() {
        shadowOf(app).checkActivities(true)
        resolvable(KIDS_WEB_URI)
        openKidsListing(app)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(KIDS_WEB_URI, started.dataString)
    }

    @Test
    fun doesNotCrashWhenNothingCanOpenTheListing() {
        shadowOf(app).checkActivities(true)
        openKidsListing(app)
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
