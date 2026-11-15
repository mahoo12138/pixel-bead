package com.pixelbead

import com.pixelbead.beadmode.ShapeMath
import com.pixelbead.beadmode.ShapeMath.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Plain-JVM tests for the bead-tool geometry. ShapeMath deliberately has no Minecraft types,
 * so the two-click rectangle and the stroke interpolation can be verified without a game.
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
    fun lineAlongOneAxisHasNoDuplicates() {
        assertEquals(
            listOf(Cell(3, 7), Cell(3, 8), Cell(3, 9)),
            ShapeMath.line(Cell(3, 7), Cell(3, 9)),
        )
        assertEquals(listOf(Cell(5, 1)), ShapeMath.line(Cell(5, 1), Cell(5, 1)))
    }

    @Test
    fun rectIsInclusiveAndRowMajor() {
        val cells = ShapeMath.rect(Cell(2, 5), Cell(0, 3))
        assertEquals(9, cells.size, "3x3 rectangle")
        // Corners are normalised: row-major from the low corner.
        assertEquals(Cell(0, 3), cells.first())
        assertEquals(Cell(2, 5), cells.last())
        assertEquals(listOf(Cell(0, 3), Cell(1, 3), Cell(2, 3)), cells.take(3))
    }

    @Test
    fun singleCellRectAndLineAgree() {
        assertEquals(listOf(Cell(4, 4)), ShapeMath.rect(Cell(4, 4), Cell(4, 4)))
        assertEquals(listOf(Cell(4, 4)), ShapeMath.line(Cell(4, 4), Cell(4, 4)))
    }

    @Test
    fun squareGrowsWithRadiusAndKeepsTheCentre() {
        val r1 = ShapeMath.square(Cell(10, 10), 1)
        assertEquals(9, r1.size, "radius 1 = 3x3")
        assertTrue(r1.contains(Cell(10, 10)))
        assertTrue(r1.contains(Cell(9, 9)) && r1.contains(Cell(11, 11)))
        assertEquals(1, ShapeMath.square(Cell(0, 0), 0).size, "radius 0 = single cell")
        assertEquals(49, ShapeMath.square(Cell(0, 0), 3).size, "radius 3 = 7x7")
    }
}
