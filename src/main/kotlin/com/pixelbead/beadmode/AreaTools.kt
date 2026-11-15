package com.pixelbead.beadmode

import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.config.ServerConfig
import com.pixelbead.network.HudMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Fast mode, behind the tool key (`~` by default).
 *
 * Holding the key and right-clicking builds a shape from **two points**: the first click places a
 * bead (place modes) and remembers the point, the second click closes the shape and fills or
 * erases everything between the two, diagonals included. The arrow keys cycle the four modes:
 *
 *  - line place  : one diagonal line of beads between the two points
 *  - line erase  : the same line, but removed
 *  - plane place : the rectangle spanned by the two points (they must lie on one plane)
 *  - plane erase : the same rectangle, removed
 *
 * Safety rails:
 *  - creative only for now: survival keeps plain one-click-per-block behaviour;
 *  - placement still goes through [BeadPlacement.placeAt], i.e. the vanilla use-on pipeline, so
 *    claim mods see one EntityPlaceEvent per cell;
 *  - erasing goes through ServerPlayerGameMode.destroyBlock, so BreakEvent still fires;
 *  - every operation is undoable (`Z`) and capped by `tool.max_area`.
 */
object AreaTools {
    /** A pending first point older than this is stale; the next click just starts over. */
    private const val MARK_TIMEOUT_MS = 60_000L

    fun fastModeAllowed(player: ServerPlayer): Boolean =
        ServerConfig.toolEnabled.get() && player.gameMode.gameModeForPlayer == GameType.CREATIVE

    /**
     * Right click while the tool key is held.
     * @return true when the click was consumed by fast mode.
     */
    fun onFastUse(player: ServerPlayer, clickedPos: BlockPos, face: Direction): Boolean {
        if (!fastModeAllowed(player)) {
            toast(player, "creative_only")
            return true
        }
        val session = ToolSessions.of(player)
        val mode = session.mode
        val target = clickedPos.relative(face)
        val dimension = player.level().dimension()
        val mark = session.mark
        val stale = mark == null ||
            session.markDimension != dimension ||
            System.currentTimeMillis() - session.markAt > MARK_TIMEOUT_MS

        if (stale) {
            // Place modes create the first bead for real; erase modes only remember the point.
            if (!mode.erase) {
                val result = BeadPlacement.placeAt(
                    player, target, clickedPos, face, report = true, trackUndo = true,
                )
                if (result != BeadPlacement.Result.PLACED) return true
            }
            session.mark = target
            session.markDimension = dimension
            session.markAt = System.currentTimeMillis()
            toast(player, "mark", "${target.x} ${target.y} ${target.z}")
            return true
        }

        session.clearMark()
        val cells = cellsBetween(mark!!, target, mode)
        if (cells == null) {
            toast(player, "not_plane")
            return true
        }
        val limit = ServerConfig.toolMaxArea.get()
        if (cells.size > limit) {
            toast(player, "too_large", cells.size.toString(), limit.toString())
            return true
        }
        return if (mode.erase) erase(player, cells) else fill(player, cells, clickedPos, face)
    }

    /** Two points -> the cells of the requested shape, or null when the plane is undefined. */
    private fun cellsBetween(a: BlockPos, b: BlockPos, mode: ToolMode): List<BlockPos>? {
        val ca = ShapeMath.Cell3(a.x, a.y, a.z)
        val cb = ShapeMath.Cell3(b.x, b.y, b.z)
        val shape = if (mode.plane) ShapeMath.plane3(ca, cb) else ShapeMath.line3(ca, cb)
        return shape?.map { BlockPos(it.x, it.y, it.z) }
    }

    private fun fill(
        player: ServerPlayer,
        cells: List<BlockPos>,
        clickedPos: BlockPos,
        face: Direction,
    ): Boolean {
        val level = player.level()
        val placed = ArrayList<UndoOp.Cell>()
        var ranOut = false
        for (pos in cells) {
            if (!level.isLoaded(pos)) continue
            val previous = level.getBlockState(pos)
            if (!previous.canBeReplaced()) continue
            // Free-floating cells have no solid neighbour to click: hit the cell itself.
            val click = if (level.getBlockState(pos.relative(face.opposite)).canBeReplaced()) pos else clickedPos
            when (
                BeadPlacement.placeAt(player, pos, click, face, report = false, trackUndo = false)
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

    /** Removes every block in [cells] — any block, not just beads (the tool key destroys freely). */
    private fun erase(player: ServerPlayer, cells: List<BlockPos>): Boolean {
        val level = player.level()
        val removed = ArrayList<UndoOp.Cell>(cells.size)
        for (pos in cells) {
            if (!level.isLoaded(pos)) continue
            val state = level.getBlockState(pos)
            if (state.isAir) continue
            if (player.gameMode.destroyBlock(pos)) removed += UndoOp.Cell(pos, state)
        }
        UndoStack.record(player, removed, merge = false)
        if (removed.isEmpty()) toast(player, "nothing") else toast(player, "erased", removed.size.toString())
        return removed.isNotEmpty()
    }

    // ---- plain left click eraser (no tool key) ----

    /** Instantly removes the clicked bead plus the cells swept since the previous click. */
    fun eraseStroke(player: ServerPlayer, pos: BlockPos, face: Direction): Boolean {
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

        val level = player.level()
        val removed = ArrayList<UndoOp.Cell>()
        for (cell in cells) {
            val p = BeadPlacement.onPlane(pos, face, cell.u, cell.v)
            if (!level.isLoaded(p)) continue
            val state = level.getBlockState(p)
            if (!BeadBlocks.isBead(state)) continue
            if (player.gameMode.destroyBlock(p)) removed += UndoOp.Cell(p, state)
        }
        if (removed.isNotEmpty()) UndoStack.record(player, removed, merge = true)
        return removed.isNotEmpty()
    }

    fun undo(player: ServerPlayer): Boolean = UndoStack.undo(player)

    private fun toast(player: ServerPlayer, key: String, arg: String = "", arg2: String = "") {
        PacketDistributor.sendToPlayer(player, HudMessage(key, arg, arg2))
    }
}
