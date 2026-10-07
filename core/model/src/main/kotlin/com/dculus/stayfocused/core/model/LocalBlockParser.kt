package com.dculus.stayfocused.core.model

import java.util.Locale

/**
 * On-device fallback for the AI block builder: a rule-based port (and improvement) of the prototype `localParse`.
 * Used when Gemini is unavailable (offline, Firebase not configured, error). Pure and never throws.
 *
 * The result is an [AiBlockResult] so it goes through the same [AiNormalizer] as the model output.
 * `apps` holds [KnownApps] ids, or the lower-cased label of an installed app that is not a known app.
 */
object LocalBlockParser {
    private val DEFAULT_APPS = listOf("instagram", "youtube")
    private const val DEFAULT_MINS = 30.0
    private const val DEFAULT_NOW = 60.0

    private val STRONG_CYCLE =
        Regex("\\bthen\\b|\\brest\\b|\\bon\\b.*\\boff\\b|\\b(?:every|each) time\\b")
    private val WEAK_CYCLE = Regex("\\b(?:lock|block)\\b.*\\bfor\\b|\\bafter\\b")
    private val DAILY = Regex("\\b(?:a|per|each|every)\\s+day\\b|\\bdaily\\b|\\btoday\\b")
    private val LOCK_WORD = Regex("\\b(?:lock|block|stop|ban)\\b")
    private val ALLOWANCE_WORD = Regex("\\b(?:after|allow|allowed|use|let)\\b")
    private val NOW_WORDS =
        Regex(
            "\\bnow\\b|\\bnext\\s+(?:\\d|an?\\s|half|quarter|[a-z]+ (?:min|hour|hr))|" +
                "right away|straight away|immediately",
        )
    private val LOCK_FOR = Regex("\\b(?:lock|block|stop|ban|no)\\b.*\\bfor\\b")
    private val LIMIT_WORDS = Regex("\\b(?:limit|allow|allowed|max|at most|only|cap|no more than|up to)\\b")

    /** Tries each rule in the prototype's order: cycle, schedule, now, then limit. */
    fun parse(
        sentence: String,
        installed: List<AppInfo> = emptyList(),
    ): AiBlockResult {
        val text = sentence.lowercase(Locale.ROOT).replace('–', '-').replace('—', '-')
        val apps = ParserApps.find(text, installed).ifEmpty { DEFAULT_APPS }
        val days = ParserDays.find(text)
        var durations = ParserDurations.find(text)
        val strongCycle = STRONG_CYCLE.containsMatchIn(text) && durations.size >= 2
        val hourly = if (strongCycle) null else ParserDurations.hourly(text, durations)
        if (hourly != null) durations = durations.filterNot { it.overlaps(hourly) }
        val mins = durations.map { it.mins }
        val daily = DAILY.containsMatchIn(text)
        val weakCycle = WEAK_CYCLE.containsMatchIn(text) && mins.size >= 2 && hourly == null && !daily
        val cycle = strongCycle || weakCycle
        val range = if (cycle) null else ParserTimes.range(text)
        return when {
            cycle -> cycleResult(text, apps, days, durations)
            range != null -> AiBlockResult(type = "schedule", apps = apps, range = range, days = days)
            else -> nowOrLimit(text, apps, days, mins, hourly != null, daily)
        }
    }

    private fun cycleResult(
        text: String,
        apps: List<String>,
        days: List<String>?,
        durations: List<ParsedDuration>,
    ): AiBlockResult {
        val (use, rest) = cycleRoles(text, durations)
        return AiBlockResult(type = "cycle", apps = apps, use = use, rest = rest, days = days)
    }

    /**
     * (use, rest) in the order spoken, except "lock X for 1 hour after 10 minutes", where the lock comes first
     * but the allowance is the second amount.
     */
    private fun cycleRoles(
        text: String,
        durations: List<ParsedDuration>,
    ): Pair<Double, Double> {
        val first = durations[0]
        val second = durations[1]
        val between = text.substring(first.end, second.start)
        val lockBefore = LOCK_WORD.containsMatchIn(text.substring(0, first.start))
        val swap = lockBefore && ALLOWANCE_WORD.containsMatchIn(between) && !LOCK_WORD.containsMatchIn(between)
        return if (swap) second.mins to first.mins else first.mins to second.mins
    }

