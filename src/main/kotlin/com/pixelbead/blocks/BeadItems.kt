package com.pixelbead.blocks

import com.pixelbead.PixelBead
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * One BlockItem per bead block. registerSimpleBlockItem derives the item id from the
 * block and applies useBlockDescriptionPrefix(), so items share the block lang key
 * ("block.pixelbead.bead_a1" -> "Bead A1" / "拼豆 A1").
 */
object BeadItems {
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(PixelBead.MODID)

    private val holders = LinkedHashMap<String, DeferredItem<BlockItem>>()

    init {
        for ((id, block) in BeadBlocks.blocks) {
            // Supplier<? extends Block> overload accepts DeferredBlock<BeadBlock>.
            holders[id] = ITEMS.registerSimpleBlockItem("bead_$id", block)
        }
    }

    fun itemFor(id: String): Item? = holders[id]?.get()

    fun allItems(): List<Item> = holders.values.map { it.get() }
}