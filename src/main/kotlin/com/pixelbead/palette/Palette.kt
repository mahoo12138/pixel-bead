package com.pixelbead.palette

import kotlinx.serialization.json.Json

/**
 * Single source of truth for the 291 MARD colors, loaded from the build-time generated
 * classpath resource. Lazy + synchronized so it is safe to touch from any registry object;
 * the mod entrypoint forces it early to fail fast at startup.
 */
object Palette {
    /** Canonical MARD series order (standard A–M first, then extensions). */
    val seriesOrder: List<String> = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "M", "P", "Q", "R", "T", "Y", "ZG",
    )

    private val expectedSeriesCounts = mapOf(
        "A" to 26, "B" to 32, "C" to 29, "D" to 26, "E" to 24, "F" to 25,
        "G" to 21, "H" to 23, "M" to 15, "P" to 23, "Q" to 5, "R" to 28,
        "T" to 1, "Y" to 5, "ZG" to 8,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private class Data(
        val all: List<BeadColor>,
        val ordered: List<BeadColor>,
        val idMap: Map<String, BeadColor>,
        val codeMap: Map<String, BeadColor>,
        val seriesMap: Map<String, List<BeadColor>>,
    )

    private val data: Data by lazy { readAndValidate() }

    val all: List<BeadColor> get() = data.all

    /** Sorted by series order, then numeric code (A2 before A10), for creative tab / picker. */
    val ordered: List<BeadColor> get() = data.ordered

    fun byId(id: String): BeadColor? = data.idMap[id.lowercase()]

    fun byCode(code: String): BeadColor? = data.codeMap[code.uppercase()]

    fun series(series: String): List<BeadColor> = data.seriesMap[series.uppercase()] ?: emptyList()

    fun argb(id: String): Int = byId(id)?.argb ?: DEFAULT_ARGB

    /** Force load + validation (called from the mod entrypoint). */
    fun load() {
        data
    }

    private const val DEFAULT_ARGB = -0x1 // 0xFFFFFFFF opaque white

    private fun readAndValidate(): Data {
        val stream = Palette::class.java.getResourceAsStream("/assets/pixelbead/palette/mard291.json")
            ?: error("Pixel Bead: missing palette resource /assets/pixelbead/palette/mard291.json")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val list = json.decodeFromString<List<BeadColor>>(text)

        check(list.size == 291) { "Pixel Bead: expected 291 colors, got ${list.size}" }
        val counts = list.groupingBy { it.series }.eachCount()
        check(counts.keys == expectedSeriesCounts.keys) {
            "Pixel Bead: series set mismatch ${counts.keys} vs ${expectedSeriesCounts.keys}"
        }
        for ((s, n) in expectedSeriesCounts) {
            check(counts[s] == n) { "Pixel Bead: series $s count ${counts[s]} != $n" }
        }
        check(list.distinctBy { it.id }.size == list.size) { "Pixel Bead: duplicate color ids" }

        val ordered = list.sortedWith(
            compareBy(
                { seriesOrder.indexOf(it.series) },
                { it.code.dropWhile { c -> c.isLetter() }.toIntOrNull() ?: 0 },
                { it.code },
            ),
        )
        return Data(
            all = list,
            ordered = ordered,
            idMap = list.associateBy { it.id },
            codeMap = list.associateBy { it.code.uppercase() },
            seriesMap = list.groupBy { it.series },
        )
    }
}