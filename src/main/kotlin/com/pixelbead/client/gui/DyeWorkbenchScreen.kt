package com.pixelbead.client.gui

import com.pixelbead.palette.Palette
import com.pixelbead.workbench.DyeWorkbenchMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * Workbench screen: input/dye/result slots (rendered by the menu) plus a color grid showing
 * only the current dye's mapped series, and a series hint. Grid clicks select via clickMenuButton.
 *
 * 26.3 RenderPearl (verified): extractRenderState(GuiGraphicsExtractor, int, int, float),
 * mouseClicked(MouseButtonEvent, boolean), text drawing via GuiGraphicsExtractor.text.
 */
class DyeWorkbenchScreen(menu: DyeWorkbenchMenu, inv: Inventory, title: Component) :
    AbstractContainerScreen<DyeWorkbenchMenu>(menu, inv, title) {

    private fun gridColors() = menu.currentSeries()?.let { Palette.series(it) } ?: emptyList()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val colors = gridColors()
        val gx = leftPos + 80
        val gy = topPos + 16
        val cell = 12
        val cols = 6
        val selected = menu.selectedColorId()
        colors.forEachIndexed { i, c ->
            val x = gx + (i % cols) * cell
            val y = gy + (i / cols) * cell
            graphics.fill(x, y, x + cell - 1, y + cell - 1, c.argb)
            if (c.id == selected) graphics.fill(x - 1, y - 1, x + cell, y + cell, 0xFF000000.toInt())
        }

        val series = menu.currentSeries()
        val hint = if (series == null) Component.translatable("gui.pixelbead.workbench.no_dye")
        else Component.translatable("gui.pixelbead.workbench.series", series)
        graphics.text(font, hint, leftPos + 8, topPos + 6, 0xFF404040.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()
        val colors = gridColors()
        val gx = leftPos + 80
        val gy = topPos + 16
        val cell = 12
        val cols = 6
        colors.forEachIndexed { i, c ->
            val x = gx + (i % cols) * cell
            val y = gy + (i / cols) * cell
            if (mx >= x && mx < x + cell && my >= y && my < y + cell) {
                val index = Palette.ordered.indexOfFirst { it.id == c.id }
                val player = minecraft.player ?: return@forEachIndexed
                if (index >= 0) menu.clickMenuButton(player, index)
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }
}