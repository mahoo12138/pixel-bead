package com.pixelbead.client

import com.pixelbead.PixelBead
import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.client.gui.DyeWorkbenchScreen
import com.pixelbead.client.gui.PixelBeadOptionsScreen
import com.pixelbead.client.hud.BeadHudLayer
import com.pixelbead.client.key.BeadKeyMappings
import com.pixelbead.client.render.BeadBlockTint
import com.pixelbead.client.render.BeadItemTint
import com.pixelbead.network.HudMessage
import com.pixelbead.palette.DyeSeriesMapReload
import com.pixelbead.workbench.Workbench
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent

/**
 * Client-only mod-bus registration (auto-routed: these events implement IModBusEvent).
 * Guarded by Dist.CLIENT so the dedicated server never loads this class.
 */
@EventBusSubscriber(modid = PixelBead.MODID, value = [Dist.CLIENT])
object PixelBeadClient {
    /**
     * Mods list -> Pixel Bead -> Config opens the same screen as the picker's gear button,
     * instead of NeoForge's auto-generated configuration screen. Registered at client setup
     * (not in the common @Mod constructor) so a dedicated server never touches this class.
     */
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        ModList.get().getModContainerById(PixelBead.MODID).ifPresent { container ->
            container.registerExtensionPoint<IConfigScreenFactory>(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent ->
                    PixelBeadOptionsScreen(parent, Minecraft.getInstance().options)
                },
            )
        }
    }

    @SubscribeEvent
    fun onBlockTints(event: RegisterColorHandlersEvent.BlockTintSources) {
        event.register(listOf(BeadBlockTint), *BeadBlocks.allBlocks().toTypedArray())
    }

    @SubscribeEvent
    fun onItemTints(event: RegisterColorHandlersEvent.ItemTintSources) {
        event.register(Identifier.fromNamespaceAndPath(PixelBead.MODID, "bead_color"), BeadItemTint.MAP_CODEC)
    }

    @SubscribeEvent
    fun onClientPayloadHandlers(event: RegisterClientPayloadHandlersEvent) {
        // Clientbound handlers belong here in 26.3 (not in the common registrar),
        // so the dedicated server never even references a client-only class.
        event.register(HudMessage.TYPE) { payload, _ -> BeadHudLayer.showToast(payload.key, payload.arg, payload.arg2) }
    }

    @SubscribeEvent
    fun onKeyMappings(event: RegisterKeyMappingsEvent) {
        event.registerCategory(BeadKeyMappings.CATEGORY)
        event.register(BeadKeyMappings.TOGGLE_MODE)
        event.register(BeadKeyMappings.OPEN_PICKER)
        event.register(BeadKeyMappings.TOOL)
        event.register(BeadKeyMappings.UNDO)
    }

    @SubscribeEvent
    fun onMenuScreens(event: RegisterMenuScreensEvent) {
        event.register(Workbench.DYE_WORKBENCH_MENU.get()) { menu, inv, title -> DyeWorkbenchScreen(menu, inv, title) }
    }

    @SubscribeEvent
    fun onGuiLayers(event: RegisterGuiLayersEvent) {
        // Verified: VanillaGuiLayers.HOTBAR = minecraft:hotbar.
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(PixelBead.MODID, "bead_hud"), BeadHudLayer)
    }

    @SubscribeEvent
    fun onClientReload(event: AddClientReloadListenersEvent) {
        event.addListener(Identifier.fromNamespaceAndPath(PixelBead.MODID, "dye_series_map"), DyeSeriesMapReload)
    }
}