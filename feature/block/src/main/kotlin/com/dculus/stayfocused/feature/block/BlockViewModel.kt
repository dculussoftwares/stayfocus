package com.dculus.stayfocused.feature.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.LockedAppsRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.LockedApp
import com.dculus.stayfocused.core.model.fallbackAppLabel
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
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

/** One row of the All apps tab. [locked] is a manual lock (indefinite) on the selected target. */
data class AppRowUi(
    val pkg: String,
    val label: String,
    val mins: Int,
    val opens: Int,
    val locked: Boolean,
)

data class BlockUiState(
    val targets: List<TargetUi> = listOf(TargetUi(BlockTarget.ThisPhone, null, 0)),
    val selected: BlockTarget = BlockTarget.ThisPhone,
    val tab: BlockTab = BlockTab.BLOCKS,
    val blocks: List<BlockRowUi> = emptyList(),
    /** All apps tab: launchable apps of the selected target, A to Z. */
    val apps: List<AppRowUi> = emptyList(),
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
    val locked: List<LockedApp>,
    val today: Map<String, AppUsageStat>,
    val aiEnabled: Boolean,
)

@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlockViewModel
    @Inject
    constructor(
        private val blocks: BlockRepository,
        linkedDevices: LinkedDevicesRepository,
        installedApps: InstalledAppsRepository,
        settings: SettingsRepository,
        private val lockedApps: LockedAppsRepository,
        usage: UsageRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState())
        private val lockMutex = Mutex()
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
                        lockedApps.observeAll(),
                        usage.today(),
                    ) { byTarget, installed, s, locked, today ->
                        Remote(devices, byTarget, installed, locked, today.apps.associateBy { it.pkg }, s.aiEnabled)
                    }
                }

        val state: StateFlow<BlockUiState> =
            combine(remote, local) { r, l -> r.toState(l) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BlockUiState())

        fun selectTarget(target: BlockTarget) = local.update { it.copy(selected = target) }

        fun selectTab(tab: BlockTab) = local.update { it.copy(tab = tab) }

        /** Opens the "Describe" panel (from the wizard's "Rather just describe it?"). */
        fun openAi() = local.update { it.copy(aiOpen = true, tab = BlockTab.BLOCKS) }

        fun toggleAi() = local.update { it.copy(aiOpen = !it.aiOpen) }

        fun setAiText(text: String) = local.update { it.copy(aiText = text) }

        fun setBlockEnabled(
            id: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch { blocks.setEnabled(id, enabled) }
        }

        /**
         * Flips the manual lock (indefinite) of [pkg] on the selected target. The current state is read from the
         * repository, under a lock, so quick repeated taps alternate lock and unlock instead of repeating one.
         */
        fun toggleAppLock(pkg: String) {
            val target = state.value.selected
            viewModelScope.launch {
                lockMutex.withLock {
                    val locked = lockedApps.observeByTarget(target).first().any { it.pkg == pkg }
                    if (locked) lockedApps.unlock(pkg, target) else lockedApps.lock(pkg, target)
                }
            }
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
            val lockedHere = locked.filter { it.target == selected }.mapTo(hashSetOf()) { it.pkg }
            val collator = Collator.getInstance()
            return BlockUiState(
                targets = targets,
                selected = selected,
                tab = l.tab,
                blocks =
                    blocksByTarget[selected].orEmpty().map { b ->
                        BlockRowUi(b, b.apps.map { labels[it] ?: fallbackAppLabel(it) })
                    },
                // A child phone has no app list until device linking (M8-03) syncs one.
                apps =
                    if (selected == BlockTarget.ThisPhone) {
                        installed
                            .map {
                                AppRowUi(
                                    it.pkg,
                                    it.label,
                                    today[it.pkg]?.mins ?: 0,
                                    today[it.pkg]?.opens ?: 0,
                                    it.pkg in lockedHere,
                                )
                            }.sortedWith(compareBy(collator) { it.label })
                    } else {
                        emptyList()
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
