package com.pixelbead.beadmode

import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.config.ServerConfig
import com.pixelbead.network.HudMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

/**
 * The bead tools behind the tool key (`~` by default):
 *
 *  - tool key + right click: first click sets an anchor, second click fills the rectangle
 *    between the two corners on that face plane (hold sneak for a single line instead).
 *  - tool key + left click: breaks a square of bead blocks in the face plane (3x3 by default).
 *  - plain left click (no tool key): the eraser stroke — every bead block the cursor sweeps
 *    over is removed, with the skipped cells interpolated like the painting stroke.
 *
 * Safety rails, on purpose:
 *  - only bead blocks are ever removed in bulk, so a stray tool click can never eat a build;
 *  - every placement still goes through [BeadPlacement.placeAt], i.e. the vanilla use-on
 *    pipeline, so claim mods keep seeing one EntityPlaceEvent per cell;
 *  - every break goes through ServerPlayerGameMode.destroyBlock, which fires BreakEvent;
 *  - undo is capped by `tool.undo_steps` and needs the material back in the inventory.
 */
object AreaTools {
    /** An anchor older than this is stale and the next click just re-anchors. */
    private const val ANCHOR_TIMEOUT_MS = 30_000L

    /** `tool` + right click: anchor on first click, fill on the second. */
    fun onToolUse(player: ServerPlayer, clickedPos: BlockPos, face: Direction, line: Boolean): Boolean {
        val session = ToolSessions.of(player)
        val dimension = player.level().dimension()
        val target = clickedPos.relative(face)
        val stale = System.currentTimeMillis() - session.anchorAt > ANCHOR_TIMEOUT_MS
        val anchor = session.anchor
        val reusable = anchor != null && !stale &&
            session.anchorFace == face &&
            session.anchorDimension == dimension &&
            BeadPlacement.samePlane(anchor, target, face)

        if (!reusable) {
            session.anchor = target
            session.anchorFace = face
            session.anchorDimension = dimension
            session.anchorAt = System.currentTimeMillis()
            toast(player, "anchor", "${target.x} ${target.y} ${target.z}")
            return true
        }

        session.clearAnchor()
        val a = BeadPlacement.planeCoords(anchor, face) ?: return false
        val b = BeadPlacement.planeCoords(target, face) ?: return false
        val cells = if (line) ShapeMath.line(a, b) else ShapeMath.rect(a, b)
        return fill(player, anchor, face, cells)
    }

    /** Fills [cells] (plane coordinates) with the selected bead, anchored on [anchor]. */
    private fun fill(
        player: ServerPlayer,
        anchor: BlockPos,
        face: Direction,
        cells: List<ShapeMath.Cell>,
    ): Boolean {
        val limit = ServerConfig.toolMaxArea.get()
        if (cells.size > limit) {
            toast(player, "too_large", cells.size.toString(), limit.toString())
            return false
        }
        val level = player.level()
        val placed = ArrayList<UndoOp.Cell>()
        var ranOut = false
        for (cell in cells) {
            val pos = BeadPlacement.onPlane(anchor, face, cell.u, cell.v)
            if (!level.isLoaded(pos)) continue
            val previous = level.getBlockState(pos)
            if (!previous.canBeReplaced()) continue
            when (
                BeadPlacement.placeAt(
                    player, pos, pos.relative(face.opposite), face,
                    report = false, trackUndo = false,
                )
            ) {
                BeadPlacement.Result.PLACED -> placed += UndoOp.Cell(pos, previous)
                BeadPlacement.Result.NO_BEADS -> {
                    ranOut = true
                    break
                }
                else -> {}
            }
        }
        UndoStack.record(player, placed, merge = false)
        when {
            ranOut -> toast(player, "insufficient")
            placed.isEmpty() -> toast(player, "blocked")
            else -> toast(player, "filled", placed.size.toString())
        }
        return placed.isNotEmpty()
    }

    /** Tool key + left click: square of bead blocks in the face plane. @return true if handled. */
    fun breakArea(player: ServerPlayer, pos: BlockPos, face: Direction): Boolean {
        if (!player.abilities.mayBuild) return false
        val center = BeadPlacement.planeCoords(pos, face) ?: return false
        val radius = ServerConfig.toolBreakRadius.get()
        val broken = collectAndDestroy(player, pos, face, ShapeMath.square(center, radius))
        if (broken.isEmpty()) {
            toast(player, "nothing")
            return true
        }
        UndoStack.record(player, broken, merge = false)
        toast(player, "broken", broken.size.toString())
        return true
    }

    /** Plain left click in bead mode: remove this bead block plus everything swept since. */
    fun erase(player: ServerPlayer, pos: BlockPos, face: Direction): Boolean {
        val session = ToolSessions.of(player)
        val now = System.currentTimeMillis()
        val cells = ArrayList<ShapeMath.Cell>()
        val last = session.lastBreak
        if (last != null && session.lastBreakFace == face &&
            now - session.lastBreakAt <= ToolSessions.STROKE_TIMEOUT_MS
        ) {
            BeadPlacement.lineCells(last, pos, face, ServerConfig.toolMaxStrokeGap.get())?.let(cells::addAll)
        }
        if (cells.isEmpty()) BeadPlacement.planeCoords(pos, face)?.let(cells::add)
        session.lastBreak = pos
        session.lastBreakFace = face
        session.lastBreakAt = now

        val broken = collectAndDestroy(player, pos, face, cells)
        if (broken.isNotEmpty()) UndoStack.record(player, broken, merge = true)
        return broken.isNotEmpty()
    }

    /**
     * Breaks the bead blocks among [cells] (plane coordinates anchored on [origin]) and returns
     * what was removed so undo can put it back. Non-bead cells are skipped: bulk removal must
     * never touch a normal build.
     */
    private fun collectAndDestroy(
        player: ServerPlayer,
        origin: BlockPos,
        face: Direction,
        cells: List<ShapeMath.Cell>,
    ): List<UndoOp.Cell> {
        val level = player.level()
        val broken = ArrayList<UndoOp.Cell>(cells.size)
        for (cell in cells) {
            val pos = BeadPlacement.onPlane(origin, face, cell.u, cell.v)
            if (!level.isLoaded(pos)) continue
            val state = level.getBlockState(pos)
            if (!BeadBlocks.isBead(state)) continue
            if (player.gameMode.destroyBlock(pos)) broken += UndoOp.Cell(pos, state)
        }
        return broken
    }

    fun undo(player: ServerPlayer): Boolean = UndoStack.undo(player)

    private fun toast(player: ServerPlayer, key: String, arg: String = "", arg2: String = "") {
        PacketDistributor.sendToPlayer(player, HudMessage(key, arg, arg2))
    }
}