    private fun nowOrLimit(
        text: String,
        apps: List<String>,
        days: List<String>?,
        mins: List<Double>,
        hourly: Boolean,
        daily: Boolean,
    ): AiBlockResult =
        if (isNow(text, mins, hourly, daily)) {
            AiBlockResult(type = "now", apps = apps, now = mins.firstOrNull() ?: DEFAULT_NOW, days = days)
        } else {
            AiBlockResult(
                type = "limit",
                apps = apps,
                mins = mins.firstOrNull() ?: DEFAULT_MINS,
                period = if (hourly) "hourly" else "daily",
                days = days,
            )
        }

    /** [parse] followed by [AiNormalizer.normalize]: the draft the wizard opens at step 3. */
    fun toDraft(
        sentence: String,
        installed: List<AppInfo>,
        target: BlockTarget = BlockTarget.ThisPhone,
    ): BlockDraft = AiNormalizer.normalize(parse(sentence, installed), sentence, installed, target)

    private fun isNow(
        text: String,
        mins: List<Double>,
        hourly: Boolean,
        daily: Boolean,
    ): Boolean =
        (NOW_WORDS.containsMatchIn(text) && !hourly && !daily) ||
            (
                mins.size == 1 && !hourly && !daily &&
                    LOCK_FOR.containsMatchIn(
                        text,
                    ) && !LIMIT_WORDS.containsMatchIn(text)
            )
}

/** A span of the sentence holding a duration. */
internal data class ParsedDuration(
    val start: Int,
    val end: Int,
    val mins: Double,
) {
    fun overlaps(other: ParsedDuration) = start < other.end && other.start < end
}

internal object ParserApps {
    private val ALIASES: Map<String, List<String>> =
        mapOf(
            "instagram" to listOf("instagram", "insta", "ig"),
            "youtube" to listOf("youtube", "you tube", "yt"),
            "reddit" to listOf("reddit"),
            "whatsapp" to listOf("whatsapp", "whats app"),
            "chrome" to listOf("chrome"),
            "x" to listOf("x", "twitter"),
            "spotify" to listOf("spotify"),
            "maps" to listOf("google maps", "maps"),
        )
    private const val BLOCK_VERB = "\\b(?:lock|block|stop|limit|ban|disable)\\s+(?:the\\s+)?"
    private const val APP_LIST_ITEM = "(?:[a-z0-9 ]{1,60}(?:,|\\band\\b)\\s*)*"
    private val TARGET_VERB = Regex("$BLOCK_VERB$APP_LIST_ITEM(?:the\\s+)?$")
    private val ALIAS_NEGATION = Regex("(?:n't|\\bnot|\\bnever|\\bexcept|\\bwithout)\\b[^,;.]*$")
    private val NEGATION = Regex("(?:n't|\\bnot|\\bnever|\\bexcept|\\bwithout)\\s*(?:\\w+\\s+)?$")
    private val SOCIAL = Regex("\\bsocials?(?:\\s+media)?\\b")

    /** Everyday words that are also labels of system apps; "on my phone" must not block the dialer. */
    private val GENERIC_LABELS =
        setOf(
            "phone",
            "messages",
            "message",
            "clock",
            "camera",
            "settings",
            "calendar",
            "files",
            "news",
            "contacts",
            "photos",
            "gallery",
            "music",
            "video",
            "email",
            "mail",
            "browser",
            "calculator",
            "notes",
            "weather",
            "search",
            "store",
            "play",
        )

    /**
     * A generic word counts as an app only when it is the object of a block verb, alone or in a list
     * ("block camera", "block instagram and camera"), and the verb is not negated ("don't block camera").
     * "on my phone" never counts.
     */
    private fun isTarget(
        text: String,
        range: IntRange,
    ): Boolean {
        val verb = TARGET_VERB.find(text.substring(0, range.first)) ?: return false
        return !NEGATION.containsMatchIn(text.substring(0, verb.range.first))
    }

    private data class Hit(
        val range: IntRange,
        val name: String,
    )

