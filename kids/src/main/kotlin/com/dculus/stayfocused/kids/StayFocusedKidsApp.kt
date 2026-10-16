package com.dculus.stayfocused.kids

import android.app.Application
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import com.dculus.stayfocused.kids.monitoring.MonitoringLauncher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StayFocusedKidsApp : Application() {
    @Inject
    lateinit var appCheckInstaller: AppCheckInstaller

    @Inject
    lateinit var monitoringLauncher: MonitoringLauncher

    override fun onCreate() {
        super.onCreate()
        appCheckInstaller.install()
        // No-op until a parent is linked; afterwards keeps the "Managed by" notification up.
        monitoringLauncher.sync()
    }
}
