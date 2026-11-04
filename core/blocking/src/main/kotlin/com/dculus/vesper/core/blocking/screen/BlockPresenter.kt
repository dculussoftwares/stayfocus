package com.dculus.vesper.core.blocking.screen

import com.dculus.vesper.core.blocking.evaluator.Decision
import com.dculus.vesper.core.blocking.evaluator.WarningKind

/** What the engine needs from the block screen; [BlockScreenLauncher] is the real implementation. */
interface BlockPresenter {
    fun show(
        pkg: String,
        decision: Decision.Block,
    )

    /** Whether a block screen is on screen or on its way (activity launching, overlay up). */
    fun isShowing(): Boolean

    /**
     * A brief, untouchable heads-up over [pkg] that its budget is running out; [glow] adds the edge glow.
     * Does nothing by default.
     */
    fun showWarning(
        pkg: String,
        kind: WarningKind,
        glow: Boolean = true,
    ) = Unit

    /**
     * A small timer tab on the left edge over [pkg] while it is in front: [remainingMs] of its [totalMs] budget are
     * left. [expanded] opens it with the time showing before it tucks itself in; otherwise it starts tucked in.
     * Does nothing by default.
     */
    fun showAppTimer(
        pkg: String,
        remainingMs: Long,
        totalMs: Long,
        expanded: Boolean = true,
    ) = Unit

    /** Removes the timer tab, if showing (the app left the front). Does nothing by default. */
    fun hideAppTimer() = Unit

    /** Removes the fallback overlay, if showing. */
    fun dismissOverlay()
}
