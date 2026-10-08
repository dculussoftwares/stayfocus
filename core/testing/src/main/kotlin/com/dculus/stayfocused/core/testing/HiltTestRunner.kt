package com.dculus.stayfocused.core.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instrumentation runner for every module's `androidTest`: swaps the real `Application` for [HiltTestApplication] so
 * `@HiltAndroidTest` classes get a test component (and the app's `onCreate` side effects don't run).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
