package com.pixelbead.beadmode

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

/**
 * Validated bead placement that goes through the real vanilla placement pipeline so
 * claim/protection mods can veto via BlockEvent.EntityPlaceEvent (design §9.3).
 * Position comes from the server-side interaction event, never from a client payload.
 *
 * Verified against decompiled 26.3 source: BlockItem.useOn() alone only calls
 * level.setBlock(); the placement events (block snapshots -> EntityPlaceEvent) are
 * fired by CommonHooks.onPlaceItemIntoWorld(), which is reached only through
 * ItemStack.useOn(). Calling Item.useOn() directly would silently bypass claim mods.
 */
object BeadPlacement {
    /** Our own placement fires UseItemOnBlockEvent; the blanket gate must let it through. */
    private val internalPlacement = ThreadLocal.withInitial { false }

    fun isInternalPlacement(): Boolean = internalPlacement.get()

    /**
     * A silent rejection is indistinguishable from "the mouse button did nothing".
     * Every early exit reports a concrete reason through the HUD toast channel.
     */
    private fun deny(player: ServerPlayer, key: String): Boolean {
        PacketDistributor.sendToPlayer(player, HudMessage(key))
        return false
    }

    fun place(player: ServerPlayer, clickedPos: BlockPos, face: Direction): Boolean {
        val s = BeadModeManager.state(player)
        if (!s.active || !BeadModeManager.canEnter(player)) return deny(player, "denied")
        val colorId = s.selectedColorId() ?: return deny(player, "no_color")
        val item = BeadItems.itemFor(colorId) ?: return deny(player, "no_color")

        val level = player.level()
        val targetPos = clickedPos.relative(face)
        if (!level.isLoaded(targetPos)) return deny(player, "blocked")
        if (!level.isInWorldBounds(targetPos)) return deny(player, "blocked")
        if (!level.worldBorder.isWithinBounds(targetPos)) return deny(player, "blocked")
        if (!player.abilities.mayBuild) return deny(player, "blocked")
        if (!level.getBlockState(targetPos).canBeReplaced()) return deny(player, "blocked")

        val survival = player.gameMode.gameModeForPlayer == GameType.SURVIVAL
        val consume = survival && ServerConfig.survivalConsumeItems.get()
        if (consume && BeadInventory.countBead(player, colorId) <= 0) return deny(player, "insufficient")

        // Detached stack: never touches the player's real inventory.
        val stack = ItemStack(item)
        val hit = BlockHitResult(Vec3.atCenterOf(clickedPos), face, clickedPos, false)
        val ctx = BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)
        val result = try {
            internalPlacement.set(true)
            stack.useOn(ctx)
        } finally {
            internalPlacement.set(false)
        }
        if (result.consumesAction()) {
            if (consume) BeadInventory.consumeBead(player, colorId, 1)
            return true
        }
        // Placement was vetoed downstream (e.g. a claim mod denied EntityPlaceEvent).
        return deny(player, "blocked")
    }
}