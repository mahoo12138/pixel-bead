package com.pixelbead.client.gui

import com.pixelbead.client.ClientPrefs
import com.pixelbead.config.HudPosition
import com.pixelbead.config.HudSwatchSize
import net.minecraft.client.Options
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsSubScreen
import net.minecraft.network.chat.Component

/**
 * Pixel Bead settings, drawn with vanilla's own options scaffold (`OptionsSubScreen` +
 * `OptionsList` + `CycleButton`) so it looks and behaves exactly like "Video Settings":
 * title header, scrolling widget list, "Done" footer, keyboard narration included.
 *
 * Reached from two places, both landing on this one screen:
 *  - the gear button in the colour picker ([ColorPickerScreen]);
 *  - Mods list -> Pixel Bead -> Config, via the `IConfigScreenFactory` registered in
 *    [com.pixelbead.client.PixelBeadClient].
 *
 * Every control writes straight to the client config and takes effect on the next frame
 * (the HUD re-reads `ClientPrefs` each render), so there is no Apply/OK step to forget.
 */
class PixelBeadOptionsScreen(
    lastScreen: Screen,
    options: Options,
) : OptionsSubScreen(lastScreen, options, Component.translatable("gui.pixelbead.options")) {

    override fun addOptions() {
        val list = this.list ?: return

        list.addHeader(Component.translatable("gui.pixelbead.options.hud"))
        list.addBig(
            CycleButton.builder(
                { position: HudPosition -> Component.translatable(position.translationKey) },
                ClientPrefs.hudPosition(),
            )
                .withValues(HudPosition.entries.toList())
                .create(0, 0, 310, 20, Component.translatable("gui.pixelbead.options.hud_position")) { _, value ->
                    ClientPrefs.setHudPosition(value)
                }
        )
        list.addBig(
            CycleButton.onOffBuilder(ClientPrefs.hudEnabled())
                .create(0, 0, 310, 20, Component.translatable("gui.pixelbead.options.hud_enabled")) { _, value ->
                    ClientPrefs.setHudEnabled(value)
                }
        )
        list.addBig(
            CycleButton.builder(
                { size: HudSwatchSize -> Component.translatable(size.translationKey) },
                ClientPrefs.swatchSize(),
            )
                .withValues(HudSwatchSize.entries.toList())
                .create(0, 0, 310, 20, Component.translatable("gui.pixelbead.options.hud_swatch")) { _, value ->
                    ClientPrefs.setSwatchSize(value)
                }
        )
    }
}