    /** Known ids and installed labels found in [text], in order of first mention. */
    fun find(
        text: String,
        installed: List<AppInfo>,
    ): List<String> {
        val aliasHits = mutableListOf<Hit>()
        ALIASES.forEach { (id, names) ->
            names.forEach { name -> wordRanges(text, name).forEach { aliasHits += Hit(it, id) } }
        }
        // A group phrase beats an installed app that happens to share its words ("Social Media").
        val groupRanges = SOCIAL.findAll(text).map { it.range }.toList()
        groupRanges.forEach { r -> KnownApps.socialMediaIds.forEach { aliasHits += Hit(r, it) } }
        val idByPkg = KnownApps.packages.entries.associate { it.value to it.key }
        val labelHits = mutableListOf<Hit>()
        installed.forEach { app ->
            val label = app.label.lowercase(Locale.ROOT).trim()
            if (label.length >= 2) {
                wordRanges(text, label)
                    .filter { it !in groupRanges && (label !in GENERIC_LABELS || isTarget(text, it)) }
                    .forEach { labelHits += Hit(it, idByPkg[app.pkg] ?: label) }
            }
        }
        // "YouTube Music" installed: the word "youtube" inside it must not also select YouTube.
        val all =
            (aliasHits + labelHits).filterNot {
                ALIAS_NEGATION.containsMatchIn(
                    text.substring(0, it.range.first),
                )
            }
        val kept = all.filterNot { hit -> all.any { it.contains(hit) } }
        return kept.sortedBy { it.range.first }.map { it.name }.distinct()
    }

    private fun Hit.contains(other: Hit) =
        range.first <= other.range.first && range.last >= other.range.last && range != other.range

    private fun wordRanges(
        text: String,
        word: String,
    ): List<IntRange> = Regex("(?<![a-z0-9])${Regex.escape(word)}(?![a-z0-9])").findAll(text).map { it.range }.toList()
}

internal object ParserDurations {
    private const val MINS_PER_HOUR = 60.0
    private const val HALF = 0.5
    private const val QUARTER = 0.25
    private const val WORDS = "one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|forty|fifty"
    private val WORD_VALUES =
        WORDS.split('|').zip(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 30, 40, 50)).toMap()
    private const val NUM = "\\d+(?:\\.\\d+)?|$WORDS"
    private const val HOUR = "(?:hours?|hrs?|h)"
    private const val MIN = "(?:minutes?|mins?|m)"

    private val HOUR_AND_HALF = Regex("\\b($NUM|an?)\\s*$HOUR\\s+and\\s+a\\s+half\\b")
    private val HALF_HOUR = Regex("\\bhalf[ -](?:an?[ -])?hour\\b")
    private val QUARTER_HOUR = Regex("\\bquarter(?: of)?(?: an?)? hour\\b")
    private val HOURS_MINS = Regex("\\b($NUM)\\s*$HOUR\\b\\s*(?:and\\s+)?($NUM)\\s*$MIN\\b")
    private val HALVES = Regex("\\b($NUM|an?)\\s+and\\s+a\\s+half\\s+hours?\\b")
    private val COMPACT = Regex("\\b(\\d+)h(\\d{1,2})\\b")
    private val HOURS = Regex("\\b($NUM)\\s*$HOUR\\b")
    private val AN_HOUR = Regex("\\ban?\\s+hour\\b")
    private val MINS = Regex("\\b($NUM)\\s*$MIN\\b")
    private val HOURLY = Regex("\\bhourly\\b|\\b(?:an?|per|each|every)\\s+hour\\b")

    fun find(text: String): List<ParsedDuration> {
        val all = mutableListOf<ParsedDuration>()

        fun add(
            regex: Regex,
            value: (MatchResult) -> Double,
        ) = regex.findAll(text).forEach { all += ParsedDuration(it.range.first, it.range.last + 1, value(it)) }
        add(HOUR_AND_HALF) { (number(it.groupValues[1]) + HALF) * MINS_PER_HOUR }
        add(HALF_HOUR) { MINS_PER_HOUR * HALF }
        add(QUARTER_HOUR) { MINS_PER_HOUR * QUARTER }
        add(HOURS_MINS) { number(it.groupValues[1]) * MINS_PER_HOUR + number(it.groupValues[2]) }
        add(HALVES) { (number(it.groupValues[1]) + HALF) * MINS_PER_HOUR }
        add(COMPACT) { it.groupValues[1].toDouble() * MINS_PER_HOUR + it.groupValues[2].toDouble() }
        add(HOURS) { number(it.groupValues[1]) * MINS_PER_HOUR }
        add(AN_HOUR) { MINS_PER_HOUR }
        add(MINS) { number(it.groupValues[1]) }
        // Longest span first at each position so "1 hour 30 min" beats "1 hour".
        val kept = mutableListOf<ParsedDuration>()
        all.sortedWith(compareBy({ it.start }, { -(it.end - it.start) })).forEach { d ->
            if (kept.none { it.overlaps(d) }) kept += d
        }
        return kept
    }

    /**
     * The "an hour" / "per hour" / "hourly" span that marks an hourly limit, or null. "an hour" only counts
     * when an earlier duration exists ("30 min an hour"); on its own it is the amount ("lock for an hour").
     */
    fun hourly(
        text: String,
        durations: List<ParsedDuration>,
    ): ParsedDuration? {
        val m =
            HOURLY.findAll(text).firstOrNull { match ->
                val bare = match.value.startsWith("a ") || match.value.startsWith("an ")
                !bare || durations.any { it.end <= match.range.first }
            } ?: return null
        return ParsedDuration(m.range.first, m.range.last + 1, MINS_PER_HOUR)
    }

    private fun number(raw: String): Double =
        when {
            raw == "a" || raw == "an" -> 1.0
            raw in WORD_VALUES -> WORD_VALUES.getValue(raw).toDouble()
            else -> raw.toDoubleOrNull() ?: 0.0
        }
}

