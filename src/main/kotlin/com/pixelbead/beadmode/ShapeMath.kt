package com.pixelbead.beadmode

/**
 * Pure grid geometry for the bead tools. No Minecraft types on purpose: shapes are built from
 * plain integer coordinates, which keeps this file unit-testable on a plain JVM (ShapeMathTest)
 * and keeps the tool handlers tiny.
 *
 * Two flavours are used:
 *  - [line] works on the 2D plane of a block face and drives the drag-stroke interpolation;
 *  - [line3] / [plane3] work in 3D and drive the fast mode, where the player marks two beads and
 *    the shape between them is filled or erased (diagonals included).
 */
object ShapeMath {
    data class Cell(val u: Int, val v: Int)

    data class Cell3(val x: Int, val y: Int, val z: Int)

    /** Bresenham line from [from] to [to] on a 2D plane; both ends included. */
    fun line(from: Cell, to: Cell): List<Cell> {
        var x = from.u
        var y = from.v
        val dx = kotlin.math.abs(to.u - x)
        val dy = -kotlin.math.abs(to.v - y)
        val sx = if (x < to.u) 1 else -1
        val sy = if (y < to.v) 1 else -1
        var err = dx + dy
        val out = ArrayList<Cell>(maxOf(dx, -dy) + 1)
        while (true) {
            out += Cell(x, y)
            if (x == to.u && y == to.v) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x += sx
            }
            if (e2 <= dx) {
                err += dx
                y += sy
            }
        }
        return out
    }

    /**
     * 3D line between two cells (diagonals included), interpolated along the longest axis.
     * Always returns `max(|dx|, |dy|, |dz|) + 1` cells, each one step away from the previous.
     */
    fun line3(from: Cell3, to: Cell3): List<Cell3> {
        val steps = maxOf(
            kotlin.math.abs(to.x - from.x),
            kotlin.math.abs(to.y - from.y),
            kotlin.math.abs(to.z - from.z),
        )
        if (steps == 0) return listOf(from)
        val out = ArrayList<Cell3>(steps + 1)
        for (i in 0..steps) {
            out += Cell3(
                from.x + Math.round((to.x - from.x).toDouble() * i / steps).toInt(),
                from.y + Math.round((to.y - from.y).toDouble() * i / steps).toInt(),
                from.z + Math.round((to.z - from.z).toDouble() * i / steps).toInt(),
            )
        }
        return out
    }

    /**
     * Axis-aligned rectangle spanned by two corners: the points must differ in at most two axes.
     * Returns null when they differ in all three (that is a 3D diagonal, not a plane).
     * Degenerate cases collapse naturally: one differing axis yields a line, zero a single cell.
     */
    fun plane3(a: Cell3, b: Cell3): List<Cell3>? {
        val varying = (if (a.x != b.x) 1 else 0) + (if (a.y != b.y) 1 else 0) + (if (a.z != b.z) 1 else 0)
        if (varying > 2) return null
        val x0 = minOf(a.x, b.x)
        val x1 = maxOf(a.x, b.x)
        val y0 = minOf(a.y, b.y)
        val y1 = maxOf(a.y, b.y)
        val z0 = minOf(a.z, b.z)
        val z1 = maxOf(a.z, b.z)
        val out = ArrayList<Cell3>((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1))
        for (z in z0..z1) {
            for (y in y0..y1) {
                for (x in x0..x1) out += Cell3(x, y, z)
            }
        }
        return out
    }
}
