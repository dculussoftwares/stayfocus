package com.dculus.stayfocused.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.dculus.stayfocused.core.sync.AuthRepository
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.RequestDecisionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/** Records the parent's answer to a request: the decision on the request and the command for the child's phone. */
internal class RequestAnswerer
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val decisions: RequestDecisionRepository,
    ) {
        /**
         * Answers [answer] as the signed-in parent. Returns true once the decision is handed over: the server has it,
         * or [timeoutMillis] passed without an answer (offline: Firestore keeps it). False if nobody is signed in or
         * the server refused it.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun answer(
            answer: RequestAnswer,
            timeoutMillis: Long = TIMEOUT_MILLIS,
        ): Boolean {
            val uid = auth.currentUser.first()?.uid ?: return false
            return try {
                // No answer in time means offline: Firestore keeps the batch and sends it later, so the notification
                // is handled either way (a second tap could not decide again).
                withTimeoutOrNull(timeoutMillis) {
                    decisions.decide(DeviceTarget(uid, answer.deviceId), answer.reqId, answer.minutes)
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Answering the request was refused")
                false
            }
        }

        companion object {
            /** A broadcast receiver has about ten seconds before the system may stop the process. */
            const val TIMEOUT_MILLIS = 8_000L
        }
    }

/**
 * The notification's Approve / No buttons. Runs the answer while the system holds the process (`goAsync`), then takes
 * the notification down. A refused or timed-out answer leaves the notification, and the amber card on Remote, in place.
 */
@AndroidEntryPoint
internal class RequestActionReceiver : BroadcastReceiver() {
    @Inject lateinit var answerer: RequestAnswerer

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ANSWER) return
        val answer = answerFrom(intent) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (answerer.answer(answer)) NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        const val ACTION_ANSWER = "com.dculus.stayfocused.action.ANSWER_REQUEST"
        const val EXTRA_DEVICE_ID = "deviceId"
        const val EXTRA_REQ_ID = "reqId"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        fun answerFrom(intent: Intent): RequestAnswer? {
            val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)?.takeIf { it.isNotBlank() }
            val reqId = intent.getStringExtra(EXTRA_REQ_ID)?.takeIf { it.isNotBlank() }
            val minutes = intent.getIntExtra(EXTRA_MINUTES, -1)
            return if (deviceId != null && reqId != null &&
                minutes >= 0
            ) {
                RequestAnswer(deviceId, reqId, minutes)
            } else {
                null
            }
        }
    }
}
