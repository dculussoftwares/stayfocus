package com.dculus.stayfocused.core.testing

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream

/**
 * Saves a screenshot when an instrumented test fails. AGP collects the files from `additionalTestOutputDir` into the
 * test results (`build/outputs/androidTest-results`), which CI uploads.
 */
class ScreenshotOnFailureRule : TestWatcher() {
    override fun failed(
        e: Throwable?,
        description: Description,
    ) {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val dir =
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: instrumentation.targetContext.cacheDir.path
            File(dir).mkdirs()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap == null) {
                Log.w(TAG, "No screenshot available for ${description.displayName}")
                return
            }
            FileOutputStream(File(dir, "${description.className}_${description.methodName}.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") error: Exception,
        ) {
            // Never mask the real test failure, but leave a trace of why there is no screenshot.
            Log.w(TAG, "Could not save a failure screenshot for ${description.displayName}", error)
        }
    }

    private companion object {
        const val PNG_QUALITY = 100
        const val TAG = "ScreenshotOnFailure"
    }
}
