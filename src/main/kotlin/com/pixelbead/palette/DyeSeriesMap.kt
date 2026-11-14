package com.pixelbead.palette

import kotlinx.serialization.json.Json
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item

/**
 * Data-driven vanilla-dye -> MARD-series map (blue dye excluded).
 * Default comes from the bundled datapack JSON; server/client reload listeners
 * (DyeSeriesMapReload) override it from datapacks so admins can adjust it.
 */
object DyeSeriesMap {
    private val json = Json { ignoreUnknownKeys = true }

    /** Immutable bundled default, used when no datapack override is present. */
    val DEFAULT_MAP: Map<String, String> = loadDefault()

    @Volatile
    private var map: Map<String, String> = DEFAULT_MAP

    fun defaultMap(): Map<String, String> = DEFAULT_MAP

    private fun loadDefault(): Map<String, String> {
        val stream = DyeSeriesMap::class.java
            .getResourceAsStream("/data/pixelbead/pixelbead/dye_series_map.json") ?: return emptyMap()
        return stream.bufferedReader(Charsets.UTF_8).use {
            json.decodeFromString<Map<String, String>>(it.readText())
        }
    }

    fun update(newMap: Map<String, String>) {
        map = newMap
    }

    fun asMap(): Map<String, String> = map

    fun seriesFor(dye: Item): String? = map[BuiltInRegistries.ITEM.getKey(dye).toString()]

    fun seriesFor(dyeId: String): String? = map[dyeId]

    /** Reverse lookup: series -> vanilla dye registry id (for UI hints). */
    fun dyeIdFor(series: String): String? =
        map.entries.firstOrNull { it.value.equals(series, ignoreCase = true) }?.key
}