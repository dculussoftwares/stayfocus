package com.dculus.stayfocused.feature.block

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.LinkedDevicesRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockSummary
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.model.fallbackAppLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import java.time.Clock
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

/** First wizard step (type). */
const val WIZARD_STEP_TYPE = 1

private const val TAG = "WizardViewModel"
private const val ARG_TARGET = "target"
private const val ARG_PREFILL = "prefill"

/** One "ON" pill. Child phones are shown but disabled until device linking lands (M8-03). */
data class WizardTargetUi(
    val target: BlockTarget,
    val device: LinkedDevice?,
    val enabled: Boolean,
)

/** Which half of a "use, then rest" block the dial edits (prototype `cycleEdit`). */
enum class CycleEdit { USE, REST }

data class WizardUiState(
    /** False until the app list loaded and the route prefill was applied. */
    val ready: Boolean = false,
    val step: Int = WIZARD_STEP_TYPE,
    val draft: BlockDraft = BlockDraft(),
    val apps: List<TargetApp> = emptyList(),
    val targets: List<WizardTargetUi> = listOf(WizardTargetUi(BlockTarget.ThisPhone, null, enabled = true)),
    /** "Rather just describe it?" is offered only when the AI feature is on. */
    val aiAvailable: Boolean = false,
    val cycleEdit: CycleEdit = CycleEdit.USE,
    /** True while a save is in flight; further "Turn on block" taps are ignored. */
    val saving: Boolean = false,
) {
    val selectedCount: Int get() = draft.apps.size

    /** Display names of the selected apps, in selection order. */
    val appLabels: List<String>
        get() {
            val byPkg = apps.associate { it.pkg to it.label }
            return draft.apps.map { byPkg[it] ?: fallbackAppLabel(it) }
        }

    val summary: String get() = BlockSummary.sentence(draft, appLabels)
}

sealed interface WizardEvent {
    /** Leave the wizard. */
    data object Close : WizardEvent

    /** "Rather just describe it?": go to the Block tab in AI mode. */
    data object DescribeWithAi : WizardEvent

    /** Continue was pressed on step 2 with no app selected. */
    data object PickAtLeastOneApp : WizardEvent

    /** Saving failed; the wizard stays open so nothing is lost. */
    data object SaveFailed : WizardEvent

    /**
     * The block was saved and turned on: go to the Block tab with [deviceId] (null = this phone) selected and toast
     * "[name] is on · [targetName]"; [targetName] null means "This phone".
     */
    data class Saved(
        val deviceId: String?,
        val name: String,
        val targetName: String?,
    ) : WizardEvent
}

