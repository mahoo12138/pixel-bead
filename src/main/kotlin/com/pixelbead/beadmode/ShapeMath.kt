package com.pixelbead.beadmode

/**
 * Pure 2D grid geometry for the bead tools. No Minecraft types on purpose: the tool logic
 * runs on `(u, v)` coordinates of the plane the player is facing, which keeps this file
 * unit-testable on a plain JVM (see ShapeMathTest) and keeps each tool handler tiny.
 *
 * A plane is picked by the face normal: X -> (y, z), Y -> (x, z), Z -> (x, y). The caller
 * converts back to world positions, so the same math works on walls, floors and ceilings.
 */
object ShapeMath {
    data class Cell(val u: Int, val v: Int)

    /** Bresenham line from [from] to [to]; both ends included, no duplicates. */
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

    /** Inclusive rectangle spanned by two opposite corners, row-major from the low corner. */
    fun rect(a: Cell, b: Cell): List<Cell> {
        val u0 = minOf(a.u, b.u)
        val u1 = maxOf(a.u, b.u)
        val v0 = minOf(a.v, b.v)
        val v1 = maxOf(a.v, b.v)
        val out = ArrayList<Cell>((u1 - u0 + 1) * (v1 - v0 + 1))
        for (v in v0..v1) {
            for (u in u0..u1) out += Cell(u, v)
        }
        return out
    }

    /** Square of [radius] around [center] in the same plane; `radius = 1` is a 3x3. */
    fun square(center: Cell, radius: Int): List<Cell> {
        val r = radius.coerceAtLeast(0)
        if (r == 0) return listOf(center)
        val out = ArrayList<Cell>((2 * r + 1) * (2 * r + 1))
        for (v in center.v - r..center.v + r) {
            for (u in center.u - r..center.u + r) out += Cell(u, v)
        }
        return out
    }
}
