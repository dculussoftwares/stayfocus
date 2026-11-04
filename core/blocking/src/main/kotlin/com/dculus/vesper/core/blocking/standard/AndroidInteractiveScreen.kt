package com.dculus.vesper.core.blocking.standard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Screen on/off from [PowerManager.isInteractive] plus `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`. */
class AndroidInteractiveScreen
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : InteractiveScreen {
        override fun interactive(): Flow<Boolean> =
            callbackFlow {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(intent.action == Intent.ACTION_SCREEN_ON)
                        }
                    }
                context.registerReceiver(
                    receiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                    },
                )
                trySend(power.isInteractive)
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
    }
