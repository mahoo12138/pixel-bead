package com.pixelbead.network

import com.pixelbead.PixelBead
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client -> server requests carry NO coordinates and NO placement color: placement is
 * driven by the vanilla right-click packet and the server derives position from the
 * interaction event (design §9.1 / §11.4). Breaking needs no payload at all.
 */
private fun id(path: String) = Identifier.fromNamespaceAndPath(PixelBead.MODID, path)

data class ToggleModeRequest(val noop: Boolean = false) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<ToggleModeRequest>(id("toggle_mode"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ToggleModeRequest> =
            StreamCodec.unit(ToggleModeRequest())
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

data class SelectSlot(val index: Int) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<SelectSlot>(id("select_slot"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectSlot> =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, { it.index }, ::SelectSlot)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

data class SetHotbarColor(val slot: Int, val colorId: String) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<SetHotbarColor>(id("set_hotbar_color"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SetHotbarColor> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, { it.slot },
            ByteBufCodecs.STRING_UTF8, { it.colorId },
            ::SetHotbarColor,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Server -> client HUD toast (translation key or status code, e.g. "insufficient"). */
data class HudMessage(val key: String) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<HudMessage>(id("hud_message"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, HudMessage> =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, { it.key }, ::HudMessage)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}