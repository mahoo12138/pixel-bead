package com.pixelbead.network

import com.pixelbead.PixelBead
import com.pixelbead.beadmode.AreaTools
import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.beadmode.ToolSessions
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Registers custom payloads. Server handlers mutate the attachment inside enqueueWork
 * (main thread); the synced attachment auto-propagates to the client. Clientbound
 * handlers are registered separately in PixelBeadClient (Dist.CLIENT).
 */
@EventBusSubscriber(modid = PixelBead.MODID)
object BeadNetwork {
    @SubscribeEvent
    fun onRegisterPayloads(e: RegisterPayloadHandlersEvent) {
        val reg = e.registrar("1")

        reg.playToServer(ToggleModeRequest.TYPE, ToggleModeRequest.STREAM_CODEC) { _, ctx ->
            ctx.enqueueWork {
                (ctx.player() as? ServerPlayer)?.let { BeadModeManager.toggle(it) }
            }
        }
        reg.playToServer(SelectSlot.TYPE, SelectSlot.STREAM_CODEC) { payload, ctx ->
            ctx.enqueueWork {
                (ctx.player() as? ServerPlayer)?.let { BeadModeManager.selectSlot(it, payload.index) }
            }
        }
        reg.playToServer(SetHotbarColor.TYPE, SetHotbarColor.STREAM_CODEC) { payload, ctx ->
            ctx.enqueueWork {
                (ctx.player() as? ServerPlayer)?.let { BeadModeManager.setHotbarColor(it, payload.slot, payload.colorId) }
            }
        }
        // Tool key is a modifier, not an action: it only flips a flag in the server session.
        // Positions still come from the vanilla interaction events, never from a payload.
        reg.playToServer(ToolKey.TYPE, ToolKey.STREAM_CODEC) { payload, ctx ->
            ctx.enqueueWork {
                (ctx.player() as? ServerPlayer)?.let {
                    ToolSessions.of(it).held = payload.held && !it.isSpectator
                }
            }
        }
        reg.playToServer(UndoRequest.TYPE, UndoRequest.STREAM_CODEC) { _, ctx ->
            ctx.enqueueWork {
                (ctx.player() as? ServerPlayer)?.let { AreaTools.undo(it) }
            }
        }
        // Declare the clientbound payload only; its handler is registered client-side
        // via RegisterClientPayloadHandlersEvent (see PixelBeadClient). This keeps every
        // client-only class out of the common code path on a dedicated server.
        reg.playToClient(HudMessage.TYPE, HudMessage.STREAM_CODEC)
    }
}