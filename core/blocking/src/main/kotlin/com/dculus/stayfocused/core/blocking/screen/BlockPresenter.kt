package com.dculus.stayfocused.core.blocking.screen

import com.dculus.stayfocused.core.blocking.evaluator.Decision

/** What the engine needs from the block screen; [BlockScreenLauncher] is the real implementation. */
interface BlockPresenter {
    fun show(
        pkg: String,
        decision: Decision.Block,
    )

    /** Whether a block screen is on screen or on its way (activity launching, overlay up). */
    fun isShowing(): Boolean

    /** Removes the fallback overlay, if showing. */
    fun dismissOverlay()
}
