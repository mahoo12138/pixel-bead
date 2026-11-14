package com.pixelbead.creative

import com.pixelbead.PixelBead
import com.pixelbead.blocks.BeadItems
import com.pixelbead.palette.Palette
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

/** Dedicated "拼豆" creative tab, populated in MARD series + natural code order. */
object BeadCreativeTab {
    val TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PixelBead.MODID)

    val BEAD_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> = TABS.register(
        "bead",
        Supplier {
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.pixelbead"))
                .icon { ItemStack(BeadItems.itemFor("a1") ?: Items.STONE) }
                .displayItems { _, output ->
                    for (c in Palette.ordered) {
                        BeadItems.itemFor(c.id)?.let { output.accept(it) }
                    }
                }
                .build()
        },
    )
}