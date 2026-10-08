package com.dculus.stayfocused.core.blocking.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.dculus.stayfocused.core.usage.UsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPermissionChecksTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private class FakeUsage(
        private val granted: Boolean,
    ) : UsageAccess {
        override fun isGranted() = granted

        override fun settingsIntent() = Intent()
    }

    @Test fun readsUsageFromUsageAccess() {
        val read = AndroidPermissionChecks(context, FakeUsage(true)).read()
        assertTrue(read.usage)
        assertFalse(read.accessibility)
        assertFalse(AndroidPermissionChecks(context, FakeUsage(false)).read().usage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun resumeSignalEmitsWhenAnActivityResumes() =
        runTest {
            var count = 0
            val job =
                ActivityResumeSignal(context)
                    .resumes()
                    .onEach { count++ }
                    .launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined))
            val controller =
                Robolectric
                    .buildActivity(Activity::class.java)
                    .create()
                    .start()
                    .resume()
            assertEquals(2, count) // initial signal on subscription + the resume
            controller.pause().stop().destroy()
            job.cancel()
        }
}
