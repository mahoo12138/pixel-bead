package com.pixelbead.workbench

import com.pixelbead.config.ServerConfig
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

/**
 * Decides whether a plain block item may be converted into beads.
 * Excludes container/BlockEntity-bearing items, air/fluids/non-placeable, and honors
 * config whitelist (override) / blacklist / allow_modded_blocks.
 *
 * Verified against decompiled 26.3 source: BlockState.hasBlockEntity() and
 * DataComponents.CONTAINER both exist and are used to exclude container blocks.
 */
object ConvertibleBlocks {
    fun isConvertible(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        if (item !is BlockItem) return false
        val block = item.block
        if (block === Blocks.AIR) return false

        val key = BuiltInRegistries.ITEM.getKey(item)
        val id = key.toString()

        if (ServerConfig.whitelist.get().contains(id)) return true
        if (ServerConfig.blacklist.get().contains(id)) return false

        if (ServerConfig.excludeBlockEntities.get() && block.defaultBlockState().hasBlockEntity()) return false
        if (stack.has(DataComponents.CONTAINER)) return false
        if (!ServerConfig.allowModdedBlocks.get() && key.namespace != "minecraft") return false
        return true
    }
}