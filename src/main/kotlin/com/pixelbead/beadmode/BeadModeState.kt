package com.pixelbead.beadmode

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Per-player bead-mode state, stored as a synced + serialized data attachment.
 * Server is the only writer. The sync codec carries only what the client HUD needs
 * (active / selectedSlot / hotbar); flight bookkeeping stays server-side.
 *
 * No copyOnDeath: death must reset the mode (design §6.2).
 */
data class BeadModeState(
    val active: Boolean = false,
    val selectedSlot: Int = 0,
    val hotbar: List<String> = DEFAULT_HOTBAR,
    val hotbarInitialized: Boolean = false,
    val priorMayFly: Boolean = false,
    val priorFlying: Boolean = false,
    val flightGranted: Boolean = false,
) {
    fun selectedColorId(): String? = hotbar.getOrNull(selectedSlot)

    /**
     * Saves written before the two-row layout had a 9-element hotbar. Normalise on read so
     * slots 9..17 are always addressable and old worlds do not show empty grey swatches.
     */
    fun normalized(): BeadModeState {
        if (hotbar.size == SLOT_COUNT && selectedSlot in 0 until SLOT_COUNT) return this
        val fixed = List(SLOT_COUNT) { i -> hotbar.getOrNull(i) ?: DEFAULT_HOTBAR[i] }
        return copy(hotbar = fixed, selectedSlot = selectedSlot.coerceIn(0, SLOT_COUNT - 1))
    }

    companion object {
        const val SLOTS_PER_ROW: Int = 9
        const val SLOT_COUNT: Int = SLOTS_PER_ROW * 2

        /** First row = 1-9, second row = Shift+1-9. All ids verified against mard-291.csv. */
        val DEFAULT_HOTBAR: List<String> = listOf(
            "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1", "m1",
            "p1", "q1", "r1", "t1", "y1", "zg1", "a2", "b2", "c2",
        )

        val CODEC: MapCodec<BeadModeState> = RecordCodecBuilder.mapCodec { inst ->
            inst.group(
                Codec.BOOL.fieldOf("active").forGetter { it.active },
                Codec.INT.fieldOf("selected_slot").forGetter { it.selectedSlot },
                Codec.STRING.listOf().fieldOf("hotbar").forGetter { it.hotbar },
                Codec.BOOL.fieldOf("hotbar_initialized").forGetter { it.hotbarInitialized },
                Codec.BOOL.fieldOf("prior_may_fly").forGetter { it.priorMayFly },
                Codec.BOOL.fieldOf("prior_flying").forGetter { it.priorFlying },
                Codec.BOOL.fieldOf("flight_granted").forGetter { it.flightGranted },
            ).apply(inst, ::BeadModeState)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BeadModeState> = StreamCodec.composite(
            ByteBufCodecs.BOOL, { it.active },
            ByteBufCodecs.VAR_INT, { it.selectedSlot },
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), { it.hotbar },
        ) { active, slot, hotbar -> BeadModeState(active = active, selectedSlot = slot, hotbar = hotbar) }
    }
}