package com.pixelbead.client.hud

import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.client.ClientPrefs
import com.pixelbead.client.gui.ColorPickerScreen
import com.pixelbead.client.gui.PixelBeadOptionsScreen
import com.pixelbead.config.ClientConfig
import com.pixelbead.config.HudPosition
import com.pixelbead.inventory.BeadInventory
import com.pixelbead.palette.Palette
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.gui.GuiLayer

/**
 * Compact bead-mode HUD: the 18 virtual color slots, current code/count and transient toasts.
 * Hidden while the color picker is open — the picker draws its own mirrored hotbar and the two
 * surfaces are intentionally independent (no shared preview state).
 *
 * The bar can be docked at the bottom (9 x 2) or on either side (one column of 18); the choice
 * lives in `ClientConfig.hudPosition` and is edited on the vanilla-styled options screen
 * (picker gear button, or Mods -> Pixel Bead -> Config).
 *
 * Verified against local decompiled 26.3 source: GuiLayer.render(GuiGraphicsExtractor,
 * DeltaTracker); text is GuiGraphicsExtractor.text(...), rects are fill(...).
 */
object BeadHudLayer : GuiLayer {
    private var toast: String? = null
    private var toastUntil: Long = 0

    fun showToast(key: String) {
        toast = key
        toastUntil = System.currentTimeMillis() + 2000L
    }

    override fun render(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!ClientConfig.hudEnabled.get()) return
        val state = BeadModeManager.state(player)
        if (!state.active) return
        // Both mod-owned screens draw their own hotbar (or cover the game): keep the HUD out.
        val screen = mc.gui.screen()
        if (screen is ColorPickerScreen || screen is PixelBeadOptionsScreen) return

        val position = ClientPrefs.hudPosition()
        val size = ClientPrefs.swatchSize()
        val guiWidth = mc.window.guiScaledWidth
        val guiHeight = mc.window.guiScaledHeight
        val cols = BeadHotbarView.columns(position, guiHeight, size)
        val x0 = BeadHotbarView.originX(position, guiWidth, cols, size)
        val y0 = BeadHotbarView.originY(position, guiHeight, cols, size)

        BeadHotbarView.draw(graphics, x0, y0, cols, size, state)

        // Bottom dock: text stacks above the bar. Side docks: text runs beside the column.
        val textX = when (position) {
            HudPosition.BOTTOM -> x0
            HudPosition.LEFT -> x0 + BeadHotbarView.widthFor(cols, size) + 6
            HudPosition.RIGHT -> 0
        }
        val textY = if (position == HudPosition.BOTTOM) y0 - 12 else y0

        val current = state.selectedColorId()
        if (current != null) {
            val color = Palette.byId(current)
            val countText = if (player.isCreative) "∞" else BeadInventory.countBead(player, current).toString()
            val label = Component.literal("${color?.code ?: current}  ×$countText")
            val x = if (position == HudPosition.RIGHT) x0 - 6 - mc.font.width(label) else textX
            graphics.text(mc.font, label, x, textY, 0xFFFFFFFF.toInt())
        }

        val t = toast
        if (t != null && System.currentTimeMillis() < toastUntil) {
            val message = Component.translatable("message.pixelbead.$t")
            val x = if (position == HudPosition.RIGHT) x0 - 6 - mc.font.width(message) else textX
            val y = if (position == HudPosition.BOTTOM) textY - 12 else textY + 12
            graphics.text(mc.font, message, x, y, 0xFFFF5555.toInt())
        } else if (t != null) {
            toast = null
        }
    }
}
