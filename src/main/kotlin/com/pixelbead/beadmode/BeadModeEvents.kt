package com.pixelbead.beadmode

import com.pixelbead.PixelBead
import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.config.ServerConfig
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.TriState
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityTeleportEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent

/**
 * Server-authoritative interception. In bead mode right-click places (or fills) the selected
 * bead and normal block/item *use* is denied (no chests, doors, buckets, food, ...). Breaking
 * non-bead blocks is intentionally **unrestricted**: they mine with left-click at vanilla
 * speed, so this mode can be used for normal demolition work too. Bead blocks themselves are
 * erased instantly, which is what makes iterating on a picture bearable.
 * Cleanup triggers restore abilities and drop per-player tool state (design §6.5 / §9.4).
 */
@EventBusSubscriber(modid = PixelBead.MODID)
object BeadModeEvents {
    @SubscribeEvent
    fun onRightClickBlock(e: PlayerInteractEvent.RightClickBlock) {
        val player = e.entity
        if (!BeadModeManager.state(player).active) return
        e.setUseBlock(TriState.FALSE)
        e.setUseItem(TriState.FALSE)
        e.cancellationResult = InteractionResult.SUCCESS
        // Cancelling stops vanilla block/item use on both sides. startPrediction() still
        // sends ServerboundUseItemOnPacket even when performUseItemOn() short-circuits, so
        // the server re-posts this event and runs the placement below. This ONLY holds if
        // BeadKeyHandler does not cancel InteractionKeyMappingTriggered for attack/use —
        // otherwise startUseItem() is never called and no packet leaves the client at all.
        e.isCanceled = true
        if (player !is ServerPlayer) return
        val face = e.face ?: return
        val tool = ServerConfig.toolEnabled.get() && ToolSessions.of(player).held
        if (tool) {
            // Tool key: first click anchors, second fills the rectangle. Both are real use-on
            // interactions, so no coordinate ever travels in a payload.
            AreaTools.onToolUse(player, e.pos, face, player.isShiftKeyDown)
        } else {
            BeadPlacement.place(player, e.pos, face)
        }
    }

    /**
     * Left click. Bead blocks are erased instantly (the eraser half of the tools); with the
     * tool key held a square is cleared instead. Non-bead blocks keep vanilla mining, so the
     * mode stays usable for ordinary demolition.
     */
    @SubscribeEvent
    fun onLeftClickBlock(e: PlayerInteractEvent.LeftClickBlock) {
        if (e.action != PlayerInteractEvent.LeftClickBlock.Action.START) return
        val player = e.entity as? ServerPlayer ?: return
        if (!BeadModeManager.state(player).active) return
        if (!ServerConfig.toolEnabled.get()) return
        if (!BeadBlocks.isBead(player.level().getBlockState(e.pos))) return

        val face = e.face ?: return
        e.isCanceled = true
        val session = ToolSessions.of(player)
        if (session.held) {
            val now = System.currentTimeMillis()
            if (now - session.lastToolAt >= ServerConfig.toolCooldownMs.get().toLong()) {
                session.lastToolAt = now
                AreaTools.breakArea(player, e.pos, face)
            }
        } else {
            AreaTools.erase(player, e.pos, face)
        }
    }

    @SubscribeEvent
    fun onRightClickItem(e: PlayerInteractEvent.RightClickItem) {
        if (BeadModeManager.state(e.entity).active) e.isCanceled = true
    }

    @SubscribeEvent
    fun onEntityInteract(e: PlayerInteractEvent.EntityInteract) {
        if (BeadModeManager.state(e.entity).active) e.isCanceled = true
    }

    @SubscribeEvent
    fun onUseItemOnBlock(e: UseItemOnBlockEvent) {
        // Defense-in-depth for every vanilla use-on path, but never for our own
        // placement (which itself goes through ItemStack.useOn).
        if (BeadPlacement.isInternalPlacement()) return
        val player = e.player ?: return
        if (BeadModeManager.state(player).active) e.isCanceled = true
    }

    // NOTE: breaking non-bead blocks is deliberately NOT intercepted. Left-click mines them at
    // vanilla speed (design §6.7 was relaxed on 2026-11-15 at the user's request).

    // ---- cleanup triggers ----
    @SubscribeEvent
    fun onLoggedIn(e: PlayerEvent.PlayerLoggedInEvent) {
        (e.entity as? ServerPlayer)?.let { WelcomeMessage.sendIfFirstTime(it) }
    }

    @SubscribeEvent
    fun onRespawn(e: PlayerEvent.PlayerRespawnEvent) {
        (e.entity as? ServerPlayer)?.let {
            BeadModeManager.forceExit(it)
            ToolSessions.clear(it)
        }
    }

    @SubscribeEvent
    fun onChangedDimension(e: PlayerEvent.PlayerChangedDimensionEvent) {
        (e.entity as? ServerPlayer)?.let {
            BeadModeManager.forceExit(it)
            ToolSessions.clear(it)
        }
    }

    @SubscribeEvent
    fun onTeleport(e: EntityTeleportEvent) {
        (e.entity as? ServerPlayer)?.let {
            BeadModeManager.forceExit(it)
            ToolSessions.clear(it)
        }
    }

    @SubscribeEvent
    fun onLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        (e.entity as? ServerPlayer)?.let {
            BeadModeManager.forceExit(it)
            ToolSessions.clear(it)
        }
    }

    @SubscribeEvent
    fun onChangeGameMode(e: PlayerEvent.PlayerChangeGameModeEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.onGameModeChanged(it) }
    }
}
