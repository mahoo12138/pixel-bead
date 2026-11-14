package com.pixelbead

import com.pixelbead.palette.Palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaletteTest {
    @Test
    fun loadsAll291Colors() {
        Palette.load()
        assertEquals(291, Palette.all.size)
    }

    @Test
    fun hasAll15SeriesWithExactCounts() {
        val expected = mapOf(
            "A" to 26, "B" to 32, "C" to 29, "D" to 26, "E" to 24, "F" to 25,
            "G" to 21, "H" to 23, "M" to 15, "P" to 23, "Q" to 5, "R" to 28,
            "T" to 1, "Y" to 5, "ZG" to 8,
        )
        assertEquals(expected.keys, Palette.all.map { it.series }.toSet())
        for ((s, n) in expected) assertEquals(n, Palette.series(s).size, "series $s")
    }

    @Test
    fun hexMatchesRgbAndArgb() {
        for (c in Palette.all) {
            val hex = c.hex.removePrefix("#")
            val r = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            assertEquals(listOf(r, g, b), c.rgb, "rgb for ${c.code}")
            val expectedArgb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            assertEquals(expectedArgb, c.argb, "argb for ${c.code}")
        }
    }

    @Test
    fun uniqueIdsAndCodes() {
        assertEquals(291, Palette.all.distinctBy { it.id }.size)
        assertEquals(291, Palette.all.distinctBy { it.code }.size)
    }

    @Test
    fun lookupIsCaseInsensitive() {
        assertEquals("A1", Palette.byCode("a1")?.code)
        assertEquals("ZG8", Palette.byCode("zg8")?.code)
        assertNotNull(Palette.byId("zg8"))
        assertEquals("ZG8", Palette.byId("ZG8")?.code)
    }

    @Test
    fun naturalOrderPutsA2BeforeA10() {
        val a = Palette.ordered.filter { it.series == "A" }.map { it.code }
        assertTrue(a.indexOf("A2") < a.indexOf("A10"), "A2 should sort before A10: $a")
        // Series order respected: all A before all B, ZG last.
        val seriesSeq = Palette.ordered.map { it.series }.distinct()
        assertEquals(Palette.seriesOrder, seriesSeq)
    }

    @Test
    fun zgIsTwoLetterSeries() {
        assertEquals(8, Palette.series("ZG").size)
        assertTrue(Palette.series("ZG").all { it.id.startsWith("zg") })
    }
}