package com.pixelbead.workbench

import com.pixelbead.PixelBead
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Registers the dye workbench block, its BlockItem, and its MenuType. */
object Workbench {
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(PixelBead.MODID)
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(PixelBead.MODID)
    val MENUS: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, PixelBead.MODID)

    val DYE_WORKBENCH_BLOCK = BLOCKS.registerBlock(
        "dye_workbench",
        ::DyeWorkbenchBlock,
        Supplier {
            BlockBehaviour.Properties.of().destroyTime(2.5f).explosionResistance(6.0f).sound(SoundType.WOOD)
        },
    )

    val DYE_WORKBENCH_ITEM = ITEMS.registerSimpleBlockItem("dye_workbench", DYE_WORKBENCH_BLOCK)

    val DYE_WORKBENCH_MENU = MENUS.register(
        "dye_workbench",
        Supplier { IMenuTypeExtension.create { id, inv, _ -> DyeWorkbenchMenu(id, inv) } },
    )
}