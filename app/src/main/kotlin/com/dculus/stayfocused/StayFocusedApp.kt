package com.dculus.stayfocused

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dculus.stayfocused.core.blocking.BlockingLog
import com.dculus.stayfocused.core.sync.AppCheckInstaller
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.PushTokenSync
import com.dculus.stayfocused.core.usage.UsageCacheScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /** Keeps the signed-in parent's FCM token registered, where `notifyParent` finds it (M8-05). */
    @Inject
    lateinit var pushTokenSync: PushTokenSync

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var usageCacheScheduler: UsageCacheScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        BlockingLog.init(this) // TEMPORARY: debug-only diagnostics; in release it deletes a leftover log
        appCheckInstaller.install()
        usageCacheScheduler.schedule()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { pushTokenSync.run() }
    }
}
