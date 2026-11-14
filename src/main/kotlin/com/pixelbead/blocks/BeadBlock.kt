package com.pixelbead.blocks

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * Thin marker subclass so bead-mode interception can do an O(1) `is BeadBlock` check.
 * All visual color comes from the shared tint source; no per-color behavior here.
 */
class BeadBlock(properties: BlockBehaviour.Properties) : Block(properties)