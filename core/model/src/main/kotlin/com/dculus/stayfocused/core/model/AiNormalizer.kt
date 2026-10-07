package com.dculus.stayfocused.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/** Port of the prototype `runAi` normalisation and `toDays`. Never throws; unusable fields fall back to defaults. */
object AiNormalizer {
    private val RANGE_REGEX = Regex("^\\d\\d:\\d\\d–\\d\\d:\\d\\d$")
    private val WEEKDAY_WORDS = Regex("weekday|school day|work day|workday")
    private val WEEKEND_WORDS = Regex("weekend")
    private val NAMED_DAY =
        Regex(
            "\\b(?:mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|" +
                "fri(?:day)?|sat(?:urday)?|sun(?:day)?)\\b",
        )
    private val DAY_KEYS =
        listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").zip(DayOfWeek.entries).toMap()
    private val FALLBACK_IDS = listOf("instagram", "youtube")
    private val DEFAULT_RANGE = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))
    private const val DEFAULT_MINS = 30
    private const val DEFAULT_USE = 10
    private const val DEFAULT_REST = 30
    private const val DEFAULT_NOW = 60
    private const val DAY_KEY_LENGTH = 3

    /**
     * @param sentence what the user typed (used for the weekday/weekend keywords).
     * @param installed apps on the target device; app names are matched to these by label, or via [KnownApps].
     */
    fun normalize(
        result: AiBlockResult,
        sentence: String,
        installed: List<AppInfo>,
        target: BlockTarget = BlockTarget.ThisPhone,
    ): BlockDraft =
        BlockDraft(
            target = target,
            type = parseType(result.type),
            apps = resolveApps(result.apps.orEmpty(), installed),
            period = if (result.period == "hourly") LimitPeriod.HOURLY else LimitPeriod.DAILY,
            mins = amount(result.mins, DEFAULT_MINS, DialConfigs.Limit),
            use = amount(result.use, DEFAULT_USE, DialConfigs.CycleUse),
            rest = amount(result.rest, DEFAULT_REST, DialConfigs.CycleRest),
            now = amount(result.now, DEFAULT_NOW, DialConfigs.BlockNow),
            range = parseRange(result.range),
            days = toDays(result.days, sentence),
            fromAi = true,
            aiNote = result.note.orEmpty(),
        )

    internal fun parseType(raw: String?): BlockType =
        when (raw) {
            "cycle" -> BlockType.CYCLE
            "schedule" -> BlockType.SCHEDULE
            "now" -> BlockType.NOW
            else -> BlockType.LIMIT
        }

    /** `max(1, round(Number(v) || default))`, additionally clamped to the dial max. */
    internal fun amount(
        value: Double?,
        default: Int,
        dial: DialConfig,
    ): Int {
        val v = if (value == null || value.isNaN() || value == 0.0) default.toDouble() else value
        return v.roundToInt().coerceIn(1, dial.max)
    }

    internal fun parseRange(raw: String?): TimeRange {
        if (raw != null && RANGE_REGEX.matches(raw)) {
            try {
                // An empty window (start == end) would never block, so treat it as unusable.
                return TimeRange.parse(raw).takeIf { it.start != it.end } ?: DEFAULT_RANGE
            } catch (
                @Suppress("SwallowedException") e: IllegalArgumentException,
            ) {
                // Right shape but not real times (e.g. "25:99–10:00"): use the default.
            }
        }
        return DEFAULT_RANGE
    }

    internal fun toDays(
        days: List<String>?,
        sentence: String,
    ): DaysOfWeek {
        val t = sentence.lowercase(Locale.ROOT)
        val chosen =
            days
                .orEmpty()
                .mapNotNull { DAY_KEYS[it.take(DAY_KEY_LENGTH).lowercase(Locale.ROOT)] }
                .toTypedArray()
        val group =
            when {
                WEEKDAY_WORDS.containsMatchIn(t) -> DaysOfWeek.WEEKDAYS
                WEEKEND_WORDS.containsMatchIn(t) -> DaysOfWeek.WEEKENDS
                else -> null
            }
        return when {
            // "weekends and Monday": keep the group and add the days the sentence names on top of it.
            group != null && NAMED_DAY.containsMatchIn(t) -> DaysOfWeek(group.mask or DaysOfWeek.of(*chosen).mask)

            group != null -> group

            chosen.isEmpty() -> DaysOfWeek.ALL

            else -> DaysOfWeek.of(*chosen)
        }
    }

    internal fun resolveApps(
        names: List<String>,
        installed: List<AppInfo>,
    ): Set<String> {
        val installedPkgs = installed.map { it.pkg }.toSet()
        val resolved =
            names
                .mapNotNull { resolveApp(it.trim().lowercase(Locale.ROOT), installed, installedPkgs) }
                .toCollection(linkedSetOf())
        if (resolved.isNotEmpty()) return resolved
        val fallback = FALLBACK_IDS.map { KnownApps.packages.getValue(it) }
        // Only apps that are actually installed; empty if neither is (the user picks apps in the wizard).
        return fallback.filter { it in installedPkgs }.toCollection(linkedSetOf())
    }

    private fun resolveApp(
        name: String,
        installed: List<AppInfo>,
        installedPkgs: Set<String>,
    ): String? {
        if (name.isEmpty()) return null
        val known = KnownApps.packages[name]
        return if (known != null && known in installedPkgs) {
            known
        } else {
            installed.firstOrNull { it.label.lowercase(Locale.ROOT) == name }?.pkg
        }
    }
}
