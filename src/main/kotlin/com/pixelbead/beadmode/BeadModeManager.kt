package com.pixelbead.beadmode

import com.pixelbead.PixelBead
import com.pixelbead.config.ServerConfig
import com.pixelbead.palette.Palette
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.neoforged.neoforge.common.NeoForgeMod

/**
 * Server-authoritative bead-mode lifecycle.
 *
 * 26.3 flight model: mayFly() = abilities.mayfly || CREATIVE_FLIGHT attribute > 0.
 * Survival flight is granted by adding a transient modifier to NeoForgeMod.CREATIVE_FLIGHT
 * (a syncable boolean attribute), and revoked by removing it — only what the mod granted.
 */
object BeadModeManager {
    private val FLIGHT_MODIFIER = AttributeModifier(
        Identifier.fromNamespaceAndPath(PixelBead.MODID, "bead_mode_flight"),
        1.0,
        AttributeModifier.Operation.ADD_VALUE,
    )

    private fun type() = BeadModeAttachments.BEAD_MODE.get()

    /** Always hand callers an 18-slot state, even for saves written by the 9-slot version. */
    fun state(player: Player): BeadModeState = player.getData(type()).normalized()

    fun setState(player: Player, state: BeadModeState) {
        player.setData(type(), state.normalized())
    }

    fun canEnter(player: ServerPlayer): Boolean {
        if (!ServerConfig.enabled.get()) return false
        return when (player.gameMode.gameModeForPlayer) {
            GameType.CREATIVE -> ServerConfig.creativeAllowed.get()
            GameType.SURVIVAL -> ServerConfig.survivalAllowed.get()
            else -> false
        }
    }

    fun toggle(player: ServerPlayer) {
        if (state(player).active) exit(player) else enter(player)
    }

    fun enter(player: ServerPlayer) {
        if (!canEnter(player)) return
        val s = state(player)
        if (s.active) return

        val priorMayFly = player.mayFly()
        val priorFlying = player.abilities.flying
        var granted = false
        if (player.gameMode.gameModeForPlayer == GameType.SURVIVAL && ServerConfig.survivalFlightEnabled.get()) {
            player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)?.addTransientModifier(FLIGHT_MODIFIER)
            granted = true
        }
        player.stopUsingItem()
        setState(player, s.copy(active = true, priorMayFly = priorMayFly, priorFlying = priorFlying, flightGranted = granted))
    }

    fun exit(player: ServerPlayer) {
        val s = state(player)
        if (!s.active) return
        restoreFlight(player, s)
        setState(player, s.copy(active = false, flightGranted = false))
    }

    /** Unconditional cleanup used by death/teleport/dimension/logout/admin/config triggers. */
    fun forceExit(player: ServerPlayer) {
        val s = state(player)
        restoreFlight(player, s)
        if (s.active || s.flightGranted) setState(player, s.copy(active = false, flightGranted = false))
    }

    private fun restoreFlight(player: ServerPlayer, s: BeadModeState) {
        if (s.flightGranted) {
            player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)?.removeModifier(FLIGHT_MODIFIER)
            // Stop actual flight only if the player wasn't flying before we granted it.
            if (player.abilities.flying && !s.priorFlying) {
                player.abilities.flying = false
                player.onUpdateAbilities()
            }
        }
    }

    fun selectSlot(player: ServerPlayer, index: Int) {
        if (index !in 0 until BeadModeState.SLOT_COUNT) return
        val s = state(player)
        if (!s.active) return
        setState(player, s.copy(selectedSlot = index))
    }

    fun setHotbarColor(player: ServerPlayer, slot: Int, colorId: String) {
        if (slot !in 0 until BeadModeState.SLOT_COUNT) return
        val color = Palette.byId(colorId) ?: return
        val s = state(player)
        val hotbar = s.hotbar.toMutableList().apply { this[slot] = color.id }
        setState(player, s.copy(hotbar = hotbar, hotbarInitialized = true))
    }

    /** Re-evaluate after a gamemode switch mid-mode. */
    fun onGameModeChanged(player: ServerPlayer) {
        val s = state(player)
        if (!s.active) return
        if (!canEnter(player)) {
            forceExit(player)
            return
        }
        val isSurvival = player.gameMode.gameModeForPlayer == GameType.SURVIVAL
        if (!isSurvival && s.flightGranted) {
            // Creative already grants flight; drop our redundant modifier.
            player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)?.removeModifier(FLIGHT_MODIFIER)
            setState(player, s.copy(flightGranted = false))
        } else if (isSurvival && !s.flightGranted && ServerConfig.survivalFlightEnabled.get()) {
            player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)?.addTransientModifier(FLIGHT_MODIFIER)
            setState(player, s.copy(priorFlying = player.abilities.flying, flightGranted = true))
        }
    }
}