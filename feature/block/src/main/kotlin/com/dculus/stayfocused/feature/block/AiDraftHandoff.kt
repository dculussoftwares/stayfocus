package com.dculus.stayfocused.feature.block

import com.dculus.stayfocused.core.model.BlockDraft
import javax.inject.Inject
import javax.inject.Singleton

/** Route prefill value that opens the wizard on step 3 with the pending AI draft. */
const val AI_PREFILL = "ai"

/** The AI result and the sentence it came from. */
data class AiDraftRequest(
    val sentence: String,
    val draft: BlockDraft,
)

/**
 * Carries the AI draft from the Block tab to the wizard (a draft is too large for a route argument). The wizard takes
 * it once; if the process died in between there is nothing to take and the wizard opens as a normal one.
 */
@Singleton
class AiDraftHandoff
    @Inject
    constructor() {
        @Volatile
        private var pending: AiDraftRequest? = null

        fun put(request: AiDraftRequest) {
            pending = request
        }

        /** Returns the pending request and clears it. */
        fun take(): AiDraftRequest? = pending.also { pending = null }
    }
