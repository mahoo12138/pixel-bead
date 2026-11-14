package com.pixelbead.config

import com.pixelbead.beadmode.BeadModeState
import net.neoforged.neoforge.common.ModConfigSpec
import java.util.function.Supplier

/** Client-only config (TOML). Key rebinds are persisted by vanilla KeyMapping, not here. */
object ClientConfig {
    private val builder = ModConfigSpec.Builder()

    val defaultHotbar: ModConfigSpec.ConfigValue<List<String>> =
        builder.comment("Default 18 virtual hotbar color ids (2 rows x 9) for first join.")
            .defineList("hotbar.defaults", BeadModeState.DEFAULT_HOTBAR, Supplier { "" }) { it is String }
    val hudEnabled: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("hud.enabled", true)
    val hudPosition: ModConfigSpec.EnumValue<HudPosition> =
        builder.comment(
            "Where the virtual hotbar is drawn: BOTTOM (9x2, flush with the window bottom),",
            "LEFT or RIGHT (single column of 18, flush with that side edge).",
            "Also editable in-game from the settings screen (picker gear button / Mods list).",
        ).defineEnum("hud.position", HudPosition.BOTTOM)
    val hudSwatchSize: ModConfigSpec.EnumValue<HudSwatchSize> =
        builder.comment(
            "Swatch size of the virtual hotbar: SMALL (12px), MEDIUM (16px) or LARGE (20px).",
            "Drives the in-game HUD and the picker's mirrored bar from the same geometry.",
            "Also editable in-game from the settings screen (picker gear button / Mods list).",
        ).defineEnum("hud.swatch_size", HudSwatchSize.MEDIUM)
    val pickerGridSize: ModConfigSpec.ConfigValue<Int> =
        builder.defineInRange("picker.grid_size", 16, 8, 32)
    val favorites: ModConfigSpec.ConfigValue<List<String>> =
        builder.defineList("picker.favorites", emptyList<String>(), Supplier { "" }) { it is String }
    val recents: ModConfigSpec.ConfigValue<List<String>> =
        builder.defineList("picker.recents", emptyList<String>(), Supplier { "" }) { it is String }

    val SPEC: ModConfigSpec = builder.build()
}