package com.pixelbead.beadmode

import com.pixelbead.config.ServerConfig
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * One-time greeting, sent the first time a player joins a world with the mod installed.
 *
 * The keys are rendered with [Component.keybind] so the text always shows the key the player
 * actually has bound (vanilla rebinds included) instead of a hard-coded "P" / "B".
 * The "already greeted" flag lives in [BeadModeAttachments.WELCOMED], which survives death.
 */
object WelcomeMessage {
    fun sendIfFirstTime(player: ServerPlayer) {
        // Nothing to greet about when the server switched the whole feature off.
        if (!ServerConfig.enabled.get()) return
        if (player.getData(BeadModeAttachments.WELCOMED.get())) return
        player.setData(BeadModeAttachments.WELCOMED.get(), true)

        player.sendSystemMessage(
            Component.translatable("message.pixelbead.welcome").withStyle(ChatFormatting.AQUA)
        )
        player.sendSystemMessage(
            Component.translatable(
                "message.pixelbead.welcome.hint",
                Component.keybind("key.pixelbead.toggle_mode").withStyle(ChatFormatting.GOLD),
                Component.keybind("key.pixelbead.open_picker").withStyle(ChatFormatting.GOLD),
            ).withStyle(ChatFormatting.GRAY)
        )
    }
}
