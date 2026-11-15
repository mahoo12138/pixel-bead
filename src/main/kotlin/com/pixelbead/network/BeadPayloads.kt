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
data class HudMessage(
    val key: String,
    /** Optional `%s` arguments; the toast only formats when they are non-empty. */
    val arg: String = "",
    val arg2: String = "",
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<HudMessage>(id("hud_message"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, HudMessage> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, { it.key },
            ByteBufCodecs.STRING_UTF8, { it.arg },
            ByteBufCodecs.STRING_UTF8, { it.arg2 },
            ::HudMessage,
        )
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/**
 * Tool key state (`~` by default). Still no coordinates: the server only needs to know whether
 * the modifier is held, and derives every position from the interaction events it already gets.
 */
data class ToolKey(val held: Boolean) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<ToolKey>(id("tool_key"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ToolKey> =
            StreamCodec.composite(ByteBufCodecs.BOOL, { it.held }, ::ToolKey)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/** Undo the player's last bead-tool operation. */
data class UndoRequest(val noop: Boolean = false) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<UndoRequest>(id("undo"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, UndoRequest> =
            StreamCodec.unit(UndoRequest())
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}