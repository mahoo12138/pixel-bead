package com.pixelbead.client

import com.pixelbead.beadmode.ToolMode

/**
 * Client-side mirror of the fast-mode selection, so the HUD can render it without asking the
 * server. The server keeps its own copy (set through [com.pixelbead.network.SetToolMode]) and is
 * still the authority on what a click does.
 */
object ToolClientState {
    var mode: ToolMode = ToolMode.LINE_PLACE
        private set

    /** Cycle and report the new mode; called from the arrow keys while the tool key is held. */
    fun shift(steps: Int): ToolMode {
        mode = mode.shift(steps)
        return mode
    }
}
