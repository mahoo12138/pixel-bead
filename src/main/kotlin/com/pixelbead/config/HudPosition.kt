package com.pixelbead.config

/**
 * Where the bead hotbar is anchored on screen. Persisted by [ClientConfig.hudPosition] and
 * edited from the options screen (also reachable from the colour picker's gear button).
 *
 * BOTTOM keeps the 9 x 2 grid flush with the window bottom; LEFT/RIGHT switch to a single
 * column of 18 flush with the side edge.
 *
 * [translationKey] lives here (a plain string, no Minecraft types) so both the options screen
 * and the picker label the enum the same way from one source.
 */
enum class HudPosition(val translationKey: String) {
    BOTTOM("gui.pixelbead.hud.bottom"),
    LEFT("gui.pixelbead.hud.left"),
    RIGHT("gui.pixelbead.hud.right"),
}
