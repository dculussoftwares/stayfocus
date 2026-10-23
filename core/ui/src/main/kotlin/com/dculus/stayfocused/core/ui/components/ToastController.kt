package com.dculus.stayfocused.core.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val SF_TOAST_DURATION_MS = 2600L

/**
 * Holds the one visible toast. [show] replaces the current message (like the prototype's `toastMsg`)
 * and restarts the 2.6 s auto-dismiss timer. Not thread-safe: call from the main thread.
 */
@Stable
class ToastController(
    private val scope: CoroutineScope,
    private val durationMs: Long = SF_TOAST_DURATION_MS,
) {
    /** The message currently shown, or null. */
    var message: String? by mutableStateOf(null)
        private set

    private var dismissJob: Job? = null

    fun show(message: String) {
        dismissJob?.cancel()
        this.message = message
        dismissJob =
            scope.launch {
                delay(durationMs)
                this@ToastController.message = null
            }
    }

    fun dismiss() {
        dismissJob?.cancel()
        message = null
    }
}
