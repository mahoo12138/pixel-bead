package com.pixelbead.beadmode

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Server-side scratch state for the bead tools: is the tool key held, which fast mode is armed,
 * where is the pending first point, where did the last stroke land, and what can still be undone.
 *
 * Deliberately **not** an attachment: none of it should survive a relog, and a persisted
 * "tool key held" flag would leave a player stuck in tool mode after a crash. Entries are
 * dropped on logout/death/dimension change.
 */
object ToolSessions {
    /** Cells after which a stroke is considered finished (a pause re-arms it). */
    const val STROKE_TIMEOUT_MS: Long = 500L
    /** A stroke area is merged into one undo entry while the player keeps dragging. */
    const val UNDO_MERGE_MS: Long = 800L

    class Session {
        var held: Boolean = false

        /** Fast-mode behaviour, cycled by the arrow keys while the tool key is held. */
        var mode: ToolMode = ToolMode.LINE_PLACE

        /** First marked point of a fast-mode shape (the second click applies it). */
        var mark: BlockPos? = null
        var markDimension: ResourceKey<Level>? = null
        var markAt: Long = 0L

        var lastPlace: BlockPos? = null
        var lastPlaceFace: Direction? = null
        var lastPlaceAt: Long = 0L

        var lastBreak: BlockPos? = null
        var lastBreakFace: Direction? = null
        var lastBreakAt: Long = 0L

        val undo: ArrayDeque<UndoOp> = ArrayDeque()
        var undoMergeAt: Long = 0L

        fun clearMark() {
            mark = null
            markDimension = null
            markAt = 0L
        }

        fun clearStroke() {
            lastPlace = null
            lastPlaceFace = null
            lastPlaceAt = 0L
            lastBreak = null
            lastBreakFace = null
            lastBreakAt = 0L
        }
    }

    private val sessions = HashMap<UUID, Session>()

    fun of(player: Player): Session = sessions.getOrPut(player.uuid) { Session() }

    /** Existing session or null — used by cleanup paths that must not allocate one. */
    fun peek(player: Player): Session? = sessions[player.uuid]

    fun clear(player: Player) {
        sessions.remove(player.uuid)
    }
}

/** One reversible area operation: what every touched cell looked like before we changed it. */
class UndoOp(val cells: MutableList<Cell>) {
    class Cell(val pos: BlockPos, val old: BlockState)
}
