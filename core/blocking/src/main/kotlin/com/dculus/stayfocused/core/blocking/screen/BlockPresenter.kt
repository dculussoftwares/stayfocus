package com.dculus.stayfocused.core.blocking.screen

import com.dculus.stayfocused.core.blocking.evaluator.Decision

/** What the engine needs from the block screen; [BlockScreenLauncher] is the real implementation. */
interface BlockPresenter {
    fun show(
        pkg: String,
        decision: Decision.Block,
    )

    /** Removes the fallback overlay, if showing. */
    fun dismissOverlay()
}
