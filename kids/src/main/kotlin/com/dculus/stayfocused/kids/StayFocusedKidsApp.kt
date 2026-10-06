package com.dculus.stayfocused.kids

import android.app.Application
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StayFocusedKidsApp : Application() {
    @Inject
    lateinit var appCheckInstaller: AppCheckInstaller

    override fun onCreate() {
        super.onCreate()
        appCheckInstaller.install()
    }
}
