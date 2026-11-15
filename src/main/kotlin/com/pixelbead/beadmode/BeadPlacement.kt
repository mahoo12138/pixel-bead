package com.pixelbead.beadmode

import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.blocks.BeadItems
import com.pixelbead.config.ServerConfig
import com.pixelbead.inventory.BeadInventory
import com.pixelbead.network.HudMessage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.abs

/**
 * Validated bead placement that goes through the real vanilla placement pipeline so
 * claim/protection mods can veto via BlockEvent.EntityPlaceEvent (design §9.3).
 * Position comes from the server-side interaction event, never from a client payload.
 *
 * Verified against decompiled 26.3 source: BlockItem.useOn() alone only calls
 * level.setBlock(); the placement events (block snapshots -> EntityPlaceEvent) are
 * fired by CommonHooks.onPlaceItemIntoWorld(), which is reached only through
 * ItemStack.useOn(). Calling Item.useOn() directly would silently bypass claim mods.
 *
 * 2026-11-15 (bead tools): the single-cell work moved into [placeAt] so area fills, strokes
 * and the undo path can reuse it; [place] keeps the vanilla right-click entry point and
 * additionally bridges the gap the mouse skipped while dragging.
 */
object BeadPlacement {
    /** Outcome of one placement attempt; area tools need to tell "blocked" from "out of beads". */
    enum class Result { PLACED, BLOCKED, NO_BEADS, DENIED, INACTIVE }

    /** Our own placement fires UseItemOnBlockEvent; the blanket gate must let it through. */
    private val internalPlacement = ThreadLocal.withInitial { false }

    fun isInternalPlacement(): Boolean = internalPlacement.get()

    /**
     * A silent rejection is indistinguishable from "the mouse button did nothing".
     * Every early exit reports a concrete reason through the HUD toast channel.
     */
    private fun toast(player: ServerPlayer, key: String, arg: String = "", arg2: String = "") {
        PacketDistributor.sendToPlayer(player, HudMessage(key, arg, arg2))
    }

    /**
     * Vanilla right-click entry point. If the previous placement is on the same plane and close
     * enough, the cells the cursor skipped over are filled first, so dragging fast leaves no gaps.
     */
    fun place(player: ServerPlayer, clickedPos: BlockPos, face: Direction): Boolean {
        if (!BeadModeManager.state(player).active) return false
        val target = clickedPos.relative(face)
        val path = strokePath(player, target, face)
        var ok = false
        path.forEachIndexed { i, pos ->
            val last = i == path.lastIndex
            val click = if (last) clickedPos else pos.relative(face.opposite)
            val result = placeAt(player, pos, click, face, report = last, trackUndo = true)
            if (result == Result.PLACED) ok = true
        }
        rememberStroke(player, target, face)
        return ok
    }

