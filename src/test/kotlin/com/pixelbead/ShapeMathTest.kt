package com.pixelbead

import com.pixelbead.beadmode.ShapeMath
import com.pixelbead.beadmode.ShapeMath.Cell
import com.pixelbead.beadmode.ShapeMath.Cell3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Plain-JVM tests for the bead-tool geometry. ShapeMath deliberately has no Minecraft types, so
 * the two-point fast-mode shapes can be verified without a game.
 */
class ShapeMathTest {
    @Test
    fun lineIncludesBothEndsAndIsContinuous() {
        val cells = ShapeMath.line(Cell(0, 0), Cell(4, 2))
        assertEquals(Cell(0, 0), cells.first())
        assertEquals(Cell(4, 2), cells.last())
        assertEquals(5, cells.size, "a 4x2 line covers 5 cells")
        for (i in 1 until cells.size) {
            val dx = kotlin.math.abs(cells[i].u - cells[i - 1].u)
            val dy = kotlin.math.abs(cells[i].v - cells[i - 1].v)
            assertTrue(dx <= 1 && dy <= 1 && dx + dy > 0, "gap between ${cells[i - 1]} and ${cells[i]}")
        }
    }

    @Test
    fun line3HitsBothEndsAndStepsOneBlockAtATime() {
        val cells = ShapeMath.line3(Cell3(0, 0, 0), Cell3(4, 2, 0))
        assertEquals(Cell3(0, 0, 0), cells.first())
        assertEquals(Cell3(4, 2, 0), cells.last())
        assertEquals(5, cells.size, "longest axis is 4, so 5 cells")
        for (i in 1 until cells.size) {
            val d = cells[i]
            val p = cells[i - 1]
            val step = maxOf(
                kotlin.math.abs(d.x - p.x),
                kotlin.math.abs(d.y - p.y),
                kotlin.math.abs(d.z - p.z),
            )
            assertEquals(1, step, "diagonal steps must stay connected: $p -> $d")
        }
    }

    @Test
    fun line3HandlesAFullThreeAxisDiagonal() {
        val cells = ShapeMath.line3(Cell3(0, 0, 0), Cell3(3, 3, 3))
        assertEquals(4, cells.size)
        assertEquals(Cell3(3, 3, 3), cells.last())
        assertEquals(listOf(Cell3(0, 0, 0), Cell3(1, 1, 1), Cell3(2, 2, 2), Cell3(3, 3, 3)), cells)
    }

    @Test
    fun plane3SpansTheRectangleBetweenTwoCorners() {
        val cells = ShapeMath.plane3(Cell3(2, 5, 7), Cell3(0, 3, 7))!!
        assertEquals(9, cells.size, "3x3 rectangle")
        assertTrue(cells.contains(Cell3(0, 3, 7)) && cells.contains(Cell3(2, 5, 7)))
        assertTrue(cells.all { it.z == 7 }, "every cell stays on the same plane")
    }

    @Test
    fun plane3DegeneratesToLineOrPoint() {
        assertEquals(3, ShapeMath.plane3(Cell3(0, 0, 0), Cell3(2, 0, 0))?.size, "one axis -> line")
        assertEquals(listOf(Cell3(4, 4, 4)), ShapeMath.plane3(Cell3(4, 4, 4), Cell3(4, 4, 4)))
    }

    @Test
    fun plane3RefusesAThreeAxisDiagonal() {
        assertNull(ShapeMath.plane3(Cell3(0, 0, 0), Cell3(1, 2, 3)), "no single plane contains both")
    }
}
