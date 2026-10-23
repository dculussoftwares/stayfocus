package com.dculus.stayfocused.feature.block

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.sync.BlockDescriber
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ARG_DESCRIBE_TARGET = "target"

/** State of the "Describe a block" screen. [targetName] is null for this phone. */
data class DescribeUiState(
    val text: String = "",
    val busy: Boolean = false,
    val error: Boolean = false,
    val targetName: String? = null,
) {
    /** "Build my block" needs a sentence. */
    val canBuild: Boolean get() = text.isNotBlank()
}

sealed interface DescribeEvent {
    /** The draft is ready: open the wizard on step 3 under the AI DRAFT banner. */
    data class OpenWizard(
        val target: String?,
    ) : DescribeEvent

    /** The red target tag was tapped but there is no other phone to switch to. */
    data object NoLinkedPhone : DescribeEvent
}

/**
 * Turns a sentence into a draft (Gemini, with the on-device fallback inside [BlockDescriber]) and hands it to the
 * wizard. Nothing is saved here; the block turns on only when the user taps "Turn on block" in the wizard.
 */
@HiltViewModel
class DescribeViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val installedApps: InstalledAppsRepository,
        private val linkedDevices: LinkedDevicesRepository,
        private val describer: BlockDescriber,
        private val handoff: AiDraftHandoff,
    ) : ViewModel() {
        private var target: BlockTarget =
            savedStateHandle.get<String>(ARG_DESCRIBE_TARGET)?.let { BlockTarget.Device(it) } ?: BlockTarget.ThisPhone
        private val mutableState = MutableStateFlow(DescribeUiState())
        val state: StateFlow<DescribeUiState> = mutableState.asStateFlow()

        private val eventChannel = Channel<DescribeEvent>(Channel.BUFFERED)
        val events: Flow<DescribeEvent> = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch { refreshName() }
        }

        private suspend fun refreshName() {
            val devices = linkedDevices.observeAll().first()
            val name =
                (target as? BlockTarget.Device)?.let { d -> devices.firstOrNull { it.id == d.deviceId }?.name }
            mutableState.update { it.copy(targetName = name) }
        }

        fun setText(text: String) = mutableState.update { it.copy(text = text, error = false) }

        /** The red target tag: switches to the next linked phone, or says there is none. */
        fun flipTarget() {
            viewModelScope.launch {
                val devices = linkedDevices.observeAll().first()
                if (devices.isEmpty()) {
                    eventChannel.send(DescribeEvent.NoLinkedPhone)
                    return@launch
                }
                val all = listOf<BlockTarget>(BlockTarget.ThisPhone) + devices.map { BlockTarget.Device(it.id) }
                target = all[(all.indexOf(target) + 1).mod(all.size)]
                refreshName()
            }
        }

        fun build() {
            val s = mutableState.value
            val sentence = s.text.trim()
            if (s.busy || sentence.isEmpty()) return
            mutableState.update { it.copy(busy = true, error = false) }
            val chosen = target
            viewModelScope.launch {
                var failed = false
                try {
                    val installed = installedApps.observeLaunchableApps().first()
                    val draft = describer.build(sentence, installed, chosen)
                    handoff.put(AiDraftRequest(sentence, draft))
                    eventChannel.send(DescribeEvent.OpenWizard((chosen as? BlockTarget.Device)?.deviceId))
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    if (e is CancellationException) throw e
                    failed = true
                    Log.w(TAG, "Could not build the block", e)
                } finally {
                    mutableState.update { it.copy(busy = false, error = failed) }
                }
            }
        }

        private companion object {
            const val TAG = "DescribeViewModel"
        }
    }
