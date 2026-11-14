package com.pixelbead.client.render

import com.pixelbead.palette.Palette
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * One shared block tint source for all 291 beads. Resolves ARGB from the block's
 * registry id via the palette, so world / hand / inventory colors stay consistent.
 *
 * Verified against decompiled 26.3 source: BlockTintSource has exactly
 * color(BlockState) and colorInWorld(BlockState, BlockAndTintGetter, BlockPos);
 * the model's tintindex 0 selects this single source from the registered list.
 */
object BeadBlockTint : BlockTintSource {
    override fun color(state: BlockState): Int = argb(state.block)

    override fun colorInWorld(state: BlockState, level: BlockAndTintGetter, pos: BlockPos): Int =
        argb(state.block)

    private fun argb(block: Block): Int {
        val path = BuiltInRegistries.BLOCK.getKey(block).path
        return Palette.argb(path.removePrefix("bead_"))
    }
}