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

    // [interaction]
    val respectClaimProtection: ModConfigSpec.ConfigValue<Boolean> =
        builder.comment("Always go through the standard interaction event chain so claim mods can veto.")
            .define("interaction.respect_claim_protection", true)

    val SPEC: ModConfigSpec = builder.build()
}