package com.pixelbead.workbench

import com.pixelbead.blocks.BeadItems
import com.pixelbead.config.ServerConfig
import com.pixelbead.palette.DyeSeriesMap
import com.pixelbead.palette.Palette
import net.minecraft.util.Prediction
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.DataSlot
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Transient per-player crafting menu (no BlockEntity). Input(16-capable) + dye + result.
 * Color selection syncs via a vanilla DataSlot + clickMenuButton (no custom payload).
 * Consumption happens only when the result is taken, with server-side re-validation.
 */
class DyeWorkbenchMenu(id: Int, private val playerInv: Inventory) :
    AbstractContainerMenu(Workbench.DYE_WORKBENCH_MENU.get(), id) {

    /**
     * 26.3 removed SimpleContainer.addListener(); the supported hook is to override
     * setChanged() (vanilla ItemCombinerMenu does the same with ResultContainer).
     */
    private inner class InputContainer : SimpleContainer(2) {
        override fun setChanged() = onInputsChanged()
    }

    private val inputContainer = InputContainer()
    private val resultContainer = SimpleContainer(1)
    private var selectedColorId: String? = null

    private val selectionSlot = object : DataSlot() {
        override fun get(): Int = selectedColorId?.let { cid -> Palette.ordered.indexOfFirst { it.id == cid } } ?: -1
        override fun set(value: Int) {
            selectedColorId = if (value in Palette.ordered.indices) Palette.ordered[value].id else null
        }
    }

    private inner class ResultSlot : Slot(resultContainer, 0, 124, 35) {
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun onTake(player: Player, stack: ItemStack) {
            craftTake()
            super.onTake(player, stack)
        }
    }

    init {
        addSlot(Slot(inputContainer, 0, 20, 20))
        addSlot(Slot(inputContainer, 1, 20, 50))
        addSlot(ResultSlot())
        for (row in 0..2) for (col in 0..8) addSlot(Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
        for (col in 0..8) addSlot(Slot(playerInv, col, 8 + col * 18, 142))
        addDataSlot(selectionSlot)
        onInputsChanged()
    }

    fun selectedColorId(): String? = selectedColorId

    fun currentSeries(): String? {
        val dye = inputContainer.getItem(1)
        return if (dye.isEmpty) null else DyeSeriesMap.seriesFor(dye.item)
    }

    override fun clickMenuButton(player: Player, buttonId: Int): Boolean {
        if (buttonId in Palette.ordered.indices) {
            selectedColorId = Palette.ordered[buttonId].id
            onInputsChanged()
            return true
        }
        return false
    }

    private fun onInputsChanged() {
        val input = inputContainer.getItem(0)
        val dye = inputContainer.getItem(1)
        val batchIn = ServerConfig.blocksPerDye.get()
        val batchOut = ServerConfig.blocksPerBatch.get()
        val series = if (dye.isEmpty) null else DyeSeriesMap.seriesFor(dye.item)
        val color = selectedColorId?.let { Palette.byId(it) }
        val status = WorkbenchLogic.evaluate(
            input.count, ConvertibleBlocks.isConvertible(input), series,
            selectedColorId, color != null && color.series == series, batchIn,
        )
        val out = if (status == WorkbenchLogic.Status.OK && color != null) {
            BeadItems.itemFor(color.id)?.let { ItemStack(it, batchOut) } ?: ItemStack.EMPTY
        } else ItemStack.EMPTY
        resultContainer.setItem(0, out)
        broadcastChanges()
    }

    private fun craftTake() {
        val input = inputContainer.getItem(0)
        val dye = inputContainer.getItem(1)
        val color = selectedColorId?.let { Palette.byId(it) } ?: return
        val batchIn = ServerConfig.blocksPerDye.get()
        val series = DyeSeriesMap.seriesFor(dye.item) ?: return
        if (input.count < batchIn || !ConvertibleBlocks.isConvertible(input) || color.series != series) return
        input.shrink(batchIn)
        dye.shrink(1)
        onInputsChanged()
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY
        val stack = slot.item
        val copy = stack.copy()
        if (index < 3) {
            // menu -> player inventory
            if (!moveItemStackTo(stack, 3, slots.size, true)) return ItemStack.EMPTY
        } else {
            // player -> input slot (0)
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY
        }
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return copy
    }

    override fun stillValid(player: Player): Boolean = true

    override fun removed(player: Player) {
        super.removed(player)
        for (i in 0 until inputContainer.containerSize) {
            val stack = inputContainer.removeItemNoUpdate(i)
            if (!stack.isEmpty) player.inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY)
        }
    }
}