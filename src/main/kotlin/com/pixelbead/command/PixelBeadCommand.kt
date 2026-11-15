package com.pixelbead.command

import com.pixelbead.PixelBead
import com.pixelbead.beadmode.BeadModeManager
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.permissions.Permissions
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * /pixelbead enable|disable|status — game-master mode control for self.
 * 26.3 replaced numeric `hasPermission(2)` with a PermissionSet/Permission model.
 */
@EventBusSubscriber(modid = PixelBead.MODID)
object PixelBeadCommand {
    private val ADMIN: java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> =
        java.util.function.Predicate { src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }

    @SubscribeEvent
    fun onRegisterCommands(e: RegisterCommandsEvent) {
        e.dispatcher.register(
            Commands.literal("pixelbead")
                .then(
                    Commands.literal("enable").requires(ADMIN).executes { ctx ->
                        BeadModeManager.enter(ctx.source.playerOrException); 1
                    },
                )
                .then(
                    Commands.literal("disable").requires(ADMIN).executes { ctx ->
                        BeadModeManager.forceExit(ctx.source.playerOrException); 1
                    },
                )
                .then(
                    Commands.literal("status").executes { ctx ->
                        val p = ctx.source.playerOrException
                        val s = BeadModeManager.state(p)
                        ctx.source.sendSystemMessage(Component.literal("Bead mode active=${s.active} slot=${s.selectedSlot} color=${s.selectedColorId()}"))
                        1
                    },
                )
        )
    }
}