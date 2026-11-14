package com.pixelbead.inventory

import com.pixelbead.blocks.BeadItems
import net.minecraft.world.entity.player.Player

/** Survival material lookup/consumption across the whole inventory (incl. hotbar + offhand). */
object BeadInventory {
    fun countBead(player: Player, colorId: String): Int {
        val item = BeadItems.itemFor(colorId) ?: return 0
        val inv = player.inventory
        var count = 0
        for (i in 0 until inv.containerSize) {
            val stack = inv.getItem(i)
            if (stack.item == item) count += stack.count
        }
        return count
    }

    /** Consume [amount] beads of [colorId]; returns false (and consumes nothing) if insufficient. */
    fun consumeBead(player: Player, colorId: String, amount: Int): Boolean {
        val item = BeadItems.itemFor(colorId) ?: return false
        if (countBead(player, colorId) < amount) return false
        val inv = player.inventory
        var remaining = amount
        for (i in 0 until inv.containerSize) {
            if (remaining <= 0) break
            val stack = inv.getItem(i)
            if (stack.item == item) {
                val take = minOf(remaining, stack.count)
                stack.shrink(take)
                remaining -= take
            }
        }
        return remaining <= 0
    }
}