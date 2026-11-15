package com.pixelbead.beadmode

/**
 * The four behaviours of the fast (tool key) mode. The player cycles them with the arrow keys
 * while the modifier is held; the server keeps the choice per session so the right-click handler
 * knows what the second point should do.
 *
 * Order matches the order shown in the HUD: line place -> line erase -> plane place -> plane erase.
 */
enum class ToolMode(val translationKey: String, val erase: Boolean, val plane: Boolean) {
    LINE_PLACE("gui.pixelbead.mode.line_place", erase = false, plane = false),
    LINE_ERASE("gui.pixelbead.mode.line_erase", erase = true, plane = false),
    PLANE_PLACE("gui.pixelbead.mode.plane_place", erase = false, plane = true),
    PLANE_ERASE("gui.pixelbead.mode.plane_erase", erase = true, plane = true),
    ;

    /** Cycle with wrap-around; [steps] is +1 for Down, -1 for Up. */
    fun shift(steps: Int): ToolMode {
        val all = entries
        val index = ((ordinal + steps) % all.size + all.size) % all.size
        return all[index]
    }

    companion object {
        /** Network decode: unknown ids fall back to the default instead of throwing. */
        fun byId(id: Int): ToolMode = entries.getOrElse(id) { LINE_PLACE }
    }
}
