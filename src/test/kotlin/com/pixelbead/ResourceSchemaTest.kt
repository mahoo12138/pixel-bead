package com.pixelbead

import com.pixelbead.palette.Palette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks in the 26.3 datapack / asset schemas that were verified against vanilla data
 * (recipe ingredients, LootPool condition, item-model tint dispatch). These files are
 * generated/shipped as plain JSON, so a compile cannot catch a schema regression.
 */
class ResourceSchemaTest {

    private fun resource(path: String): JsonObject {
        val text = ResourceSchemaTest::class.java.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Missing classpath resource $path")
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(vararg path: String): String? {
        var current: JsonElement = this
        for (key in path) current = (current as? JsonObject)?.get(key) ?: return null
        return (current as? JsonPrimitive)?.contentOrNull
    }

    private fun beadIds(): List<String> = Palette.all.map { "bead_${it.id}" }

    @Test
    fun recipeIngredientUsesTagStringForm() {
        val recipe = resource("/data/pixelbead/recipe/dye_workbench.json")
        assertEquals("minecraft:crafting_shaped", recipe.string("type"))
        // 26.3 Ingredient.CODEC expects a plain string ("#tag" or "item"), not {"tag": ...}.
        val ingredient = recipe["key"]!!.jsonObject["P"]
        assertTrue(ingredient is JsonPrimitive, "ingredient must be a string, got $ingredient")
        assertEquals("#minecraft:planks", ingredient.content)
        assertEquals("pixelbead:dye_workbench", recipe.string("result", "id"))
    }

    @Test
    fun everyLootTableUsesSingularConditionField() {
        for (id in listOf("dye_workbench") + beadIds()) {
            val loot = resource("/data/pixelbead/loot_table/blocks/$id.json")
            assertEquals("minecraft:block", loot.string("type"), "loot table $id")
            val pool = loot["pools"]!!.jsonArray.single().jsonObject
            assertNull(pool["conditions"], "26.3 LootPool.CODEC has no 'conditions' array: $id")
            assertEquals("minecraft:survives_explosion", pool.string("condition", "type"), "loot table $id")
            assertEquals(
                "pixelbead:$id",
                pool["entries"]!!.jsonArray.single().jsonObject.string("name"),
                "loot table $id drops itself",
            )
        }
    }

    @Test
    fun everyItemModelDispatchesTheRegisteredTintSource() {
        for (id in beadIds()) {
            val model = resource("/assets/pixelbead/items/$id.json")["model"]!!.jsonObject
            assertEquals("minecraft:model", model.string("type"), "item model $id")
            assertEquals("pixelbead:block/bead", model.string("model"), "item model $id")
            val tint = model["tints"]!!.jsonArray.single().jsonObject
            assertEquals("pixelbead:bead_color", tint.string("type"), "item model $id")
        }
    }

    @Test
    fun everyBlockstatePointsAtSharedCubeModel() {
        for (id in beadIds()) {
            val state = resource("/assets/pixelbead/blockstates/$id.json")
            assertEquals(
                "pixelbead:block/bead",
                state.string("variants", "", "model"),
                "blockstate $id",
            )
        }
    }

    @Test
    fun dyeSeriesMapCoversExactlyTheMappedSeries() {
        val map = resource("/data/pixelbead/pixelbead/dye_series_map.json")
        assertEquals(15, map.size)
        for ((dye, series) in map) {
            assertTrue(dye.startsWith("minecraft:") && dye.endsWith("_dye"), "unexpected dye key $dye")
            val value = (series as? JsonPrimitive)?.contentOrNull
            assertTrue(value != null && value in Palette.seriesOrder, "unexpected series $value for $dye")
        }
        // Blue has no MARD series in the vendor data; it must stay unmapped.
        assertFalse(map.containsKey("minecraft:blue_dye"))
    }

    @Test
    fun allUiAndMessageKeysAreTranslatedInBothLanguages() {
        // BeadPlacement reports every rejection reason via HudMessage, and the picker/HUD
        // use these gui keys; a missing entry would show the raw key instead of text.
        val keys = listOf(
            "message.pixelbead.welcome",
            "message.pixelbead.welcome.hint",
            "message.pixelbead.insufficient",
            "message.pixelbead.denied",
            "message.pixelbead.no_color",
            "message.pixelbead.blocked",
            "gui.pixelbead.options",
            "gui.pixelbead.options.open",
            "gui.pixelbead.options.hud",
            "gui.pixelbead.options.hud_position",
            "gui.pixelbead.options.hud_enabled",
            "gui.pixelbead.options.hud_swatch",
            "gui.pixelbead.size.small",
            "gui.pixelbead.size.medium",
            "gui.pixelbead.size.large",
            "gui.pixelbead.hud.bottom",
            "gui.pixelbead.hud.left",
            "gui.pixelbead.hud.right",
        )
        for (lang in listOf("en_us", "zh_cn")) {
            val map = resource("/assets/pixelbead/lang/$lang.json")
            for (key in keys) {
                val value = (map[key] as? JsonPrimitive)?.contentOrNull
                assertTrue(!value.isNullOrBlank(), "missing $key in $lang")
            }
        }
    }

    @Test
    fun defaultHotbarColorsExistInPalette() {
        // Mirrors BeadModeState.DEFAULT_HOTBAR (2 rows x 9). Kept as a literal on purpose:
        // BeadModeState's companion pulls in Minecraft network classes that a plain JVM
        // unit test must not load.
        val ids = listOf(
            "a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1", "m1",
            "p1", "q1", "r1", "t1", "y1", "zg1", "a2", "b2", "c2",
        )
        assertEquals(18, ids.size)
        assertEquals(18, ids.distinct().size)
        for (id in ids) assertNotNull(Palette.byId(id), "default hotbar color $id is missing from the palette")
    }
}
