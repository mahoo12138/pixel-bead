package com.pixelbead.beadmode

import com.mojang.serialization.Codec
import com.pixelbead.PixelBead
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * Registers the bead-mode data attachment. 26.3 has no RegisterAttachmentTypesEvent;
 * attachment types register through a DeferredRegister on NeoForgeRegistries.Keys.ATTACHMENT_TYPES.
 * Serialized (persists) + synced (auto-sent on login/change); no copyOnDeath so death resets mode.
 */
object BeadModeAttachments {
    val ATTACHMENTS: DeferredRegister<AttachmentType<*>> =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, PixelBead.MODID)

    val BEAD_MODE = ATTACHMENTS.register(
        "bead_mode",
        Supplier {
            AttachmentType.builder(Supplier { BeadModeState() })
                .serialize(BeadModeState.CODEC)
                .sync(BeadModeState.STREAM_CODEC)
                .build()
        },
    )

    /**
     * Set once the first-time greeting has been shown. Deliberately a separate type from
     * [BEAD_MODE]: that one must reset on death, this one must not (`copyOnDeath`), otherwise
     * dying and relogging would replay the welcome. Never synced to the client.
     */
    val WELCOMED: DeferredHolder<AttachmentType<*>, AttachmentType<Boolean>> = ATTACHMENTS.register(
        "welcomed",
        Supplier {
            AttachmentType.builder(Supplier { false })
                .serialize(Codec.BOOL.fieldOf("welcomed"))
                .copyOnDeath()
                .build()
        },
    )
}
