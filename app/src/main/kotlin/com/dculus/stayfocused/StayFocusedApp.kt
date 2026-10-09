package com.dculus.stayfocused

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.usage.UsageCacheScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StayFocusedApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var appCheckInstaller: AppCheckInstaller

    /** Injected so the repository exists from app start: it restores the session and repairs a missing profile. */
    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var usageCacheScheduler: UsageCacheScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appCheckInstaller.install()
        usageCacheScheduler.schedule()
    }
}
