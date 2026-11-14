package com.pixelbead.workbench

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * Dye workbench block. No BlockEntity: opens a transient per-player crafting menu.
 * Verified against decompiled 26.3 source: useWithoutItem(BlockState, Level, BlockPos,
 * Player, BlockHitResult), player.openMenu(SimpleMenuProvider, BlockPos), and
 * InteractionResult.CONSUME/SUCCESS are unchanged.
 */
class DyeWorkbenchBlock(properties: BlockBehaviour.Properties) : Block(properties) {
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        player.openMenu(
            SimpleMenuProvider(
                { id, inv, _ -> DyeWorkbenchMenu(id, inv) },
                Component.translatable("container.pixelbead.dye_workbench"),
            ),
            pos,
        )
        return InteractionResult.CONSUME
    }
}