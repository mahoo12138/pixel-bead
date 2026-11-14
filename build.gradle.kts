plugins {
    id("net.neoforged.moddev") version "2.0.148"
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}

group = property("mod_group") as String
version = property("mod_version") as String

repositories {
    mavenCentral()
    // Kotlin for Forge (KFF) — provides the Kotlin language loader + bundled stdlib/coroutines/serialization.
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

neoForge {
    version = property("neo_version") as String

    runs {
        // KFF 6.5.0 still declares the deprecated `logoFile` metadata key. NeoForge 26.3 turns
        // that into a dev-only "Warning while loading mods" screen (LogoFileWarningsHandler);
        // production builds skip the warning automatically. Suppress only this specific dev
        // warning, never real loading issues, for both dev run types.
        create("client") {
            client()
            systemProperty("neoforge.warnings.logofile.hide", "true")
        }
        create("server") {
            server()
            systemProperty("neoforge.warnings.logofile.hide", "true")
        }
    }

    mods {
        create("pixelbead") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kff_version")}")
    testImplementation(kotlin("test"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

// ---------------------------------------------------------------------------
// Build-time color resource generation.
// Single source of truth: color-data/mard-291.csv (MIT, MakeBead).
// Emits canonical palette JSON + 291 blockstates / item models / loot tables / lang.
// Hard-fails if the data does not match the MARD 291 specification.
// ---------------------------------------------------------------------------
val generatedResourcesDir = layout.buildDirectory.dir("generated/pixelbeadResources")
val colorCsv = layout.projectDirectory.file("color-data/mard-291.csv")

val expectedSeriesCounts: Map<String, Int> = linkedMapOf(
    "A" to 26, "B" to 32, "C" to 29, "D" to 26, "E" to 24, "F" to 25,
    "G" to 21, "H" to 23, "M" to 15, "P" to 23, "Q" to 5, "R" to 28,
    "T" to 1, "Y" to 5, "ZG" to 8,
)

/**
 * Gradle 9 configuration-cache-safe task action. Keeping the whole generator in a
 * standalone Action (holding only File/Map values) avoids capturing the build script
 * object in doLast, which otherwise makes the configuration cache entry unserializable.
 */
class BeadResourceGenerator(
    private val csvFile: File,
    private val outDir: File,
    private val colorDataDir: File,
    private val expected: Map<String, Int>,
) : org.gradle.api.Action<org.gradle.api.Task> {

    private data class GenColor(
        val id: String,
        val code: String,
        val series: String,
        val hex: String,
        val r: Int,
        val g: Int,
        val b: Int,
        val name: String,
        val discontinued: Boolean,
    )

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun write(rel: String, text: String) {
        val f = File(outDir, rel)
        f.parentFile.mkdirs()
        f.writeText(text)
    }

    private fun langJson(map: Map<String, String>): String = buildString {
        append("{\n")
        val entries = map.entries.toList()
        entries.forEachIndexed { i, (k, v) ->
            append("  \"${jsonEscape(k)}\": \"${jsonEscape(v)}\"")
            append(if (i < entries.size - 1) ",\n" else "\n")
        }
        append("}\n")
    }

    override fun execute(task: org.gradle.api.Task) {
        val lines = csvFile.readLines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "mard-291.csv is empty" }
        val header = lines.first()
        require(header == "code,name,hex,r,g,b,discontinued") { "Unexpected CSV header: $header" }

        val colors = ArrayList<GenColor>()
        val seenIds = HashSet<String>()
        val seriesCounts = LinkedHashMap<String, Int>()
        for (line in lines.drop(1)) {
            val parts = line.split(",")
            require(parts.size == 7) { "Malformed CSV row (expected 7 fields): $line" }
            val code = parts[0]
            val name = parts[1]
            val hex = parts[2]
            val rs = parts[3]
            val gs = parts[4]
            val bs = parts[5]
            val dis = parts[6]
            val id = code.lowercase()
            val series = code.takeWhile { it.isLetter() }
            require(series.isNotEmpty()) { "Cannot derive series from code: $code" }
            require(seenIds.add(id)) { "Duplicate color id: $id" }
            val r = rs.trim().toInt()
            val g = gs.trim().toInt()
            val b = bs.trim().toInt()
            val hexNorm = hex.trim().uppercase()
            require(hexNorm.matches(Regex("#[0-9A-F]{6}"))) { "Bad hex for $code: $hex" }
            val fromHex = (hexNorm.substring(1, 3).toInt(16) to hexNorm.substring(3, 5).toInt(16)) to hexNorm.substring(5, 7).toInt(16)
            require(fromHex.first.first == r && fromHex.first.second == g && fromHex.second == b) {
                "hex/rgb mismatch for $code: $hex vs $r,$g,$b"
            }
            seriesCounts[series] = (seriesCounts[series] ?: 0) + 1
            colors.add(GenColor(id, code, series, hexNorm, r, g, b, name, dis.trim().toBoolean()))
        }

        // Hard validation against the MARD 291 specification.
        require(colors.size == 291) { "Expected 291 colors, got ${colors.size}" }
        require(seriesCounts.keys == expected.keys) {
            "Series set mismatch: got ${seriesCounts.keys}, expected ${expected.keys}"
        }
        for ((s, n) in expected) {
            require(seriesCounts[s] == n) { "Series $s count mismatch: got ${seriesCounts[s]}, expected $n" }
        }

        // 1) Canonical palette JSON (read at runtime by com.pixelbead.palette.Palette).
        val paletteJson = buildString {
            append("[\n")
            colors.forEachIndexed { i, c ->
                append("  {\"id\":\"${c.id}\",\"code\":\"${jsonEscape(c.code)}\",\"series\":\"${c.series}\",")
                append("\"hex\":\"${c.hex}\",\"rgb\":[${c.r},${c.g},${c.b}],")
                append("\"name\":\"${jsonEscape(c.name)}\",\"discontinued\":${c.discontinued}}")
                append(if (i < colors.size - 1) ",\n" else "\n")
            }
            append("]\n")
        }
        write("assets/pixelbead/palette/mard291.json", paletteJson)

        // Static UI language strings (extended in later phases).
        val uiEn = linkedMapOf(
            "itemGroup.pixelbead" to "Pixel Bead",
            "block.pixelbead.dye_workbench" to "Bead Dye Workbench",
            "container.pixelbead.dye_workbench" to "Bead Dye Workbench",
            "key.pixelbead.toggle_mode" to "Toggle Bead Mode",
            "key.pixelbead.open_picker" to "Open Bead Color Picker",
            "gui.pixelbead.picker" to "Bead Colors",
            "gui.pixelbead.search" to "Search code...",
            "gui.pixelbead.options" to "Pixel Bead Settings",
            "gui.pixelbead.options.open" to "Settings",
            "gui.pixelbead.options.hud" to "Bead HUD",
            "gui.pixelbead.options.hud_position" to "HUD position",
            "gui.pixelbead.options.hud_enabled" to "Show HUD",
            "gui.pixelbead.options.hud_swatch" to "Swatch size",
            "gui.pixelbead.size.small" to "Small",
            "gui.pixelbead.size.medium" to "Medium",
            "gui.pixelbead.size.large" to "Large",
            "gui.pixelbead.hud.bottom" to "Bottom",
            "gui.pixelbead.hud.left" to "Left",
            "gui.pixelbead.hud.right" to "Right",
            "gui.pixelbead.workbench.no_dye" to "Insert a dye to choose a series",
            "gui.pixelbead.workbench.series" to "Series: %s",
            "message.pixelbead.insufficient" to "Not enough beads in inventory",
            "message.pixelbead.denied" to "Bead mode is unavailable here",
            "message.pixelbead.no_color" to "No bead color selected",
            "message.pixelbead.blocked" to "Cannot place a bead there",
        )
        val uiZh = linkedMapOf(
            "itemGroup.pixelbead" to "拼豆",
            "block.pixelbead.dye_workbench" to "拼豆染色工作台",
            "container.pixelbead.dye_workbench" to "拼豆染色工作台",
            "key.pixelbead.toggle_mode" to "切换拼豆模式",
            "key.pixelbead.open_picker" to "打开拼豆颜色选择器",
            "gui.pixelbead.picker" to "拼豆颜色",
            "gui.pixelbead.search" to "搜索色号...",
            "gui.pixelbead.options" to "拼豆设置",
            "gui.pixelbead.options.open" to "设置",
            "gui.pixelbead.options.hud" to "拼豆 HUD",
            "gui.pixelbead.options.hud_position" to "HUD 位置",
            "gui.pixelbead.options.hud_enabled" to "显示 HUD",
            "gui.pixelbead.options.hud_swatch" to "色块大小",
            "gui.pixelbead.size.small" to "小",
            "gui.pixelbead.size.medium" to "中",
            "gui.pixelbead.size.large" to "大",
            "gui.pixelbead.hud.bottom" to "下方",
            "gui.pixelbead.hud.left" to "左侧",
            "gui.pixelbead.hud.right" to "右侧",
            "gui.pixelbead.workbench.no_dye" to "放入染料以选择色系",
            "gui.pixelbead.workbench.series" to "色系：%s",
            "message.pixelbead.insufficient" to "背包中拼豆不足",
            "message.pixelbead.denied" to "当前无法使用拼豆模式",
            "message.pixelbead.no_color" to "未选择拼豆颜色",
            "message.pixelbead.blocked" to "该位置无法放置拼豆",
        )

        val en = LinkedHashMap<String, String>()
        val zh = LinkedHashMap<String, String>()
        for (c in colors) {
            val key = "block.pixelbead.bead_${c.id}"
            en[key] = "Bead ${c.code}"
            zh[key] = "拼豆 ${c.code}"

            // 2) Blockstate -> shared solid-color cube model.
            write(
                "assets/pixelbead/blockstates/bead_${c.id}.json",
                "{\n  \"variants\": {\n    \"\": { \"model\": \"pixelbead:block/bead\" }\n  }\n}\n",
            )

            // 3) Item model definition referencing the shared block model + custom tint source.
            //    Verified against vanilla 26.3 assets (e.g. items/acacia_leaves.json):
            //    tints entries are dispatched by the "type" field.
            write(
                "assets/pixelbead/items/bead_${c.id}.json",
                "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"pixelbead:block/bead\",\n    \"tints\": [ { \"type\": \"pixelbead:bead_color\" } ]\n  }\n}\n",
            )

            // 4) Loot table: drop self, survives explosion.
            write(
                "data/pixelbead/loot_table/blocks/bead_${c.id}.json",
                "{\n  \"type\": \"minecraft:block\",\n  \"pools\": [\n    {\n      \"rolls\": 1,\n      \"entries\": [ { \"type\": \"minecraft:item\", \"name\": \"pixelbead:bead_${c.id}\" } ],\n      \"condition\": { \"type\": \"minecraft:survives_explosion\" }\n    }\n  ]\n}\n",
            )
        }
        en.putAll(uiEn)
        zh.putAll(uiZh)

        write("assets/pixelbead/lang/en_us.json", langJson(en))
        write("assets/pixelbead/lang/zh_cn.json", langJson(zh))

        // Ship attribution inside the jar.
        for (n in listOf("mard-291.csv", "LICENSE", "NOTICE.md")) {
            val src = File(colorDataDir, n)
            if (src.exists()) write("assets/pixelbead/color-data/$n", src.readText())
        }

        task.logger.lifecycle("generateBeadResources: ${colors.size} colors, ${seriesCounts.size} series -> $outDir")
    }
}

val generateBeadResources = tasks.register("generateBeadResources") {
    description = "Generate palette JSON and per-color block/item/loot/lang resources from mard-291.csv"
    group = "pixelbead"
    inputs.file(colorCsv)
    inputs.dir(layout.projectDirectory.dir("color-data"))
    // Generator logic itself lives in this script; changes must invalidate the output.
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
    outputs.dir(generatedResourcesDir)

    doLast(
        BeadResourceGenerator(
            colorCsv.asFile,
            generatedResourcesDir.get().asFile,
            layout.projectDirectory.dir("color-data").asFile,
            expectedSeriesCounts.toMap(),
        )
    )
}

sourceSets {
    main {
        resources.srcDir(generatedResourcesDir)
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateBeadResources)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}