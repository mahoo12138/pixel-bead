package com.pixelbead.config

/**
 * Swatch edge length of the virtual hotbar, in GUI pixels. Persisted by
 * [ClientConfig.hudSwatchSize] and edited on the options screen.
 *
 * Three discrete steps rather than a slider: the bar has to stay pixel-crisp, and the picker's
 * mirrored copy reuses the same geometry, so whole-pixel steps keep both surfaces aligned.
 * [GAP][com.pixelbead.client.hud.BeadHotbarView.GAP] stays 1px, so every step keeps the bar
 * reading as one tight color strip.
 */
enum class HudSwatchSize(val translationKey: String, val swatch: Int) {
    SMALL("gui.pixelbead.size.small", 12),
    MEDIUM("gui.pixelbead.size.medium", 16),
    LARGE("gui.pixelbead.size.large", 20),
}
