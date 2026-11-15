package com.pixelbead.client.gui

import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.beadmode.BeadModeState
import com.pixelbead.client.ClientPrefs
import com.pixelbead.client.hud.BeadHotbarView
import com.pixelbead.config.HudSwatchSize
import com.pixelbead.network.SelectSlot
import com.pixelbead.network.SetHotbarColor
import com.pixelbead.palette.BeadColor
import com.pixelbead.palette.Palette
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.network.ClientPacketDistributor

/**
 * Full 291-color picker with a mirrored copy of the 18-slot hotbar at the bottom.
 *
 * Editing flow: click a slot to target it, then click colors on the palette. Every color
 * click writes to the current slot and **advances the cursor to the next slot** (wrapping
 * at 18), so an entire palette can be filled with consecutive clicks. Assignments are
 * applied to a local hotbar copy immediately (optimistic) and sent to the server.
 *
 * The picker is fully self-contained: while it is open BeadHudLayer hides the in-game HUD,
 * and there is no shared preview state between the two surfaces. Settings are not inlined
 * here — the gear button in the top-right corner opens [PixelBeadOptionsScreen], which uses
 * vanilla's own options layout.
 *
 * 26.3 RenderPearl: extractRenderState(...), mouseClicked(MouseButtonEvent, boolean),
 * GuiGraphicsExtractor.text/centeredText/outline.
 */
class ColorPickerScreen : Screen(Component.translatable("gui.pixelbead.picker")) {
    private var seriesIndex = 0
    private var selectedColorId: String? = null
    private lateinit var search: EditBox
    private var hoverSlot: Int? = null
    private var hoverColorId: String? = null

    /** Slot that receives the next color click. Gold outline in the mirrored bar. */
    private var editSlot = 0

    /** Optimistic mirrors of the attachment so clicks feel instant. */
    private var localHotbar: MutableList<String> = mutableListOf()
    private var initialized = false

    override fun init() {
        val query = if (::search.isInitialized) search.value else ""
        if (!initialized) {
            val state = Minecraft.getInstance().player?.let { BeadModeManager.state(it) }
            selectedColorId = state?.selectedColorId()
            editSlot = state?.selectedSlot ?: 0
            localHotbar = MutableList(BeadModeState.SLOT_COUNT) { i ->
                state?.hotbar?.getOrNull(i) ?: BeadModeState.DEFAULT_HOTBAR[i]
            }
            initialized = true
        }
        search = EditBox(
            font, width / 2 - SEARCH_W / 2, SEARCH_Y, SEARCH_W, SEARCH_H,
            Component.translatable("gui.pixelbead.search"),
        )
        search.setResponder { }
        search.value = query
        addRenderableWidget(search)
        addRenderableWidget(
            Button.builder(Component.translatable("gui.pixelbead.options.open")) {
                val mc = Minecraft.getInstance()
                mc.gui.setScreen(PixelBeadOptionsScreen(this, mc.options))
            }
                // Same row *and* same height as the search box, so the two read as one header line.
                .bounds(width - SETTINGS_BTN_W - 8, SEARCH_Y, SETTINGS_BTN_W, SEARCH_H)
                .build()
        )
    }

    /** Swatch size shared with the in-game HUD, so the mirrored bar matches it exactly. */
    private fun swatchSize(): HudSwatchSize = ClientPrefs.swatchSize()

    private fun hotbarX(): Int =
        (width - BeadHotbarView.widthFor(BeadHotbarView.BOTTOM_COLS, swatchSize())) / 2
    /**
     * The picker's mirrored bar always stays at the bottom, regardless of where the in-game HUD
     * is docked: it is the editing surface, and docking is configured elsewhere (gear button).
     */
    private fun hotbarY(): Int =
        height - BeadHotbarView.heightFor(BeadHotbarView.BOTTOM_COLS, swatchSize()) - BeadHotbarView.BOTTOM_GAP

    /** Rows that fit between the series tabs and the mirrored hotbar. */
    private fun maxVisibleColors(): Int {
        val rows = ((hotbarY() - 16 - GRID_Y) / CELL).coerceAtLeast(1)
        return rows * COLS
    }

    private fun allMatchingColors(): List<BeadColor> {
        val q = search.value.trim().uppercase()
        if (q.isNotEmpty()) return Palette.all.filter { it.code.contains(q) }
        return Palette.series(Palette.seriesOrder[seriesIndex])
    }

    private fun visibleColors(): List<BeadColor> = allMatchingColors().take(maxVisibleColors())