internal object ParserTimes {
    private const val NOON = 12
    private const val HOURS_IN_DAY = 24
    private const val MAX_MINUTE = 59
    private const val WORD_GROUP = 4
    private const val TOKEN = "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?"
    private val CLOCK = Regex("\\b$TOKEN(?![\\d:])|\\b(noon|midnight)\\b")
    private val SPAN = Regex("\\b$TOKEN\\s*(?:to|-|and|until|till|through)\\s*$TOKEN(?![\\d:])")
    private val FROM_BETWEEN = Regex("\\b(?:from|between)\\s+\\d")
    private val UNTIL = Regex("\\b(?:until|till)\\b")
    private val MARKER = Regex("am|pm|a\\.m\\.|p\\.m\\.|:")

    private data class Clock(
        val hour: Int,
        val minute: Int,
        val meridiem: Char?,
    )

    /** "HH:MM–HH:MM" when the text names two times (or the default range for one); null when it names none. */
    fun range(text: String): String? {
        val pair = (span(text) ?: marked(text))?.takeIf { (a, b) -> a.isReal() && b.isReal() }
        val (start, end) = pair?.let { resolve(it.first, it.second) ?: DEFAULT_MINUTES } ?: return null
        return "%02d:%02d–%02d:%02d".format(Locale.ROOT, start / 60, start % 60, end / 60, end % 60)
    }

    private const val DEFAULT_START = 9 * 60
    private const val DEFAULT_END = 17 * 60
    private val DEFAULT_MINUTES = DEFAULT_START to DEFAULT_END
    private val DEFAULT_CLOCKS = Clock(9, 0, null) to Clock(17, 0, null)

    /** "8am-3pm", "from 9 to 5": bare numbers only count after from/between or with a marker on either end. */
    private fun span(text: String): Pair<Clock, Clock>? {
        val m = SPAN.find(text) ?: return null
        val a = m.toClock(1)
        val b = m.toClock(4)
        val marked = a.meridiem != null || b.meridiem != null || m.value.contains(':')
        val bareOk = FROM_BETWEEN.containsMatchIn(text) || UNTIL.containsMatchIn(m.value)
        return if (marked || bareOk) a to b else null
    }

    /** Two or more times with am/pm, a colon, noon or midnight that are not a span ("from 8am until 3pm"). */
    private fun marked(text: String): Pair<Clock, Clock>? {
        val found =
            CLOCK
                .findAll(text)
                .mapNotNull { m ->
                    m.wordClock()
                        ?: m.takeIf { MARKER.containsMatchIn(it.value) && !text.startsWithWordAfter(it) }?.toClock(1)
                }.toList()
        return when {
            found.size >= 2 -> found[0] to found[1]
            found.size == 1 -> DEFAULT_CLOCKS
            else -> null
        }
    }

