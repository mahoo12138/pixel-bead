package com.pixelbead.palette

import com.pixelbead.PixelBead
import kotlinx.serialization.json.Json
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

/**
 * Loads the dye->series map from datapacks so admins can override the bundled default.
 * Registered on both server and client reload events; vanilla datapack sync propagates
 * server overrides to clients. Uses SimplePreparableReloadListener (the 26.3
 * SimpleJsonResourceReloadListener became codec-based with a different constructor).
 */
object DyeSeriesMapReload : SimplePreparableReloadListener<Map<String, String>>() {
    private val json = Json { ignoreUnknownKeys = true }
    // ResourceManager.getResource() resolves the identifier path verbatim as a file path,
    // so the .json extension is required (data/pixelbead/pixelbead/dye_series_map.json).
    private val fileId = Identifier.fromNamespaceAndPath(PixelBead.MODID, "pixelbead/dye_series_map.json")

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Map<String, String> {
        val resource = manager.getResource(fileId).orElse(null) ?: return DyeSeriesMap.defaultMap()
        val text = resource.openAsReader().use { it.readText() }
        return json.decodeFromString<Map<String, String>>(text)
    }

    override fun apply(map: Map<String, String>, manager: ResourceManager, profiler: ProfilerFiller) {
        DyeSeriesMap.update(map)
    }
}