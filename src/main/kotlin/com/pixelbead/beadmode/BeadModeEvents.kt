package com.pixelbead.beadmode

import com.pixelbead.PixelBead
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.TriState
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityTeleportEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent

/**
 * Server-authoritative interception. In bead mode right-click always places the selected bead
 * and normal block/item *use* is denied (no chests, doors, buckets, food, ...). Breaking is
 * intentionally **unrestricted**: bead and normal blocks alike can be mined with left-click at
 * vanilla speed, so this mode can be used for normal demolition work too.
 * Cleanup triggers restore abilities via the single forceExit path (design §6.5 / §9.4).
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
        if (player is ServerPlayer) e.face?.let { face -> BeadPlacement.place(player, e.pos, face) }
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

    // NOTE: breaking is deliberately NOT intercepted. Left-click mines bead and normal blocks
    // alike at vanilla speed (design §6.7 was relaxed on 2026-11-15 at the user's request).

    // ---- cleanup triggers ----
    @SubscribeEvent
    fun onLoggedIn(e: PlayerEvent.PlayerLoggedInEvent) {
        (e.entity as? ServerPlayer)?.let { WelcomeMessage.sendIfFirstTime(it) }
    }

    @SubscribeEvent
    fun onRespawn(e: PlayerEvent.PlayerRespawnEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.forceExit(it) }
    }

    @SubscribeEvent
    fun onChangedDimension(e: PlayerEvent.PlayerChangedDimensionEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.forceExit(it) }
    }

    @SubscribeEvent
    fun onTeleport(e: EntityTeleportEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.forceExit(it) }
    }

    @SubscribeEvent
    fun onLoggedOut(e: PlayerEvent.PlayerLoggedOutEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.forceExit(it) }
    }

    @SubscribeEvent
    fun onChangeGameMode(e: PlayerEvent.PlayerChangeGameModeEvent) {
        (e.entity as? ServerPlayer)?.let { BeadModeManager.onGameModeChanged(it) }
    }
}