package com.pixelbead

import com.pixelbead.beadmode.BeadModeAttachments
import com.pixelbead.blocks.BeadBlocks
import com.pixelbead.blocks.BeadItems
import com.pixelbead.config.ClientConfig
import com.pixelbead.config.ServerConfig
import com.pixelbead.creative.BeadCreativeTab
import com.pixelbead.palette.DyeSeriesMapReload
import com.pixelbead.palette.Palette
import com.pixelbead.workbench.Workbench
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddServerReloadListenersEvent
import org.slf4j.LoggerFactory

@Mod(PixelBead.MODID)
class PixelBead(modEventBus: IEventBus, modContainer: ModContainer) {
    companion object {
        const val MODID = "pixelbead"
        private val LOGGER = LoggerFactory.getLogger("pixelbead")
    }

    init {
        // Force palette load + validation before any registry object initializes.
        Palette.load()
        LOGGER.info("Pixel Bead: loaded {} MARD colors", Palette.all.size)

        BeadBlocks.BLOCKS.register(modEventBus)
        BeadItems.ITEMS.register(modEventBus)
        BeadCreativeTab.TABS.register(modEventBus)

        Workbench.BLOCKS.register(modEventBus)
        Workbench.ITEMS.register(modEventBus)
        Workbench.MENUS.register(modEventBus)

        BeadModeAttachments.ATTACHMENTS.register(modEventBus)

        // 26.3 removed ModConfig.Type.SERVER: SYNCED is the per-world server config
        // (loaded from serverconfig/, reloadable, synced to clients).
        modContainer.registerConfig(ModConfig.Type.SYNCED, ServerConfig.SPEC)
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC)

        // AddServerReloadListenersEvent is a GAME-bus event (not IModBusEvent);
        // AddClientReloadListenersEvent is a MOD-bus event and is handled client-side.
        NeoForge.EVENT_BUS.addListener<AddServerReloadListenersEvent> { e ->
            e.addListener(Identifier.fromNamespaceAndPath(MODID, "dye_series_map"), DyeSeriesMapReload)
        }

        // Game-bus subscribers (BeadModeEvents, BeadNetwork, PixelBeadCommand) and the client
        // subscribers (PixelBeadClient, BeadKeyHandler) are auto-discovered via @EventBusSubscriber.
    }
}