    /**
     * One cell at [targetPos]. [clickedPos] is the block the player's ray actually hit, which is
     * what the vanilla context wants; for area fills it is simply the block behind the cell.
     * [report] is false for the intermediate cells of an area operation (no toast storm).
     */
    fun placeAt(
        player: ServerPlayer,
        targetPos: BlockPos,
        clickedPos: BlockPos,
        face: Direction,
        report: Boolean,
        trackUndo: Boolean,
    ): Result {
        val s = BeadModeManager.state(player)
        if (!s.active || !BeadModeManager.canEnter(player)) {
            if (report) toast(player, "denied")
            return Result.DENIED
        }
        val colorId = s.selectedColorId() ?: run {
            if (report) toast(player, "no_color")
            return Result.DENIED
        }
        val item = BeadItems.itemFor(colorId) ?: run {
            if (report) toast(player, "no_color")
            return Result.DENIED
        }

        val level = player.level()
        if (!level.isLoaded(targetPos)) return reject(report, player, "blocked")
        if (!level.isInWorldBounds(targetPos)) return reject(report, player, "blocked")
        if (!level.worldBorder.isWithinBounds(targetPos)) return reject(report, player, "blocked")
        if (!player.abilities.mayBuild) return reject(report, player, "blocked")
        val previous = level.getBlockState(targetPos)
        if (!previous.canBeReplaced()) return Result.BLOCKED

        val survival = player.gameMode.gameModeForPlayer == GameType.SURVIVAL
        val consume = survival && ServerConfig.survivalConsumeItems.get()
        if (consume && BeadInventory.countBead(player, colorId) <= 0) {
            if (report) toast(player, "insufficient")
            return Result.NO_BEADS
        }

        // Detached stack: never touches the player's real inventory.
        val stack = ItemStack(item)
        // BlockPlaceContext treats a replaceable "clicked" block as the block to fill, so a
        // free-floating cell (support is air) must be hit directly instead of through its
        // neighbour — otherwise the bead lands one block off.
        val hitPos = if (level.getBlockState(clickedPos).canBeReplaced()) targetPos else clickedPos
        val hit = BlockHitResult(Vec3.atCenterOf(hitPos), face, hitPos, false)
        val ctx = BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)
        val result = try {
            internalPlacement.set(true)
            stack.useOn(ctx)
        } finally {
            internalPlacement.set(false)
        }
        if (!result.consumesAction()) {
            // Placement was vetoed downstream (e.g. a claim mod denied EntityPlaceEvent).
            return reject(report, player, "blocked")
        }
        // Belt and braces: a use-on can report success without our cell being filled.
        if (!BeadBlocks.isBead(level.getBlockState(targetPos))) return reject(report, player, "blocked")
        if (consume) BeadInventory.consumeBead(player, colorId, 1)
        if (trackUndo) UndoStack.record(player, listOf(UndoOp.Cell(targetPos, previous)), merge = true)
        return Result.PLACED
    }

    private fun reject(report: Boolean, player: ServerPlayer, key: String): Result {
        if (report) toast(player, key)
        return Result.BLOCKED
    }

    // ---- stroke (drag) support ----

    private fun strokePath(player: ServerPlayer, target: BlockPos, face: Direction): List<BlockPos> {
        if (!ServerConfig.toolEnabled.get()) return listOf(target)
        val session = ToolSessions.of(player)
        val last = session.lastPlace ?: return listOf(target)
        if (session.lastPlaceFace != face) return listOf(target)
        if (System.currentTimeMillis() - session.lastPlaceAt > ToolSessions.STROKE_TIMEOUT_MS) return listOf(target)
        val cells = lineCells(last, target, face, ServerConfig.toolMaxStrokeGap.get()) ?: return listOf(target)
        // First cell is the previous placement (already placed); continue from there.
        return cells.drop(1).map { onPlane(target, face, it.u, it.v) }
    }

    private fun rememberStroke(player: ServerPlayer, target: BlockPos, face: Direction) {
        val session = ToolSessions.of(player)
        session.lastPlace = target
        session.lastPlaceFace = face
        session.lastPlaceAt = System.currentTimeMillis()
    }

    /**
     * Bresenham between two world positions on the same face plane, or null when the two are
     * not on one plane / further apart than the configured gap (a fast flick should not fill
     * half the wall).
     */
    fun lineCells(from: BlockPos, to: BlockPos, face: Direction, maxGap: Int): List<ShapeMath.Cell>? {
        val a = planeCoords(from, face) ?: return null
        val b = planeCoords(to, face) ?: return null
        if (abs(a.u - b.u) > maxGap || abs(a.v - b.v) > maxGap) return null
        return ShapeMath.line(a, b)
    }

    /** The two in-plane coordinates of [pos] for a face normal along [face]'s axis. */
    fun planeCoords(pos: BlockPos, face: Direction): ShapeMath.Cell? = when (face.axis) {
        Direction.Axis.X -> ShapeMath.Cell(pos.y, pos.z)
        Direction.Axis.Y -> ShapeMath.Cell(pos.x, pos.z)
        Direction.Axis.Z -> ShapeMath.Cell(pos.x, pos.y)
    }

    /** Rebuild a world position from in-plane coordinates, keeping the plane's fixed axis. */
    fun onPlane(origin: BlockPos, face: Direction, u: Int, v: Int): BlockPos = when (face.axis) {
        Direction.Axis.X -> BlockPos(origin.x, u, v)
        Direction.Axis.Y -> BlockPos(u, origin.y, v)
        Direction.Axis.Z -> BlockPos(u, v, origin.z)
    }

    /** True when both positions share the axis coordinate the face points along. */
    fun samePlane(a: BlockPos, b: BlockPos, face: Direction): Boolean = when (face.axis) {
        Direction.Axis.X -> a.x == b.x
        Direction.Axis.Y -> a.y == b.y
        Direction.Axis.Z -> a.z == b.z
    }

    /** Convenience for tools that only need to know whether a block is a bead block. */
    fun isBead(pos: BlockPos, player: ServerPlayer): Boolean = BeadBlocks.isBead(player.level().getBlockState(pos))
}
