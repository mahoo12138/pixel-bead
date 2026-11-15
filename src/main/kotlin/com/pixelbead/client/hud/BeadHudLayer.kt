package com.pixelbead.client.hud

import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.client.ClientPrefs
import com.pixelbead.client.gui.ColorPickerScreen
import com.pixelbead.client.gui.PixelBeadOptionsScreen
import com.pixelbead.client.key.BeadKeyMappings
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
 * Compact bead-mode HUD: the 18 virtual color slots, current code/count, transient toasts and
 * the tool-key hint.
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
    private var toast: Component? = null
    private var toastOk = false
    private var toastUntil: Long = 0

    /** Tool results that are good news; everything else is reported in warning red. */
    private val successKeys = setOf("filled", "broken", "undo_done", "anchor")

    fun showToast(key: String, arg: String = "", arg2: String = "") {
        val path = "message.pixelbead.$key"
        toast = when {
            arg2.isNotEmpty() -> Component.translatable(path, arg, arg2)
            arg.isNotEmpty() -> Component.translatable(path, arg)
            else -> Component.translatable(path)
        }
        toastOk = key in successKeys
        toastUntil = System.currentTimeMillis() + if (toastOk) 1200L else 2000L
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
        val columnRight = position == HudPosition.RIGHT
        val textX = when (position) {
            HudPosition.BOTTOM -> x0
            HudPosition.LEFT -> x0 + BeadHotbarView.widthFor(cols, size) + 6
            HudPosition.RIGHT -> 0
        }
        fun line(component: Component, row: Int, color: Int) {
            val y = if (position == HudPosition.BOTTOM) y0 - 12 - 12 * row else y0 + 12 * row
            val x = if (columnRight) x0 - 6 - mc.font.width(component) else textX
            graphics.text(mc.font, component, x, y, color)
        }

        val current = state.selectedColorId()
        if (current != null) {
            val color = Palette.byId(current)
            val countText = if (player.isCreative) "∞" else BeadInventory.countBead(player, current).toString()
            line(Component.literal("${color?.code ?: current}  ×$countText"), 0, 0xFFFFFFFF.toInt())
        }

        // Tool hint only while the modifier is actually held, so the HUD stays quiet otherwise.
        if (screen == null && BeadKeyMappings.TOOL.isDown) {
            line(Component.translatable("gui.pixelbead.tool_hint"), 2, 0xFF8ED0F0.toInt())
        }

        val t = toast
        if (t != null && System.currentTimeMillis() < toastUntil) {
            line(t, 1, if (toastOk) 0xFF7CE07C.toInt() else 0xFFFF5555.toInt())
        } else if (t != null) {
            toast = null
        }
    }
}
