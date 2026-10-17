package com.dculus.stayfocused.push

import com.dculus.stayfocused.core.sync.PushTokenSync
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Receives the pushes of the `notifyParent` and `unlinkDevice` functions and keeps the FCM token registered. */
@AndroidEntryPoint
internal class PushService : FirebaseMessagingService() {
    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var tokenSync: PushTokenSync

    override fun onMessageReceived(message: RemoteMessage) {
        PushMessage.parse(message.data)?.let(notifier::show)
    }

    override fun onNewToken(token: String) {
        // The service may be stopped right after this returns; the write has to outlive it.
        processScope.launch { tokenSync.refresh() }
    }

    private companion object {
        val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
