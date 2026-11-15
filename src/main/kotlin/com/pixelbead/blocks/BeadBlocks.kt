package com.pixelbead.blocks

import com.pixelbead.PixelBead
import com.pixelbead.palette.Palette
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/**
 * Registers all 291 bead blocks from the palette. Every color shares one model, one
 * texture and one tint source; only the registry id differs. Uniform properties:
 * full cube, no gravity, no BlockEntity, unified mining speed, no tool requirement.
 */
object BeadBlocks {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(PixelBead.MODID)

    private val holders = LinkedHashMap<String, DeferredBlock<BeadBlock>>()

    init {
        for (c in Palette.ordered) {
            // Explicit Supplier overload: 26.3 also has a UnaryOperator overload,
            // and a bare Kotlin lambda is ambiguous between the two.
            holders[c.id] = BLOCKS.registerBlock("bead_${c.id}", ::BeadBlock, Supplier { uniformProps() })
        }
    }

    /** color id -> deferred block holder (consumed by BeadItems). */
    val blocks: Map<String, DeferredBlock<BeadBlock>> get() = holders

    val ids: Set<String> get() = holders.keys

    fun allBlocks(): List<Block> = holders.values.map { it.get() }

    fun isBead(state: BlockState): Boolean = state.block is BeadBlock

    /** Reverse lookup used by the undo/material bookkeeping: block -> palette color id. */
    private val colorIds: Map<Block, String> by lazy {
        holders.entries.associate { (id, holder) -> holder.get() as Block to id }
    }

    fun colorIdOf(state: BlockState): String? = colorIds[state.block]

    private fun uniformProps(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .destroyTime(0.5f)
            .explosionResistance(6.0f)
            .sound(SoundType.STONE)
}