/**
 * State of the 3-step block wizard. Created from the route (`target`, `prefill` = a [BlockTemplate] id); the draft's
 * defaults match the prototype `openWizard`. The rules of step 3 and the save belong to M4-03 to M4-05.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WizardViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val targetApps: TargetAppsProvider,
        linkedDevices: LinkedDevicesRepository,
        settings: SettingsRepository,
        private val blocks: BlockRepository,
        private val clock: Clock,
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
                    // Keep the type and step the user already chose; only the apps depend on the target.
                    val apps =
                        template?.prefill(newTarget, installed)?.draft?.apps
                            ?: BlockDraft().apps.filterTo(linkedSetOf()) { it in installed }
                    s.copy(draft = s.draft.copy(target = newTarget, apps = apps))
                }
                target.value = newTarget
            }
        }

        /** Picking a type is selecting it and moving on to the apps. */
        fun pickType(type: BlockType) {
            mutableState.update {
                it.copy(draft = it.draft.copy(type = type), step = WIZARD_STEP_APPS, cycleEdit = CycleEdit.USE)
            }
        }

        fun toggleApp(pkg: String) {
            mutableState.update { s ->
                val apps = if (pkg in s.draft.apps) s.draft.apps - pkg else s.draft.apps + pkg
                s.copy(draft = s.draft.copy(apps = apps))
            }
        }

        /** The CTA: steps 1 and 2 advance (step 2 needs an app); step 3 saves the block and turns it on. */
        fun next() {
            val s = mutableState.value
            when {
                !s.ready || s.saving -> Unit
                s.step >= WIZARD_STEP_APPS && s.draft.apps.isEmpty() -> emit(WizardEvent.PickAtLeastOneApp)
                s.step < WIZARD_STEP_RULES -> mutableState.update { it.copy(step = it.step + 1) }
                else -> save(s)
            }
        }

        private fun save(s: WizardUiState) {
            val draft = s.draft
            val now = clock.instant()
            val info = BlockSummary.nameAndDescription(draft, s.appLabels)
            val block =
                Block(
                    id = UUID.randomUUID().toString(),
                    target = draft.target,
                    type = draft.type,
                    name = info.name,
                    apps = draft.apps,
                    limitMins = draft.mins.takeIf { draft.type == BlockType.LIMIT },
                    period = draft.period.takeIf { draft.type == BlockType.LIMIT },
                    useMins = draft.use.takeIf { draft.type == BlockType.CYCLE },
                    restMins = draft.rest.takeIf { draft.type == BlockType.CYCLE },
                    range = draft.range.takeIf { draft.type == BlockType.SCHEDULE },
                    durationMins = draft.now.takeIf { draft.type == BlockType.NOW },
                    startedAt = now.takeIf { draft.type == BlockType.NOW },
                    days = draft.days,
                    enabled = true,
                    createdAt = now,
                    source =
                        when {
                            draft.fromAi -> BlockSource.AI
                            template != null -> BlockSource.TEMPLATE
                            else -> BlockSource.MANUAL
                        },
                )
            val device = s.targets.firstOrNull { it.target == draft.target }?.device
            val deviceId = (draft.target as? BlockTarget.Device)?.deviceId
            mutableState.update { it.copy(saving = true) }
            viewModelScope.launch {
                try {
                    blocks.upsert(block)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Saving the block failed", e)
                    mutableState.update { it.copy(saving = false) }
                    emit(WizardEvent.SaveFailed)
                    return@launch
                }
                eventChannel.send(
                    WizardEvent.Saved(deviceId, info.name, if (deviceId == null) null else device?.name ?: deviceId),
                )
            }
        }

        fun setPeriod(period: LimitPeriod) = updateDraft { copy(period = period) }

        fun setMins(mins: Int) = updateDraft { copy(mins = mins) }

        fun setNow(mins: Int) = updateDraft { copy(now = mins) }

        /** Sets the value of the half of the cycle dial that is being edited. */
        fun setCycleValue(mins: Int) =
            mutableState.update {
                it.copy(
                    draft =
                        if (it.cycleEdit ==
                            CycleEdit.USE
                        ) {
                            it.draft.copy(use = mins)
                        } else {
                            it.draft.copy(rest = mins)
                        },
                )
            }

        fun selectCycleEdit(edit: CycleEdit) = mutableState.update { it.copy(cycleEdit = edit) }

        fun setRange(range: TimeRange) = updateDraft { copy(range = range) }

        fun toggleDay(day: DayOfWeek) = updateDraft { copy(days = DaysOfWeek(days.mask xor (1 shl (day.value - 1)))) }

        /** Entry point for the AI flow (M9-05): shows [draft] on the rules step under the "AI DRAFT" banner. */
        internal fun openAiDraft(draft: BlockDraft) =
            mutableState.update { it.copy(draft = draft.copy(fromAi = true), step = WIZARD_STEP_RULES) }

        /** The AI draft banner's "Change": back to the type step as a manual block. */
        fun changeType() =
            mutableState.update { it.copy(step = WIZARD_STEP_TYPE, draft = it.draft.copy(fromAi = false)) }

        private fun updateDraft(change: BlockDraft.() -> BlockDraft) =
            mutableState.update { it.copy(draft = it.draft.change()) }

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
