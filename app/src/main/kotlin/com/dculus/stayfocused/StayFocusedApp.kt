package com.dculus.stayfocused

import android.app.Application
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StayFocusedApp : Application() {
    @Inject
    lateinit var appCheckInstaller: AppCheckInstaller

    override fun onCreate() {
        super.onCreate()
        appCheckInstaller.install()
    }
}