    private fun colorAt(mx: Double, my: Double): BeadColor? {
        if (mx < GRID_X || my < GRID_Y) return null
        val col = ((mx - GRID_X) / CELL).toInt()
        val row = ((my - GRID_Y) / CELL).toInt()
        if (col !in 0 until COLS || row < 0) return null
        val index = row * COLS + col
        val colors = visibleColors()
        if (index !in colors.indices) return null
        val x = GRID_X + col * CELL
        val y = GRID_Y + row * CELL
        if (mx >= x + CELL || my >= y + CELL) return null
        return colors[index]
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF.toInt())

        val tabY = 44
        Palette.seriesOrder.forEachIndexed { i, s ->
            val x = 10 + i * 22
            val color = if (i == seriesIndex) 0xFF404040.toInt() else 0xFF202020.toInt()
            graphics.fill(x, tabY, x + 20, tabY + 14, color)
            graphics.text(font, s, x + 3, tabY + 3, 0xFFFFFFFF.toInt())
        }

        val colors = visibleColors()
        colors.forEachIndexed { i, c ->
            val x = GRID_X + (i % COLS) * CELL
            val y = GRID_Y + (i / COLS) * CELL
            graphics.fill(x, y, x + CELL - 1, y + CELL - 1, c.argb)
            if (c.id == selectedColorId) {
                // Outline, never an opaque rectangle: a filled highlight hides the color.
                graphics.outline(x - 1, y - 1, CELL + 1, CELL + 1, 0xFFFFFFFF.toInt())
            }
        }

        // Hover is only used for the local info line; no state is shared with the game HUD.
        hoverColorId = colorAt(mouseX.toDouble(), mouseY.toDouble())?.id

        val infoY = GRID_Y + ((colors.size + COLS - 1) / COLS) * CELL + 6
        val hover = hoverColorId?.let { Palette.byId(it) }
        val total = allMatchingColors().size
        val info = buildString {
            append("${colors.size} / $total")
            hover?.let { append("   ${it.code}  ${it.hex}") }
        }
        graphics.text(font, info, GRID_X, infoY, 0xFFAAAAAA.toInt())

        val hx = hotbarX()
        val hy = hotbarY()
        val size = swatchSize()
        hoverSlot = BeadHotbarView.slotAt(
            mouseX.toDouble(), mouseY.toDouble(), hx, hy, BeadHotbarView.BOTTOM_COLS, size,
        )
        Minecraft.getInstance().player?.let { player ->
            BeadHotbarView.draw(
                graphics, hx, hy, BeadHotbarView.BOTTOM_COLS, size, BeadModeManager.state(player),
                highlightSlot = editSlot,
                highlightColor = BeadHotbarView.EDIT_COLOR,
                hoverSlot = hoverSlot,
                hotbarOverride = localHotbar,
            )
        }
        graphics.text(font, "1-9", hx - 58, hy + 4, 0xFFAAAAAA.toInt())
        graphics.text(font, "Shift+1-9", hx - 58, hy + BeadHotbarView.cellFor(size) + 4, 0xFFAAAAAA.toInt())
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x()
        val my = event.y()

        val tabY = 44
        Palette.seriesOrder.forEachIndexed { i, _ ->
            val x = 10 + i * 22
            if (mx >= x && mx < x + 20 && my >= tabY && my < tabY + 14) {
                seriesIndex = i
                search.value = ""
                return true
            }
        }

        val color = colorAt(mx, my)
        if (color != null) {
            selectedColorId = color.id
            ClientPrefs.addRecent(color.id)
            // Write to the targeted slot, show it instantly, then move on.
            ClientPacketDistributor.sendToServer(SetHotbarColor(editSlot, color.id))
            localHotbar[editSlot] = color.id
            editSlot = (editSlot + 1) % BeadModeState.SLOT_COUNT
            ClientPacketDistributor.sendToServer(SelectSlot(editSlot))
            return true
        }

        val slot = BeadHotbarView.slotAt(mx, my, hotbarX(), hotbarY(), BeadHotbarView.BOTTOM_COLS, swatchSize())
        if (slot != null) {
            editSlot = slot
            ClientPacketDistributor.sendToServer(SelectSlot(slot))
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val COLS = 16
        const val CELL = 16
        const val GRID_X = 10
        const val GRID_Y = 64

        /** Header row shared by the search box and the settings button. */
        const val SEARCH_Y = 20
        const val SEARCH_H = 18
        const val SEARCH_W = 160

        /** Vanilla-looking "Settings" button in the top-right corner, opens PixelBeadOptionsScreen. */
        const val SETTINGS_BTN_W = 54
    }
}
