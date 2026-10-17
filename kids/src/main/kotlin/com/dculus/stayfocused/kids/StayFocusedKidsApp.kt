package com.dculus.stayfocused.kids

import android.app.Application
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import com.dculus.stayfocused.kids.monitoring.MonitoringLauncher
import com.dculus.stayfocused.kids.monitoring.ParentRulesCleaner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StayFocusedKidsApp : Application() {
    @Inject
    lateinit var appCheckInstaller: AppCheckInstaller

    @Inject
    lateinit var monitoringLauncher: MonitoringLauncher

    @Inject
    internal lateinit var rulesCleaner: ParentRulesCleaner

    override fun onCreate() {
        super.onCreate()
        appCheckInstaller.install()
        // No-op until a parent is linked; afterwards keeps the "Managed by" notification up.
        monitoringLauncher.sync()
        // An unlink the process died in the middle of must still end the parent's rules.
        rulesCleaner.resume()
    }
}
