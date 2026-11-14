package com.pixelbead.client.render

import com.mojang.serialization.MapCodec
import com.pixelbead.palette.Palette
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/**
 * One shared item tint source registered as `pixelbead:bead_color`, referenced by every
 * generated item-model JSON. Resolves ARGB from the item's registry id via the palette.
 *
 * Verified against decompiled 26.3 source: ItemTintSources.CODEC = dispatch(type()).
 * type() MUST return the exact MapCodec instance registered in
 * RegisterColorHandlersEvent.ItemTintSources, otherwise the id mapper cannot
 * serialise it back to `pixelbead:bead_color` (a fresh MapCodec.unit() would fail).
 */
object BeadItemTint : ItemTintSource {
    val MAP_CODEC: MapCodec<BeadItemTint> = MapCodec.unit(this)

    override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int {
        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        return Palette.argb(path.removePrefix("bead_"))
    }

    override fun type(): MapCodec<out ItemTintSource> = MAP_CODEC
}