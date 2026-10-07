package com.dculus.stayfocused.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalBlockParserTest {
    @Serializable
    private data class Case(
        val sentence: String,
        val expected: AiBlockResult,
        val localOnly: Boolean = false,
    )

    @Serializable
    private data class Installed(
        val pkg: String,
        val label: String,
    )

    @Serializable
    private data class Fixture(
        val installedApps: List<Installed>,
        val cases: List<Case>,
    )

    private val fixture: Fixture =
        Json { ignoreUnknownKeys = true }.decodeFromString(
            Fixture.serializer(),
            checkNotNull(javaClass.classLoader.getResourceAsStream("ai/block-sentences.json")) { "fixture missing" }
                .bufferedReader()
                .use { it.readText() },
        )

    private val installed: List<AppInfo> =
        KnownApps.packages.map { (id, pkg) -> AppInfo(pkg, id.replaceFirstChar { it.uppercase() }) } +
            fixture.installedApps.map { AppInfo(it.pkg, it.label) }

    private val ig = KnownApps.packages.getValue("instagram")
    private val yt = KnownApps.packages.getValue("youtube")

    @Test
    fun `fixture has the three prototype examples and at least 25 more sentences`() {
        val sentences = fixture.cases.map { it.sentence }
        listOf(
            "Instagram 10 min, then lock 1 hour",
            "No YouTube on school days 8am–3pm",
            "Social media 45 min a day",
        ).forEach { assertTrue(it in sentences, it) }
        assertTrue(sentences.size >= 28, "only ${sentences.size} sentences")
    }

    @Test
    fun `every fixture sentence parses to the expected rule`() {
        val failures = fixture.cases.mapNotNull { case -> diff(case)?.let { "\"${case.sentence}\": $it" } }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun diff(case: Case): String? {
        val got = LocalBlockParser.parse(case.sentence, installed)
        val want = case.expected
        val diffs =
            buildList {
                if (got.type != want.type) add("type ${got.type} != ${want.type}")
                if (got.apps.orEmpty().toSet() != want.apps.orEmpty().toSet()) add("apps ${got.apps} != ${want.apps}")
                when (want.type) {
                    "limit" -> {
                        if (got.mins != want.mins) add("mins ${got.mins} != ${want.mins}")
                        if (got.period != want.period) add("period ${got.period} != ${want.period}")
                    }

                    "cycle" -> {
                        if (got.use != want.use) add("use ${got.use} != ${want.use}")
                        if (got.rest != want.rest) add("rest ${got.rest} != ${want.rest}")
                    }

                    "schedule" -> {
                        if (got.range != want.range) add("range ${got.range} != ${want.range}")
                    }

                    "now" -> {
                        if (got.now != want.now) add("now ${got.now} != ${want.now}")
                    }
                }
                if (got.days?.toSet() != want.days?.toSet()) add("days ${got.days} != ${want.days}")
            }
        return diffs.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    @Test
    fun `parser output normalises to a usable draft`() {
        val draft = LocalBlockParser.toDraft("No YouTube on school days 8am–3pm", installed)
        assertEquals(BlockType.SCHEDULE, draft.type)
        assertEquals(setOf(yt), draft.apps)
        assertEquals("08:00–15:00", draft.range.format())
        assertEquals(DaysOfWeek.WEEKDAYS, draft.days)
        assertTrue(draft.fromAi)
    }

    @Test
    fun `an installed app label that is not a known app is matched`() {
        val draft = LocalBlockParser.toDraft("Block Clash of Clans for 1 hour 30 minutes", installed)
        assertEquals(setOf("com.supercell.clashofclans"), draft.apps)
        assertEquals(90, draft.now)
    }

    @Test
    fun `empty and nonsense input never throws and gives the prototype defaults`() {
        listOf("", "   ", "???", "asdf qwer", "99:99 to 77:77", "0 minutes", "12 to 12 from", "25pm to 99am").forEach {
            LocalBlockParser.toDraft(it, installed)
        }
        val draft = LocalBlockParser.toDraft("", installed)
        assertEquals(BlockType.LIMIT, draft.type)
        assertEquals(30, draft.mins)
        assertEquals(LimitPeriod.DAILY, draft.period)
        assertEquals(setOf(ig, yt), draft.apps)
    }

    @Test
    fun `short app names only match whole words`() {
        assertEquals(listOf("instagram", "youtube"), LocalBlockParser.parse("fox next maxim", installed).apps)
        assertEquals(listOf("x"), LocalBlockParser.parse("block x for 20 min", installed).apps)
    }
}
