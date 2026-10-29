package com.dculus.stayfocused.core.blocking.screen

import com.dculus.stayfocused.core.blocking.evaluator.Decision
import com.dculus.stayfocused.core.blocking.evaluator.WarningKind

/** What the engine needs from the block screen; [BlockScreenLauncher] is the real implementation. */
interface BlockPresenter {
    fun show(
        pkg: String,
        decision: Decision.Block,
    )

    /** Whether a block screen is on screen or on its way (activity launching, overlay up). */
    fun isShowing(): Boolean

    /** A brief, untouchable heads-up over [pkg] that its budget is running out. Does nothing by default. */
    fun showWarning(
        pkg: String,
        kind: WarningKind,
    ) = Unit

    /** Removes the fallback overlay, if showing. */
    fun dismissOverlay()
}
