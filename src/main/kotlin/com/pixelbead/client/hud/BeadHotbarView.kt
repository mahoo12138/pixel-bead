package com.pixelbead.client.hud

import com.pixelbead.beadmode.BeadModeState
import com.pixelbead.config.HudPosition
import com.pixelbead.config.HudSwatchSize
import com.pixelbead.palette.Palette
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Single source of truth for how the virtual 18-slot hotbar looks. The in-game HUD and the
 * color picker both call [draw], so the two surfaces can never drift apart visually.
 *
 * Geometry is driven by a column count and a swatch size rather than fixed constants:
 * - `BOTTOM` uses [BOTTOM_COLS] (9) -> 9 x 2, flush with the window bottom.
 * - `LEFT`/`RIGHT` use one column of 18, flush with that side edge, vertically centered.
 * - [HudSwatchSize] picks the swatch edge (12 / 16 / 20 px); the 1px channel stays constant.
 *
 * A selected slot is outlined, never filled: an opaque highlight covers the swatch and makes
 * it look pure white. The dark ring fills the 1px channel while the colored ring is drawn
 * *inside* the swatch, so neighbours are never covered.
 */
object BeadHotbarView {
    /**
     * A single 1px channel between swatches: the bar reads as one tight color strip at every
     * size step. Changing this value would break the "dark ring fills the channel" highlight.
     */
    const val GAP: Int = 1
    const val BOTTOM_COLS: Int = 9
    /** 2px of breathing room: the bar is effectively flush with the window edge. */
    const val BOTTOM_GAP: Int = 2

    // Plain vals: `0xFF....toInt()` is not a compile-time constant in Kotlin.
    private val EMPTY = 0xFF808080.toInt()
    private val OUTLINE_SHADOW = 0xFF101010.toInt()
    private val OUTLINE_SELECTED = 0xFFFFFFFF.toInt()
    /** Gold cursor used by the picker for the slot that will receive the next click. */
    val EDIT_COLOR = 0xFFFFD166.toInt()
    private val OUTLINE_HOVER = 0xFF9A9A9A.toInt()

    fun cellFor(size: HudSwatchSize): Int = size.swatch + GAP

    /**
     * One column of [BeadModeState.SLOT_COUNT] is `18 * cell - GAP` tall; on windows too short
     * for that, fall back to two columns of 9 so no slot ends up clipped off screen.
     */
    fun columns(position: HudPosition, guiHeight: Int, size: HudSwatchSize): Int {
        if (position == HudPosition.BOTTOM) return BOTTOM_COLS
        return if (heightFor(1, size) + BOTTOM_GAP * 2 <= guiHeight) 1 else 2
    }

    fun rowsFor(cols: Int): Int = (BeadModeState.SLOT_COUNT + cols - 1) / cols

    fun widthFor(cols: Int, size: HudSwatchSize): Int = cols * cellFor(size) - GAP

    fun heightFor(cols: Int, size: HudSwatchSize): Int = (rowsFor(cols) - 1) * cellFor(size) + size.swatch

    fun slotX(originX: Int, index: Int, cols: Int, size: HudSwatchSize): Int =
        originX + (index % cols) * cellFor(size)

    fun slotY(originY: Int, index: Int, cols: Int, size: HudSwatchSize): Int =
        originY + (index / cols) * cellFor(size)

    fun originX(position: HudPosition, guiWidth: Int, cols: Int, size: HudSwatchSize): Int = when (position) {
        HudPosition.BOTTOM -> (guiWidth - widthFor(cols, size)) / 2
        HudPosition.LEFT -> BOTTOM_GAP
        HudPosition.RIGHT -> guiWidth - widthFor(cols, size) - BOTTOM_GAP
    }

    fun originY(position: HudPosition, guiHeight: Int, cols: Int, size: HudSwatchSize): Int = when (position) {
        HudPosition.BOTTOM -> guiHeight - heightFor(cols, size) - BOTTOM_GAP
        else -> (guiHeight - heightFor(cols, size)) / 2
    }

    /** Index under the pointer, or null. Used by the picker's mirrored hotbar. */
    fun slotAt(
        mx: Double,
        my: Double,
        originX: Int,
        originY: Int,
        cols: Int,
        size: HudSwatchSize,
    ): Int? {
        if (mx < originX || my < originY) return null
        val cell = cellFor(size)
        val col = ((mx - originX) / cell).toInt()
        val row = ((my - originY) / cell).toInt()
        if (col !in 0 until cols || row < 0) return null
        val index = row * cols + col
        if (index >= BeadModeState.SLOT_COUNT) return null
        val x = slotX(originX, index, cols, size)
        val y = slotY(originY, index, cols, size)
        if (mx >= x + size.swatch || my >= y + size.swatch) return null
        return index
    }

    /**
     * [highlightSlot]/[highlightColor] mark the active slot (white in the HUD, gold in the
     * picker). [hotbarOverride] lets the picker show just-assigned colors immediately instead
     * of waiting for the server attachment sync. There is deliberately no preview state: the
     * HUD and the picker are independent surfaces.
     */
    fun draw(
        graphics: GuiGraphicsExtractor,
        originX: Int,
        originY: Int,
        cols: Int,
        size: HudSwatchSize,
        state: BeadModeState,
        highlightSlot: Int = state.selectedSlot,
        highlightColor: Int = OUTLINE_SELECTED,
        hoverSlot: Int? = null,
        hotbarOverride: List<String>? = null,
    ) {
        val colors = hotbarOverride ?: state.hotbar
        for (i in 0 until BeadModeState.SLOT_COUNT) {
            val x = slotX(originX, i, cols, size)
            val y = slotY(originY, i, cols, size)
            val argb = colors.getOrNull(i)?.let { Palette.byId(it)?.argb } ?: EMPTY
            graphics.fill(x, y, x + size.swatch, y + size.swatch, argb)
        }

        if (hoverSlot != null && hoverSlot != highlightSlot) {
            outline(graphics, slotX(originX, hoverSlot, cols, size), slotY(originY, hoverSlot, cols, size), size, OUTLINE_HOVER)
        }
        outline(graphics, slotX(originX, highlightSlot, cols, size), slotY(originY, highlightSlot, cols, size), size, highlightColor)
    }

    /**
     * With only a 1px channel between swatches the ring cannot grow outwards, so the dark
     * outline fills that channel (giving contrast against the neighbours) and the colored
     * outline is drawn *inside* the swatch edge, leaving the core of the color visible.
     */
    private fun outline(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: HudSwatchSize, color: Int) {
        graphics.outline(x - 1, y - 1, size.swatch + 2, size.swatch + 2, OUTLINE_SHADOW)
        graphics.outline(x, y, size.swatch, size.swatch, color)
    }
}
