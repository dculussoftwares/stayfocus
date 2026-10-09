package com.dculus.stayfocused.feature.block

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.LinkedDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** First wizard step (type). */
const val WIZARD_STEP_TYPE = 1

private const val ARG_TARGET = "target"
private const val ARG_PREFILL = "prefill"

/** One "ON" pill. Child phones are shown but disabled until device linking lands (M8-03). */
data class WizardTargetUi(
    val target: BlockTarget,
    val device: LinkedDevice?,
    val enabled: Boolean,
)

data class WizardUiState(
    /** False until the app list loaded and the route prefill was applied. */
    val ready: Boolean = false,
    val step: Int = WIZARD_STEP_TYPE,
    val draft: BlockDraft = BlockDraft(),
    val apps: List<TargetApp> = emptyList(),
    val targets: List<WizardTargetUi> = listOf(WizardTargetUi(BlockTarget.ThisPhone, null, enabled = true)),
    /** "Rather just describe it?" is offered only when the AI feature is on. */
    val aiAvailable: Boolean = false,
) {
    val selectedCount: Int get() = draft.apps.size
}

sealed interface WizardEvent {
    /** Leave the wizard. */
    data object Close : WizardEvent

    /** "Rather just describe it?": go to the Block tab in AI mode. */
    data object DescribeWithAi : WizardEvent

    /** Continue was pressed on step 2 with no app selected. */
    data object PickAtLeastOneApp : WizardEvent
}

/**
 * State of the 3-step block wizard. Created from the route (`target`, `prefill` = a [BlockTemplate] id); the draft's
 * defaults match the prototype `openWizard`. The rules of step 3 and the save belong to M4-03 to M4-05.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WizardViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val targetApps: TargetAppsProvider,
        linkedDevices: LinkedDevicesRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        private val initialTarget: BlockTarget =
            savedStateHandle.get<String>(ARG_TARGET)?.let { BlockTarget.Device(it) } ?: BlockTarget.ThisPhone
        private val target = MutableStateFlow(initialTarget)
        private val template: BlockTemplate? = BlockTemplate.fromId(savedStateHandle.get<String>(ARG_PREFILL))

        private val mutableState = MutableStateFlow(WizardUiState(draft = BlockDraft(target = initialTarget)))
        val state: StateFlow<WizardUiState> = mutableState.asStateFlow()

        private val eventChannel = Channel<WizardEvent>(Channel.BUFFERED)
        val events: Flow<WizardEvent> = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                // The first app list decides which template/default apps exist on this target.
                val first = targetApps.apps(initialTarget).first()
                mutableState.update { applyPrefill(it, first) }
                target.flatMapLatest { targetApps.apps(it) }.collect { apps ->
                    // An app that disappears (uninstalled) must not stay selected.
                    val available = apps.mapTo(hashSetOf()) { it.pkg }
                    mutableState.update { s ->
                        s.copy(
                            apps = apps,
                            draft =
                                s.draft.copy(
                                    apps =
                                        s.draft.apps.filterTo(linkedSetOf()) {
                                            it in
                                                available
                                        },
                                ),
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.settings.collect { s -> mutableState.update { it.copy(aiAvailable = s.aiEnabled) } }
            }
            viewModelScope.launch {
                linkedDevices.observeAll().collect { devices ->
                    val pills =
                        listOf(WizardTargetUi(BlockTarget.ThisPhone, null, enabled = true)) +
                            devices.map { WizardTargetUi(BlockTarget.Device(it.id), it, enabled = false) }
                    mutableState.update { it.copy(targets = pills) }
                }
            }
        }

        private fun applyPrefill(
            current: WizardUiState,
            apps: List<TargetApp>,
        ): WizardUiState {
            val installed = apps.mapTo(hashSetOf()) { it.pkg }
            val prefill = template?.prefill(initialTarget, installed)
            if (prefill != null) {
                return current.copy(ready = true, apps = apps, draft = prefill.draft, step = prefill.startStep)
            }
            val draft = current.draft
            val available = draft.apps.filterTo(linkedSetOf()) { it in installed }
            return current.copy(ready = true, apps = apps, draft = draft.copy(apps = available))
        }

        /** An enabled "ON" pill: re-targets the draft; the default apps are re-resolved for the new target. */
        fun selectTarget(newTarget: BlockTarget) {
            val pill = mutableState.value.targets.firstOrNull { it.target == newTarget }
            if (pill == null || !pill.enabled || newTarget == mutableState.value.draft.target) return
            viewModelScope.launch {
                val installed = targetApps.apps(newTarget).first().mapTo(hashSetOf()) { it.pkg }
                mutableState.update { s ->
                    val prefill = template?.prefill(newTarget, installed)
                    if (prefill != null) {
                        s.copy(draft = prefill.draft, step = prefill.startStep)
                    } else {
                        val apps = BlockDraft().apps.filterTo(linkedSetOf()) { it in installed }
                        s.copy(draft = s.draft.copy(target = newTarget, apps = apps))
                    }
                }
                target.value = newTarget
            }
        }

        /** Picking a type is selecting it and moving on to the apps. */
        fun pickType(type: BlockType) {
            mutableState.update { it.copy(draft = it.draft.copy(type = type), step = WIZARD_STEP_APPS) }
        }

        fun toggleApp(pkg: String) {
            mutableState.update { s ->
                val apps = if (pkg in s.draft.apps) s.draft.apps - pkg else s.draft.apps + pkg
                s.copy(draft = s.draft.copy(apps = apps))
            }
        }

        /** The CTA: steps 1 and 2 advance (step 2 needs an app); the step 3 save is added by M4-05. */
        fun next() {
            val s = mutableState.value
            when {
                !s.ready -> Unit
                s.step == WIZARD_STEP_APPS && s.draft.apps.isEmpty() -> emit(WizardEvent.PickAtLeastOneApp)
                s.step < WIZARD_STEP_RULES -> mutableState.update { it.copy(step = it.step + 1) }
            }
        }

        /** Back: one step earlier, or close from step 1. */
        fun back() {
            val s = mutableState.value
            if (s.step > WIZARD_STEP_TYPE) {
                mutableState.update { it.copy(step = it.step - 1) }
            } else {
                emit(WizardEvent.Close)
            }
        }

        fun describeWithAi() = emit(WizardEvent.DescribeWithAi)

        private fun emit(event: WizardEvent) {
            viewModelScope.launch { eventChannel.send(event) }
        }
    }
