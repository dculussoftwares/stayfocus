package com.dculus.stayfocused.feature.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BlockTab { BLOCKS, ALL_APPS }

/** One "Blocking on" card: This phone ([device] null) or a linked child phone. */
data class TargetUi(
    val target: BlockTarget,
    val device: LinkedDevice?,
    val activeCount: Int,
)

data class BlockRowUi(
    val block: Block,
    /** Display names of the blocked apps. */
    val appLabels: List<String>,
)

data class BlockUiState(
    val targets: List<TargetUi> = listOf(TargetUi(BlockTarget.ThisPhone, null, 0)),
    val selected: BlockTarget = BlockTarget.ThisPhone,
    val tab: BlockTab = BlockTab.BLOCKS,
    val blocks: List<BlockRowUi> = emptyList(),
    /** The "AI Describe" button is shown only when the AI feature is on. */
    val aiAvailable: Boolean = false,
    val aiOpen: Boolean = false,
    val aiText: String = "",
    val installedPackages: Set<String> = emptySet(),
) {
    val selectedTarget: TargetUi get() = targets.first { it.target == selected }
}

sealed interface BlockEvent {
    /** Open the block wizard for [target] (a child device id, null = this phone) from [prefill] (a template id). */
    data class OpenWizard(
        val target: String?,
        val prefill: String?,
    ) : BlockEvent
}

private data class LocalState(
    val selected: BlockTarget = BlockTarget.ThisPhone,
    val tab: BlockTab = BlockTab.BLOCKS,
    val aiOpen: Boolean = false,
    val aiText: String = "",
)

private data class Remote(
    val devices: List<LinkedDevice>,
    val blocksByTarget: Map<BlockTarget, List<Block>>,
    val installed: List<AppInfo>,
    val aiEnabled: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        private val blocks: BlockRepository,
        linkedDevices: LinkedDevicesRepository,
        installedApps: InstalledAppsRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState())
        private val eventChannel = Channel<BlockEvent>(Channel.BUFFERED)

        val events: Flow<BlockEvent> = eventChannel.receiveAsFlow()

        init {
            // An unlinked device must not become selected again if the same id is linked later.
            viewModelScope.launch {
                linkedDevices.observeAll().collect { devices ->
                    local.update { l ->
                        val gone =
                            l.selected is BlockTarget.Device && devices.none { BlockTarget.Device(it.id) == l.selected }
                        if (gone) l.copy(selected = BlockTarget.ThisPhone) else l
                    }
                }
            }
        }

        private val remote: Flow<Remote> =
            linkedDevices
                .observeAll()
                .flatMapLatest { devices ->
                    val targets = listOf<BlockTarget>(BlockTarget.ThisPhone) + devices.map { BlockTarget.Device(it.id) }
                    val perTarget =
                        combine(targets.map(blocks::observeByTarget)) { lists -> targets.zip(lists).toMap() }
                    combine(
                        perTarget,
                        installedApps.observeLaunchableApps(),
                        settings.settings,
                    ) { byTarget, installed, s ->
                        Remote(devices, byTarget, installed, s.aiEnabled)
                    }
                }

        val state: StateFlow<BlockUiState> =
            combine(remote, local) { r, l -> r.toState(l) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BlockUiState())

        fun selectTarget(target: BlockTarget) = local.update { it.copy(selected = target) }

        fun selectTab(tab: BlockTab) = local.update { it.copy(tab = tab) }

        fun toggleAi() = local.update { it.copy(aiOpen = !it.aiOpen) }

        fun setAiText(text: String) = local.update { it.copy(aiText = text) }

        fun setBlockEnabled(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch { blocks.setEnabled(id, enabled) }
        }

        fun newBlock() = emit(BlockEvent.OpenWizard(state.value.selected.deviceId(), prefill = null))

        fun useTemplate(template: BlockTemplate) =
            emit(BlockEvent.OpenWizard(state.value.selected.deviceId(), template.id))

        /** What [useTemplate] opens the wizard with, for the current target and installed apps. */
        fun templatePrefill(template: BlockTemplate): TemplatePrefill =
            with(state.value) { template.prefill(selected, installedPackages) }

        private fun emit(event: BlockEvent) {
            viewModelScope.launch { eventChannel.send(event) }
        }

        private fun Remote.toState(l: LocalState): BlockUiState {
            val targets =
                listOf(TargetUi(BlockTarget.ThisPhone, null, activeIn(BlockTarget.ThisPhone))) +
                    devices.map { TargetUi(BlockTarget.Device(it.id), it, activeIn(BlockTarget.Device(it.id))) }
            // A device that was unlinked while selected falls back to this phone.
            val selected = if (targets.any { it.target == l.selected }) l.selected else BlockTarget.ThisPhone
            val labels = installed.associate { it.pkg to it.label }
            return BlockUiState(
                targets = targets,
                selected = selected,
                tab = l.tab,
                blocks =
                    blocksByTarget[selected].orEmpty().map { b ->
                        BlockRowUi(b, b.apps.map { labels[it] ?: fallbackLabel(it) })
                    },
                aiAvailable = aiEnabled,
                aiOpen = aiEnabled && l.aiOpen,
                aiText = l.aiText,
                installedPackages = installed.mapTo(hashSetOf()) { it.pkg },
            )
        }

        private fun Remote.activeIn(target: BlockTarget) = blocksByTarget[target].orEmpty().count { it.enabled }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun BlockTarget.deviceId(): String? = (this as? BlockTarget.Device)?.deviceId

/** Name of an app without a launcher label (not installed): the prototype name, else the package tail. */
internal fun fallbackLabel(pkg: String): String {
    val known =
        KnownApps.packages.entries
            .firstOrNull { it.value == pkg }
            ?.key
    val raw = known ?: pkg.substringAfterLast('.')
    return raw.replaceFirstChar { it.uppercase() }
}
