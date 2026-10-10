package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.KnownApps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AiBlockBuilderTest {
    private val ig = KnownApps.packages.getValue("instagram")
    private val installed = listOf(AppInfo(ig, "Instagram"))
    private val sentence = "Instagram 30 min a day"

    private class FakeModel(
        private val block: suspend (String) -> String,
    ) : AiModel {
        val sentences = mutableListOf<String>()

        override suspend fun generate(sentence: String): String {
            sentences += sentence
            return block(sentence)
        }
    }

    private fun builder(
        model: AiModel,
        configured: Boolean = true,
    ) = AiBlockBuilder(model, { configured }, AiPrompt.TIMEOUT_MS)

    @Test
    fun `uses the model result and sends only the trimmed sentence`() =
        runTest {
            val model =
                FakeModel { """{"type":"now","apps":["instagram"],"now":45,"note":"Blocked for 45 minutes."}""" }
            val draft = builder(model).build("  $sentence  ", installed)
            assertEquals(BlockType.NOW, draft.type)
            assertEquals(45, draft.now)
            assertEquals("Blocked for 45 minutes.", draft.aiNote)
            assertTrue(draft.fromAi)
            assertEquals(listOf(sentence), model.sentences)
        }

    @Test
    fun `falls back to the on-device parser when Firebase is not configured`() =
        runTest {
            val model = FakeModel { error("must not be called") }
            val draft = builder(model, configured = false).build(sentence, installed)
            assertEquals(BlockType.LIMIT, draft.type)
            assertEquals(30, draft.mins)
            assertEquals(setOf(ig), draft.apps)
            assertTrue(model.sentences.isEmpty())
        }

    @Test
    fun `falls back on malformed JSON`() =
        runTest {
            val draft = builder(FakeModel { "Sorry, I cannot do that" }).build(sentence, installed)
            assertEquals(BlockType.LIMIT, draft.type)
            assertEquals(30, draft.mins)
        }

    @Test
    fun `falls back when the JSON has no valid type`() =
        runTest {
            val draft = builder(FakeModel { "{}" }).build("Block Instagram for 10 minutes", installed)
            assertEquals(BlockType.NOW, draft.type)
            assertEquals(10, draft.now)
        }

    @Test
    fun `falls back when the model throws`() =
        runTest {
            val draft = builder(FakeModel { throw IOException("offline") }).build(sentence, installed)
            assertEquals(30, draft.mins)
            assertEquals(setOf(ig), draft.apps)
        }

    @Test
    fun `falls back after the 8 second timeout`() =
        runTest {
            val draft = CompletableDeferred<com.dculus.stayfocused.core.model.BlockDraft>()
            backgroundScope.launch {
                draft.complete(
                    builder(FakeModel { awaitCancellation() }).build(sentence, installed),
                )
            }
            advanceTimeBy(AiPrompt.TIMEOUT_MS - 1)
            runCurrent()
            assertTrue(!draft.isCompleted)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(30, draft.await().mins)
        }

    @Test(expected = CancellationException::class)
    fun `cancellation is not swallowed`() =
        runTest {
            builder(FakeModel { throw CancellationException("cancelled") }).build(sentence, installed)
        }
}