    /** 1-12 with am/pm, 0-24 without; minutes 0-59. */
    private fun Clock.isReal(): Boolean =
        minute <= MAX_MINUTE &&
            if (meridiem != null) hour in 1..NOON else (hour < HOURS_IN_DAY || (hour == HOURS_IN_DAY && minute == 0))

    private fun String.startsWithWordAfter(m: MatchResult): Boolean =
        substring(m.range.last + 1).trimStart().let { it.startsWith("noon") || it.startsWith("midnight") }

    private fun MatchResult.wordClock(): Clock? =
        when (groupValues.getOrNull(WORD_GROUP)) {
            "noon" -> Clock(NOON, 0, 'p')
            "midnight" -> Clock(0, 0, null)
            else -> null
        }

    private fun MatchResult.toClock(first: Int): Clock =
        Clock(
            hour = groupValues[first].toInt(),
            minute = groupValues[first + 1].ifEmpty { "0" }.toInt(),
            meridiem = groupValues[first + 2].firstOrNull(),
        )

    /** Minutes since midnight for both ends, or null if a time is not a real one or the window is empty. */
    private fun resolve(
        a: Clock,
        b: Clock,
    ): Pair<Int, Int>? {
        val ends = candidates(b, null)
        val starts = candidates(a, b.meridiem)
        // Prefer the reading where the window stays inside one day ("9 to 5" -> 09:00-17:00).
        val pair =
            starts.firstNotNullOfOrNull { s -> ends.firstOrNull { e -> e > s }?.let { s to it } }
                ?: (starts.first() to ends.first())
        return pair.takeIf { it.first != it.second }
    }

    /** Possible minutes-of-day for [c], most likely first. A bare hour 1-11 is tried as am then pm. */
    private fun candidates(
        c: Clock,
        inherit: Char?,
    ): List<Int> {
        fun at(h: Int) = (h % HOURS_IN_DAY) * 60 + c.minute
        val hint = c.meridiem ?: inherit
        val base =
            when {
                hint == 'a' -> listOf(at(c.hour % NOON))
                hint == 'p' -> listOf(at(c.hour % NOON + NOON))
                c.hour == 0 || c.hour > NOON -> listOf(at(c.hour))
                else -> listOf(at(c.hour % NOON), at(c.hour % NOON + NOON))
            }
        // A meridiem inherited from the other end is only a hint; keep the other readings as a fallback.
        return if (c.meridiem == null && inherit != null) (base + candidates(c, null)).distinct() else base
    }
}

internal object ParserDays {
    private val NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private const val WEEKDAY_COUNT = 5
    private const val DAY_KEY_LENGTH = 3
    private const val DAY =
        "(mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)"
    private val DAY_RANGE = Regex("\\b$DAY\\s*(?:to|-|through|thru|until|till)\\s*$DAY\\b")
    private val DAY_WORD = Regex("\\b$DAY\\b")
    private val WEEKDAYS = Regex("weekday|school day|work day|workday")
    private val WEEKENDS = Regex("weekend")

    /** Day keys for the days named in [text]; null when none are (the normaliser then picks every day). */
    fun find(text: String): List<String>? {
        val group =
            when {
                WEEKDAYS.containsMatchIn(text) -> NAMES.subList(0, WEEKDAY_COUNT)
                WEEKENDS.containsMatchIn(text) -> NAMES.subList(WEEKDAY_COUNT, NAMES.size)
                else -> emptyList()
            }
        // "weekends and Monday": the group plus the days named on top of it.
        val days = (group + named(text)).distinct().sortedBy { NAMES.indexOf(it) }
        return days.takeIf { it.isNotEmpty() }
    }

    private fun named(text: String): List<String> {
        val ranges = DAY_RANGE.findAll(text).toList()
        val fromRanges =
            ranges.flatMap { range ->
                val from = NAMES.indexOf(range.groupValues[1].take(DAY_KEY_LENGTH))
                val to = NAMES.indexOf(range.groupValues[2].take(DAY_KEY_LENGTH))
                val length = (to - from + NAMES.size) % NAMES.size
                (0..length).map { NAMES[(from + it) % NAMES.size] }
            }
        val single =
            DAY_WORD
                .findAll(text)
                .filter { word -> ranges.none { word.range.first in it.range } }
                .map { it.groupValues[1].take(DAY_KEY_LENGTH) }
        return (fromRanges + single).distinct().sortedBy { NAMES.indexOf(it) }
    }
}
