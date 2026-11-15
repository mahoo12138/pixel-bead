package com.pixelbead.client.key

import com.pixelbead.PixelBead
import com.pixelbead.beadmode.BeadModeManager
import com.pixelbead.beadmode.BeadModeState
import com.pixelbead.client.gui.ColorPickerScreen
import com.pixelbead.network.SelectSlot
import com.pixelbead.network.ToggleModeRequest
import com.pixelbead.network.ToolKey
import com.pixelbead.network.UndoRequest
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.network.ClientPacketDistributor

/**
 * Client input: mode/picker keys, virtual 18-slot hotbar (2 rows x 9) decoupled from the
 * real inventory, the tool modifier and undo. 1-9 select the first row, Shift+1-9 the second,
 * scroll cycles all 18.
 * InputEvent.Key is not cancellable in 26.3, so the real selected slot is forced back each
 * tick while in mode (design §7.1). Scroll + pick-block are cancellable.
 *
 * The tool key is a *held* modifier, so it is sampled every tick and only sent when the state
 * changes (one tiny boolean packet per press/release). Presses inside a screen are ignored:
 * typing `~` in chat must not arm the tools.
 *
 * Verified against decompiled 26.3 source: Minecraft.gui.screen()/setScreen(),
 * ClientPacketDistributor.sendToServer(), InputEvent.Key.getKey() (SDL scancode) +
 * InputConstants.PRESS, KeyMapping.isDown/consumeClick, Inventory.getSelectedSlot().
 */
@EventBusSubscriber(modid = PixelBead.MODID, value = [Dist.CLIENT])
object BeadKeyHandler {
    private var savedRealSlot = 0
    private var wasActive = false
    private var toolSent = false

    @SubscribeEvent
    fun onClientTick(e: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            toolSent = false
            return
        }
        while (BeadKeyMappings.TOGGLE_MODE.consumeClick()) ClientPacketDistributor.sendToServer(ToggleModeRequest())
        if (mc.gui.screen() == null) {
            while (BeadKeyMappings.OPEN_PICKER.consumeClick()) mc.gui.setScreen(ColorPickerScreen())
        }
        val active = BeadModeManager.state(player).active

        // Drain the click queue every tick, only act on it while playing.
        while (BeadKeyMappings.UNDO.consumeClick()) {
            if (active && mc.gui.screen() == null) ClientPacketDistributor.sendToServer(UndoRequest())
        }

        val held = active && mc.gui.screen() == null && BeadKeyMappings.TOOL.isDown
        if (held != toolSent) {
            toolSent = held
            ClientPacketDistributor.sendToServer(ToolKey(held))
        }

        if (active && !wasActive) savedRealSlot = player.inventory.selectedSlot
        if (active) player.inventory.setSelectedSlot(savedRealSlot)
        wasActive = active
    }

    @SubscribeEvent
    fun onLoggedOut(e: ClientPlayerNetworkEvent.LoggingOut) {
        toolSent = false
    }

    @SubscribeEvent
    fun onKey(e: InputEvent.Key) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.gui.screen() != null || !BeadModeManager.state(player).active) return
        if (e.action != InputConstants.PRESS) return
        // getKey() is the SDL scancode, matching InputConstants.KEY_1..KEY_9.
        val slot = when (e.key) {
            InputConstants.KEY_1 -> 0; InputConstants.KEY_2 -> 1; InputConstants.KEY_3 -> 2
            InputConstants.KEY_4 -> 3; InputConstants.KEY_5 -> 4; InputConstants.KEY_6 -> 5
            InputConstants.KEY_7 -> 6; InputConstants.KEY_8 -> 7; InputConstants.KEY_9 -> 8
            else -> -1
        }
        if (slot >= 0) {
            // 1-9 -> first row; Shift+1-9 -> second row. KeyEvent.hasShiftDown() is
            // InputWithModifiers' SDL modifier check in 26.3.
            val index = slot + if (e.keyEvent.hasShiftDown()) BeadModeState.SLOTS_PER_ROW else 0
            ClientPacketDistributor.sendToServer(SelectSlot(index))
        }
    }

    @SubscribeEvent
    fun onScroll(e: InputEvent.MouseScrollingEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.gui.screen() != null) return
        val s = BeadModeManager.state(player)
        if (!s.active) return
        e.isCanceled = true
        val delta = if (e.scrollDeltaY > 0) -1 else 1
        val total = BeadModeState.SLOT_COUNT
        val next = ((s.selectedSlot + delta) % total + total) % total
        ClientPacketDistributor.sendToServer(SelectSlot(next))
    }

    @SubscribeEvent
    fun onInteractionKey(e: InputEvent.InteractionKeyMappingTriggered) {
        val player = Minecraft.getInstance().player ?: return
        if (!BeadModeManager.state(player).active) return
        // NEVER cancel attack/use here. In 26.3 Minecraft.startAttack() and startUseItem()
        // return immediately when this event is cancelled (and continueAttack() short-circuits),
        // so no START_DESTROY_BLOCK packet and no ServerboundUseItemOnPacket ever leave the
        // client. That also means the server-side RightClickBlock chain — and therefore
        // BeadPlacement.place() — is never reached: both mouse buttons look dead.
        // All interaction filtering is done server-side in BeadModeEvents instead.
        // Middle-click is the only input we block: vanilla pick-block would mutate the real
        // hotbar, which must stay decoupled from the virtual bead hotbar.
        if (e.isPickBlock) e.isCanceled = true
    }
}
