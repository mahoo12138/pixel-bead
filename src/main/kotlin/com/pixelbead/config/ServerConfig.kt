package com.pixelbead.config

import net.neoforged.neoforge.common.ModConfigSpec
import java.util.function.Supplier

/** Server-authoritative config (TOML). Registered as ModConfig.Type.SYNCED in 26.3. */
object ServerConfig {
    private val builder = ModConfigSpec.Builder()

    // [bead_mode]
    val enabled: ModConfigSpec.ConfigValue<Boolean> =
        builder.comment("Master switch for bead mode.").define("bead_mode.enabled", true)
    val creativeAllowed: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("bead_mode.creative_allowed", true)
    val survivalAllowed: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("bead_mode.survival_allowed", true)
    val survivalFlightEnabled: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("bead_mode.survival_flight_enabled", true)
    val survivalConsumeItems: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("bead_mode.survival_consume_items", true)

    // [crafting]
    val blocksPerDye: ModConfigSpec.ConfigValue<Int> =
        builder.comment("Input blocks consumed per dye.").defineInRange("crafting.blocks_per_dye", 16, 1, 64)
    val blocksPerBatch: ModConfigSpec.ConfigValue<Int> =
        builder.comment("Bead items produced per batch.").defineInRange("crafting.blocks_per_batch", 16, 1, 64)
    val allowModdedBlocks: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("crafting.allow_modded_blocks", true)
    val excludeBlockEntities: ModConfigSpec.ConfigValue<Boolean> =
        builder.define("crafting.exclude_block_entities", true)
    val whitelist: ModConfigSpec.ConfigValue<List<String>> =
        builder.comment("Registry ids always allowed as workbench input (overrides other rules).")
            .defineList("crafting.whitelist", emptyList<String>(), Supplier { "" }) { it is String }
    val blacklist: ModConfigSpec.ConfigValue<List<String>> =
        builder.comment("Registry ids never allowed as workbench input.")
            .defineList("crafting.blacklist", emptyList<String>(), Supplier { "" }) { it is String }

    // [tool]
    val toolEnabled: ModConfigSpec.ConfigValue<Boolean> = builder
        .comment(
            "Bead tools: drag-stroke painting/erasing, tool-key area fill and area break.",
            "Turning this off restores plain one-click-per-block behaviour.",
        )
        .define("tool.enabled", true)
    val toolMaxStrokeGap: ModConfigSpec.ConfigValue<Int> = builder
        .comment("Largest gap (in blocks) a drag stroke may bridge before it stops interpolating.")
        .defineInRange("tool.max_stroke_gap", 8, 1, 32)
    val toolMaxArea: ModConfigSpec.ConfigValue<Int> = builder
        .comment("Largest rectangle a single area fill may touch.")
        .defineInRange("tool.max_area", 512, 1, 4096)
    val toolBreakRadius: ModConfigSpec.ConfigValue<Int> = builder
        .comment("Radius of the tool-key area break in the face plane (1 = 3x3).")
        .defineInRange("tool.break_radius", 1, 1, 3)
    val toolUndoSteps: ModConfigSpec.ConfigValue<Int> = builder
        .comment("How many bead-tool operations can be undone per player (0 disables undo).")
        .defineInRange("tool.undo_steps", 8, 0, 32)
    val toolCooldownMs: ModConfigSpec.ConfigValue<Int> = builder
        .comment("Minimum interval between two tool-key area operations, in milliseconds.")
        .defineInRange("tool.cooldown_ms", 50, 0, 1000)

    // [interaction]
    val respectClaimProtection: ModConfigSpec.ConfigValue<Boolean> =
        builder.comment("Always go through the standard interaction event chain so claim mods can veto.")
            .define("interaction.respect_claim_protection", true)

    val SPEC: ModConfigSpec = builder.build()
}