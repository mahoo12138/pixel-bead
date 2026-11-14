package com.pixelbead.client.render

import com.pixelbead.PixelBead
import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.blocks.BeadItems
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RenderHandEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers

/**
 * While bead mode is active the entire vanilla bottom HUD (item bar + status bars) is hidden
 * so the mod's 18-slot HUD can sit flush with the window bottom, and the first-person hand
 * holds the currently selected bead instead of the real inventory item.
 *
 * Both effects are pure client render-state overrides. The real inventory is never mutated,
 * so the virtual hotbar stays decoupled and a dedicated server observes no change at all.
 *
 * Verified against decompiled 26.3: GameRenderer passes exactly
 * playerState.firstPersonHandsAndItems into FirstPersonHandsAndItemsRenderer, and
 * submitArmWithItem() re-reads mainHandItem/offHandItem from that same state object AFTER
 * the RenderHandEvent hook fires. GuiLayerManager posts RenderGuiLayerEvent.Pre per layer
 * and skips render() when it is cancelled.
 */
@EventBusSubscriber(modid = PixelBead.MODID, value = [Dist.CLIENT])
object BeadFirstPersonView {

    private fun active(): Boolean =
        Minecraft.getInstance().player?.let { BeadModeManager.state(it).active } ?: false

    /**
     * Everything vanilla draws along the bottom edge: the item bar, its selection frame,
     * the floating held-item name, health/armor/food/air/vehicle bars and the experience
     * bar/level. Crosshair, effects, chat and boss bar are deliberately left alone.
     */
    private val HIDDEN_LAYERS = setOf(
        VanillaGuiLayers.HOTBAR,
        VanillaGuiLayers.SELECTED_ITEM_NAME,
        VanillaGuiLayers.PLAYER_HEALTH,
        VanillaGuiLayers.ARMOR_LEVEL,
        VanillaGuiLayers.FOOD_LEVEL,
        VanillaGuiLayers.AIR_LEVEL,
        VanillaGuiLayers.VEHICLE_HEALTH,
        VanillaGuiLayers.EXPERIENCE_LEVEL,
        VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
        VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND,
    )

    @SubscribeEvent
    fun onRenderGuiLayer(e: RenderGuiLayerEvent.Pre) {
        // Filter first: this fires once per GUI layer per frame.
        if (e.name !in HIDDEN_LAYERS) return
        if (!active()) return
        e.isCanceled = true
    }

    @SubscribeEvent
    fun onRenderHand(e: RenderHandEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val state = BeadModeManager.state(player)
        if (!state.active) return

        val hands = e.playerState.firstPersonHandsAndItems
        if (e.hand == InteractionHand.OFF_HAND) {
            // Keep the off-hand arm but hide whatever item is really being carried.
            hands.offHandItem = ItemStack.EMPTY
            hands.offHandRenderState.clear()
            return
        }

        val colorId = state.selectedColorId() ?: return
        val item = BeadItems.itemFor(colorId) ?: return
        val stack = ItemStack(item)
        val context = if (player.mainArm == HumanoidArm.RIGHT) {
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
        } else {
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        }

        hands.mainHandItem = stack
        // Rebuild the item render state so the vanilla renderer draws the bead model with
        // the normal swing/equip transforms instead of the real held item.
        mc.itemModelResolver.updateForTopItem(
            hands.mainHandRenderState, stack, context,
            player.level(), player, player.id + context.ordinal,
        )
        // Discard use/charge animation left over from the real item (bead mode blocks use).
        hands.mainHandUseDuration = 0
        hands.mainHandChargeDuration = 0
        hands.useItemRemainingTicks = 0
    }
}
