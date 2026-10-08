package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.TimeRange

/** Wizard step that picks the apps (prototype `wizStep` 2). */
const val WIZARD_STEP_APPS = 2

/** Wizard step that sets the rules (prototype `wizStep` 3). */
const val WIZARD_STEP_RULES = 3

/** The four "Start from a template" cards (prototype `templates`). [id] is the `BlockWizard.prefill` value. */
enum class BlockTemplate(
    val id: String,
    val type: BlockType,
    /** Prototype app ids, mapped to packages through [KnownApps]. */
    val appIds: List<String>,
) {
    MINDFUL_SCROLLING("mindful_scrolling", BlockType.CYCLE, listOf("instagram", "youtube", "reddit")),
    STUDY_TIME("study_time", BlockType.SCHEDULE, listOf("instagram", "youtube", "reddit")),
    SOCIAL_LIMIT("social_limit", BlockType.LIMIT, listOf("instagram", "reddit", "x")),
    BEDTIME("bedtime", BlockType.SCHEDULE, KnownApps.packages.keys.toList()),
    ;

    companion object {
        fun fromId(id: String?): BlockTemplate? = entries.firstOrNull { it.id == id }
    }
}

/** The wizard state a template opens with, and the step it opens at. */
data class TemplatePrefill(
    val draft: BlockDraft,
    val startStep: Int,
)

/**
 * The prototype's `openWizard({...})` for this template. Template apps that are not in [installedPackages] are
 * dropped; with none left there is nothing to review, so the wizard opens at the app picker instead of the rules.
 */
fun BlockTemplate.prefill(
    target: BlockTarget,
    installedPackages: Set<String>,
): TemplatePrefill {
    val apps =
        appIds
            .mapNotNull { KnownApps.packages[it] }
            .filterTo(linkedSetOf()) { it in installedPackages }
    val base = BlockDraft(target = target, type = type, apps = apps)
    val draft =
        when (this) {
            BlockTemplate.MINDFUL_SCROLLING -> base.copy(use = 10, rest = 30, days = DaysOfWeek.ALL)
            BlockTemplate.STUDY_TIME -> base.copy(range = TimeRange.parse("16:00–20:00"), days = DaysOfWeek.WEEKDAYS)
            BlockTemplate.SOCIAL_LIMIT -> base.copy(mins = 30, days = DaysOfWeek.ALL)
            BlockTemplate.BEDTIME -> base.copy(range = TimeRange.parse("22:00–07:00"), days = DaysOfWeek.ALL)
        }
    return TemplatePrefill(draft, if (apps.isEmpty()) WIZARD_STEP_APPS else WIZARD_STEP_RULES)
}
