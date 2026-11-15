package com.pixelbead.client.key

import com.mojang.blaze3d.platform.InputConstants
import com.pixelbead.PixelBead
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

/**
 * Rebindable keys (vanilla controls screen persists rebinds).
 * Verified against decompiled 26.3 source: InputConstants.Type.KEYBOARD (GLFW/KEYSYM
 * replaced by SDL), KeyMapping.Category(Identifier) + RegisterKeyMappingsEvent
 * .registerCategory(), and the (name, Type, scancode, Category) constructor.
 */
object BeadKeyMappings {
    // Category.register(...) is deprecated in 26.3; construct the record directly and
    // let RegisterKeyMappingsEvent.registerCategory() add it to the sort order.
    val CATEGORY: KeyMapping.Category =
        KeyMapping.Category(Identifier.fromNamespaceAndPath(PixelBead.MODID, "bead"))

    val TOGGLE_MODE = KeyMapping("key.pixelbead.toggle_mode", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY)
    val OPEN_PICKER = KeyMapping("key.pixelbead.open_picker", InputConstants.Type.KEYBOARD, InputConstants.KEY_P, CATEGORY)

    /** Held modifier for the area tools (anchor/fill on right click, square break on left). */
    val TOOL = KeyMapping("key.pixelbead.tool", InputConstants.Type.KEYBOARD, InputConstants.KEY_GRAVE, CATEGORY)

    /** Undo the last bead-tool operation. */
    val UNDO = KeyMapping("key.pixelbead.undo", InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY)
}