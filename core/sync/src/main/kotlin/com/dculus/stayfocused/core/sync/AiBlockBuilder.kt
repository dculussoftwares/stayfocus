package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.AiBlockResult
import com.dculus.stayfocused.core.model.AiNormalizer
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.BlockDraft
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LocalBlockParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Describe it" block builder: Gemini first, then the on-device [LocalBlockParser] when the cloud model is not
 * available (Firebase not configured, offline, timeout, malformed output, any error). No account is required.
 */
@Singleton
class AiBlockBuilder
    internal constructor(
        private val model: AiModel,
        private val isConfigured: () -> Boolean,
        private val timeoutMs: Long,
    ) {
        @Inject
        internal constructor(model: AiModel, availability: FirebaseAvailability) :
            this(model, { availability.isConfigured }, AiPrompt.TIMEOUT_MS)

        /** Never throws (other than cancellation). The draft has `fromAi = true`. */
        suspend fun build(
            sentence: String,
            installed: List<AppInfo>,
            target: BlockTarget = BlockTarget.ThisPhone,
        ): BlockDraft {
            val result = if (isConfigured()) fromModel(sentence) else null
            return if (result != null) {
                AiNormalizer.normalize(result, sentence, installed, target)
            } else {
                LocalBlockParser.toDraft(sentence, installed, target)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun fromModel(sentence: String): AiBlockResult? =
            try {
                val text =
                    withTimeoutOrNull(timeoutMs) { model.generate(sentence.trim().take(AiPrompt.MAX_SENTENCE_LENGTH)) }
                val parsed = text?.let(AiBlockResult::parse)
                if (parsed ==
                    null
                ) {
                    Timber.w("Gemini gave no usable result (timeout or malformed); using on-device parser")
                }
                parsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Gemini failed; using on-device parser")
                null
            }
    }
