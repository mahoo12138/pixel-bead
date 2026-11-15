package com.pixelbead.beadmode

import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.blocks.BeadItems
import com.pixelbead.config.ServerConfig
import com.pixelbead.inventory.BeadInventory
import com.pixelbead.network.HudMessage
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Prediction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Bounded per-player undo for the bead tools: area fills, area breaks and strokes.
 *
 * Every entry stores the *previous* block state of each touched cell, so undo is a plain
 * restore plus the inverse material move:
 *  - a cell we filled  (old = air/plant, now = bead) -> restore, hand 1 bead back
 *  - a cell we broke   (old = bead, now = air)       -> restore, take 1 bead from inventory
 *
 * That makes undo non-exploitable: you cannot loop "break -> undo -> break" to duplicate
 * beads, because restoring a break costs the bead the break dropped. If the bead is not in
 * the inventory yet (the drop is still on the ground) the undo is refused instead of
 * silently losing material — the player can pick the drop up and press undo again.
 *
 * Creative (or `survival_consume_items = false`) has no material flow, so only blocks move.
 */
object UndoStack {
    fun record(player: Player, cells: List<UndoOp.Cell>, merge: Boolean) {
        if (cells.isEmpty()) return
        val steps = ServerConfig.toolUndoSteps.get()
        if (steps <= 0) return
        val session = ToolSessions.of(player)
        val now = System.currentTimeMillis()
        val top = session.undo.lastOrNull()
        if (merge && top != null && now - session.undoMergeAt <= ToolSessions.UNDO_MERGE_MS) {
            top.cells.addAll(cells)
        } else {
            session.undo.addLast(UndoOp(cells.toMutableList()))
        }
        session.undoMergeAt = if (merge) now else 0L
        while (session.undo.size > steps) session.undo.removeFirst()
    }

    fun undo(player: ServerPlayer): Boolean {
        val session = ToolSessions.of(player)
        val op = session.undo.lastOrNull() ?: run {
            PacketDistributor.sendToPlayer(player, HudMessage("undo_empty"))
            return false
        }
        val level = player.level()
        val consume = player.gameMode.gameModeForPlayer == GameType.SURVIVAL &&
            ServerConfig.survivalConsumeItems.get()

        // Only cells that still look untouched are reverted; anything the player changed
        // since (or a claim mod blocking us) is left alone.
        val restore = op.cells.filter { level.getBlockState(it.pos) != it.old }

        if (consume) {
            val needed = HashMap<String, Int>()
            for (cell in restore) {
                val oldBead = BeadBlocks.colorIdOf(cell.old) ?: continue
                val nowBead = BeadBlocks.colorIdOf(level.getBlockState(cell.pos))
                if (nowBead == oldBead) continue
                needed[oldBead] = (needed[oldBead] ?: 0) + 1
            }
            for ((color, count) in needed) {
                if (BeadInventory.countBead(player, color) < count) {
                    PacketDistributor.sendToPlayer(player, HudMessage("undo_beads"))
                    return false
                }
            }
            for ((color, count) in needed) BeadInventory.consumeBead(player, color, count)
            // Refund the beads our fill had consumed.
            for (cell in restore) {
                val nowBead = BeadBlocks.colorIdOf(level.getBlockState(cell.pos)) ?: continue
                if (BeadBlocks.colorIdOf(cell.old) == nowBead) continue
                give(player, nowBead)
            }
        }

        for (cell in restore) level.setBlock(cell.pos, cell.old, 3)
        session.undo.removeLast()
        session.clearStroke()
        session.clearAnchor()
        PacketDistributor.sendToPlayer(player, HudMessage("undo_done", restore.size.toString()))
        return true
    }

    private fun give(player: ServerPlayer, colorId: String) {
        val item = BeadItems.itemFor(colorId) ?: return
        val stack = ItemStack(item)
        if (!player.inventory.add(stack)) player.drop(stack, false, Prediction.SERVER_ONLY)
    }

    /** Drops everything queued for a player (logout / death / dimension change). */
    fun forget(player: Player) {
        val session = ToolSessions.peek(player) ?: return
        session.undo.clear()
        session.clearAnchor()
        session.clearStroke()
    }
}
